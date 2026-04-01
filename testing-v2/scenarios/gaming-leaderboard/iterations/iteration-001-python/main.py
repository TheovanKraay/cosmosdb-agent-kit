"""
Gaming Leaderboard API — FastAPI + Azure Cosmos DB (NoSQL)
==========================================================
Implements the gaming-leaderboard API contract with Cosmos DB best practices:
- Multiple containers (players, scores, leaderboards)
- Synthetic partition key on leaderboards container
- Composite indexes for ORDER BY bestScore DESC, displayName ASC
- ETag-based optimistic concurrency for player stat updates
- Type discriminator and schema version on all documents
- Custom excluded indexing paths
"""

import os
import uuid
from datetime import datetime, timezone
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from azure.cosmos.aio import CosmosClient
from azure.cosmos import PartitionKey
from azure.cosmos.exceptions import CosmosHttpResponseError, CosmosResourceNotFoundError
from azure.core import MatchConditions


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
COSMOS_ENDPOINT = os.environ.get("COSMOS_ENDPOINT", "https://localhost:8081")
COSMOS_KEY = os.environ.get(
    "COSMOS_KEY",
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==",
)
DATABASE_NAME = "gaming-leaderboard-db"

# Container names
PLAYERS_CONTAINER = "players"
SCORES_CONTAINER = "scores"
LEADERBOARDS_CONTAINER = "leaderboards"

# Globals
cosmos_client: CosmosClient = None
database = None
players_container = None
scores_container = None
leaderboards_container = None


# ---------------------------------------------------------------------------
# Pydantic models
# ---------------------------------------------------------------------------
class CreatePlayerRequest(BaseModel):
    playerId: str
    displayName: str
    region: str


class UpdatePlayerRequest(BaseModel):
    displayName: Optional[str] = None
    region: Optional[str] = None


class SubmitScoreRequest(BaseModel):
    playerId: str
    score: int
    gameMode: Optional[str] = None


# ---------------------------------------------------------------------------
# Cosmos DB initialization
# ---------------------------------------------------------------------------
async def init_cosmos():
    """Create database and containers with best-practice configuration."""
    global cosmos_client, database
    global players_container, scores_container, leaderboards_container

    cosmos_client = CosmosClient(COSMOS_ENDPOINT, credential=COSMOS_KEY)
    database = await cosmos_client.create_database_if_not_exists(id=DATABASE_NAME)

    # Players container — partition key /playerId for efficient point reads
    players_container = await database.create_container_if_not_exists(
        id=PLAYERS_CONTAINER,
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy={
            "indexingMode": "consistent",
            "automatic": True,
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [
                {"path": "/_etag/?"},
                {"path": "/gameMode/?"},
            ],
            "compositeIndexes": [
                [
                    {"path": "/bestScore", "order": "descending"},
                    {"path": "/displayName", "order": "ascending"},
                ]
            ],
        },
        offer_throughput=400,
    )

    # Scores container — partition key /playerId for player score history
    scores_container = await database.create_container_if_not_exists(
        id=SCORES_CONTAINER,
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy={
            "indexingMode": "consistent",
            "automatic": True,
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [
                {"path": "/_etag/?"},
                {"path": "/gameMode/?"},
            ],
            "compositeIndexes": [
                [
                    {"path": "/timestamp", "order": "descending"},
                    {"path": "/score", "order": "descending"},
                ]
            ],
        },
        offer_throughput=400,
    )

    # Leaderboards container — synthetic partition key /leaderboardKey
    # for efficient top-N queries within a single partition
    leaderboards_container = await database.create_container_if_not_exists(
        id=LEADERBOARDS_CONTAINER,
        partition_key=PartitionKey(path="/leaderboardKey"),
        indexing_policy={
            "indexingMode": "consistent",
            "automatic": True,
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [
                {"path": "/_etag/?"},
                {"path": "/gameMode/?"},
            ],
            "compositeIndexes": [
                [
                    {"path": "/bestScore", "order": "descending"},
                    {"path": "/displayName", "order": "ascending"},
                ]
            ],
        },
        offer_throughput=400,
    )


async def close_cosmos():
    """Close the Cosmos DB client."""
    global cosmos_client
    if cosmos_client:
        await cosmos_client.close()


@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_cosmos()
    yield
    await close_cosmos()


app = FastAPI(title="Gaming Leaderboard API", lifespan=lifespan)


# ---------------------------------------------------------------------------
# Helper: build player response dict
# ---------------------------------------------------------------------------
def player_response(doc: dict) -> dict:
    return {
        "playerId": doc["playerId"],
        "displayName": doc["displayName"],
        "region": doc["region"],
        "totalGames": doc.get("totalGames", 0),
        "bestScore": doc.get("bestScore", 0),
        "averageScore": doc.get("averageScore", 0),
    }


# ---------------------------------------------------------------------------
# Helper: update leaderboard entries for a player
# ---------------------------------------------------------------------------
async def upsert_leaderboard_entries(player_doc: dict):
    """
    Upsert leaderboard entries for a player into global and regional
    leaderboards. Each entry is keyed by a synthetic partition key.
    """
    if player_doc.get("bestScore", 0) <= 0:
        return

    region = player_doc["region"]
    player_id = player_doc["playerId"]

    for scope, key in [("global", "global_all-time"), ("regional", f"{region}_all-time")]:
        entry = {
            "id": f"{scope}_{player_id}",
            "playerId": player_id,
            "displayName": player_doc["displayName"],
            "region": region,
            "bestScore": player_doc["bestScore"],
            "leaderboardKey": key,
            "type": "leaderboardEntry",
            "schemaVersion": 1,
        }
        await leaderboards_container.upsert_item(body=entry)


# ---------------------------------------------------------------------------
# Helper: remove leaderboard entries for a player
# ---------------------------------------------------------------------------
async def remove_leaderboard_entries(player_id: str, region: str):
    """Remove all leaderboard entries for a given player."""
    for scope, key in [("global", "global_all-time"), ("regional", f"{region}_all-time")]:
        doc_id = f"{scope}_{player_id}"
        try:
            await leaderboards_container.delete_item(item=doc_id, partition_key=key)
        except CosmosResourceNotFoundError:
            pass


# ---------------------------------------------------------------------------
# Health
# ---------------------------------------------------------------------------
@app.get("/health")
async def health():
    return {"status": "ok"}


# ---------------------------------------------------------------------------
# POST /api/players — Create player
# ---------------------------------------------------------------------------
@app.post("/api/players", status_code=201)
async def create_player(req: CreatePlayerRequest):
    if not req.playerId or not req.displayName or not req.region:
        raise HTTPException(status_code=400, detail="playerId, displayName and region are required")

    doc = {
        "id": req.playerId,
        "playerId": req.playerId,
        "displayName": req.displayName,
        "region": req.region,
        "totalGames": 0,
        "bestScore": 0,
        "averageScore": 0,
        "totalScore": 0,
        "type": "player",
        "schemaVersion": 1,
    }

    try:
        result = await players_container.create_item(body=doc)
    except CosmosHttpResponseError as e:
        if e.status_code == 409:
            raise HTTPException(status_code=409, detail="Player already exists")
        raise

    return JSONResponse(status_code=201, content=player_response(result))


# ---------------------------------------------------------------------------
# GET /api/players/{playerId}
# ---------------------------------------------------------------------------
@app.get("/api/players/{playerId}")
async def get_player(playerId: str):
    try:
        doc = await players_container.read_item(item=playerId, partition_key=playerId)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")
    return player_response(doc)


# ---------------------------------------------------------------------------
# PATCH /api/players/{playerId}
# ---------------------------------------------------------------------------
@app.patch("/api/players/{playerId}")
async def update_player(playerId: str, req: UpdatePlayerRequest):
    try:
        doc = await players_container.read_item(item=playerId, partition_key=playerId)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    old_region = doc["region"]
    changed = False

    if req.displayName is not None:
        doc["displayName"] = req.displayName
        changed = True
    if req.region is not None:
        doc["region"] = req.region
        changed = True

    if changed:
        result = await players_container.replace_item(item=doc["id"], body=doc)
    else:
        result = doc

    # Update leaderboard entries if player data changed
    if changed and result.get("bestScore", 0) > 0:
        # Remove old regional entry if region changed
        if req.region is not None and req.region != old_region:
            old_regional_id = f"regional_{playerId}"
            old_regional_key = f"{old_region}_all-time"
            try:
                await leaderboards_container.delete_item(
                    item=old_regional_id, partition_key=old_regional_key
                )
            except CosmosResourceNotFoundError:
                pass
        await upsert_leaderboard_entries(result)

    return player_response(result)


# ---------------------------------------------------------------------------
# DELETE /api/players/{playerId}
# ---------------------------------------------------------------------------
@app.delete("/api/players/{playerId}", status_code=204)
async def delete_player(playerId: str):
    try:
        doc = await players_container.read_item(item=playerId, partition_key=playerId)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    region = doc["region"]

    # Delete player document
    await players_container.delete_item(item=playerId, partition_key=playerId)

    # Delete all scores for this player
    query = "SELECT * FROM c WHERE c.playerId = @pid"
    params = [{"name": "@pid", "value": playerId}]
    async for score_doc in scores_container.query_items(
        query=query, parameters=params, partition_key=playerId
    ):
        await scores_container.delete_item(item=score_doc["id"], partition_key=playerId)

    # Remove from leaderboards
    await remove_leaderboard_entries(playerId, region)

    return JSONResponse(status_code=204, content=None)


# ---------------------------------------------------------------------------
# POST /api/scores — Submit a score
# ---------------------------------------------------------------------------
@app.post("/api/scores", status_code=201)
async def submit_score(req: SubmitScoreRequest):
    if not req.playerId:
        raise HTTPException(status_code=400, detail="playerId is required")
    if req.score is None:
        raise HTTPException(status_code=400, detail="score is required")
    if req.score < 0:
        raise HTTPException(status_code=400, detail="score must be non-negative")

    # Verify player exists
    try:
        player_doc = await players_container.read_item(
            item=req.playerId, partition_key=req.playerId
        )
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Create score document
    score_id = str(uuid.uuid4())
    now = datetime.now(timezone.utc).isoformat()

    score_doc = {
        "id": score_id,
        "scoreId": score_id,
        "playerId": req.playerId,
        "score": req.score,
        "gameMode": req.gameMode,
        "timestamp": now,
        "type": "score",
        "schemaVersion": 1,
    }

    await scores_container.create_item(body=score_doc)

    # Update player stats with ETag-based optimistic concurrency (retry loop)
    max_retries = 20
    for attempt in range(max_retries):
        try:
            player_doc = await players_container.read_item(
                item=req.playerId, partition_key=req.playerId
            )
            etag = player_doc.get("_etag")

            total_games = player_doc.get("totalGames", 0) + 1
            total_score = player_doc.get("totalScore", 0) + req.score
            best_score = max(player_doc.get("bestScore", 0), req.score)
            average_score = total_score / total_games if total_games > 0 else 0

            player_doc["totalGames"] = total_games
            player_doc["totalScore"] = total_score
            player_doc["bestScore"] = best_score
            player_doc["averageScore"] = average_score

            await players_container.replace_item(
                item=player_doc["id"],
                body=player_doc,
                etag=etag,
                match_condition=MatchConditions.IfNotModified,
            )

            # Update leaderboard entries
            await upsert_leaderboard_entries(player_doc)
            break

        except CosmosHttpResponseError as e:
            if e.status_code == 412 and attempt < max_retries - 1:
                continue  # ETag conflict — retry
            raise

    return JSONResponse(
        status_code=201,
        content={
            "scoreId": score_id,
            "playerId": req.playerId,
            "score": req.score,
        },
    )


# ---------------------------------------------------------------------------
# GET /api/players/{playerId}/scores — Score history
# ---------------------------------------------------------------------------
@app.get("/api/players/{playerId}/scores")
async def get_player_scores(playerId: str, limit: int = Query(default=10, ge=1, le=100)):
    # Verify player exists
    try:
        await players_container.read_item(item=playerId, partition_key=playerId)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    query = (
        "SELECT * FROM c WHERE c.playerId = @pid "
        "ORDER BY c.timestamp DESC"
    )
    params = [{"name": "@pid", "value": playerId}]

    results = []
    async for item in scores_container.query_items(
        query=query, parameters=params, partition_key=playerId, max_item_count=limit
    ):
        results.append({
            "scoreId": item["scoreId"],
            "playerId": item["playerId"],
            "score": item["score"],
            "gameMode": item.get("gameMode"),
            "timestamp": item["timestamp"],
        })
        if len(results) >= limit:
            break

    return results


# ---------------------------------------------------------------------------
# GET /api/leaderboards/global — Global leaderboard
# ---------------------------------------------------------------------------
@app.get("/api/leaderboards/global")
async def global_leaderboard(top: int = Query(default=100, ge=0, le=100)):
    if top == 0:
        return []

    query = (
        "SELECT * FROM c "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )

    entries = []
    async for item in leaderboards_container.query_items(
        query=query, partition_key="global_all-time"
    ):
        entries.append(item)
        if len(entries) >= top:
            break

    result = []
    for i, entry in enumerate(entries):
        result.append({
            "rank": i + 1,
            "playerId": entry["playerId"],
            "displayName": entry["displayName"],
            "score": entry["bestScore"],
        })

    return result


# ---------------------------------------------------------------------------
# GET /api/leaderboards/regional/{region}
# ---------------------------------------------------------------------------
@app.get("/api/leaderboards/regional/{region}")
async def regional_leaderboard(region: str, top: int = Query(default=100, ge=0, le=100)):
    if top == 0:
        return []

    leaderboard_key = f"{region}_all-time"

    query = (
        "SELECT * FROM c "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )

    entries = []
    async for item in leaderboards_container.query_items(
        query=query, partition_key=leaderboard_key
    ):
        entries.append(item)
        if len(entries) >= top:
            break

    result = []
    for i, entry in enumerate(entries):
        result.append({
            "rank": i + 1,
            "playerId": entry["playerId"],
            "displayName": entry["displayName"],
            "score": entry["bestScore"],
        })

    return result


# ---------------------------------------------------------------------------
# GET /api/players/{playerId}/rank — Player rank + neighbors
# ---------------------------------------------------------------------------
@app.get("/api/players/{playerId}/rank")
async def player_rank(playerId: str):
    # Verify player exists and has scores
    try:
        player_doc = await players_container.read_item(
            item=playerId, partition_key=playerId
        )
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    if player_doc.get("bestScore", 0) == 0 and player_doc.get("totalGames", 0) == 0:
        raise HTTPException(status_code=404, detail="Player has no scores")

    # Get the full global leaderboard to determine rank
    query = (
        "SELECT * FROM c "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )

    all_entries = []
    async for item in leaderboards_container.query_items(
        query=query, partition_key="global_all-time"
    ):
        all_entries.append(item)

    # Find the player's position
    player_index = -1
    for i, entry in enumerate(all_entries):
        if entry["playerId"] == playerId:
            player_index = i
            break

    if player_index == -1:
        raise HTTPException(status_code=404, detail="Player not found in leaderboard")

    player_rank_val = player_index + 1
    player_score = all_entries[player_index]["bestScore"]

    # Get neighbors (±10 positions)
    start = max(0, player_index - 10)
    end = min(len(all_entries), player_index + 11)

    neighbors = []
    for i in range(start, end):
        if i == player_index:
            continue
        neighbors.append({
            "rank": i + 1,
            "playerId": all_entries[i]["playerId"],
            "displayName": all_entries[i]["displayName"],
            "score": all_entries[i]["bestScore"],
        })

    return {
        "playerId": playerId,
        "rank": player_rank_val,
        "score": player_score,
        "neighbors": neighbors,
    }
