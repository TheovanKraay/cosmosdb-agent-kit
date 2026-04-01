"""
Gaming Leaderboard API — FastAPI + Azure Cosmos DB (async)

Cosmos DB best practices applied:
- Async SDK (azure.cosmos.aio) with aiohttp
- Multiple containers with appropriate partition keys
- Synthetic partition keys for leaderboard container
- Composite indexes for ORDER BY bestScore DESC, displayName ASC
- ETag-based optimistic concurrency for score submission
- Type discriminators and schema versioning on all documents
- Custom indexing policies excluding unused paths
- Autoscale throughput configuration
- Singleton CosmosClient via lifespan
"""

import os
import uuid
from datetime import datetime, timezone
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from azure.cosmos.aio import CosmosClient
from azure.cosmos import PartitionKey
from azure.cosmos.exceptions import CosmosResourceNotFoundError, CosmosHttpResponseError
from azure.core import MatchConditions

# ---------------------------------------------------------------------------
# Configuration from environment variables
# ---------------------------------------------------------------------------
COSMOS_ENDPOINT = os.environ.get("COSMOS_ENDPOINT", "https://localhost:8081")
COSMOS_KEY = os.environ.get(
    "COSMOS_KEY",
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==",
)
DATABASE_NAME = "gaming-leaderboard-db"
PLAYERS_CONTAINER = "players"
SCORES_CONTAINER = "scores"
LEADERBOARDS_CONTAINER = "leaderboards"

# ---------------------------------------------------------------------------
# Globals (singleton client)
# ---------------------------------------------------------------------------
cosmos_client: Optional[CosmosClient] = None
database = None
players_container = None
scores_container = None
leaderboards_container = None


# ---------------------------------------------------------------------------
# Cosmos DB initialization
# ---------------------------------------------------------------------------
async def init_cosmos():
    global cosmos_client, database
    global players_container, scores_container, leaderboards_container

    cosmos_client = CosmosClient(COSMOS_ENDPOINT, credential=COSMOS_KEY)
    database = await cosmos_client.create_database_if_not_exists(id=DATABASE_NAME)

    # Players container — partition on /playerId for point reads
    players_container = await database.create_container_if_not_exists(
        id=PLAYERS_CONTAINER,
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy={
            "indexingMode": "consistent",
            "automatic": True,
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [
                {"path": "/displayName/?"},
                {"path": "/averageScore/?"},
                {"path": "/schemaVersion/?"},
                {"path": '/"_etag"/?'},
            ],
            "compositeIndexes": [
                [
                    {"path": "/bestScore", "order": "descending"},
                    {"path": "/displayName", "order": "ascending"},
                ]
            ],
        },
        offer_throughput={"maxThroughput": 4000},
    )

    # Scores container — partition on /playerId
    scores_container = await database.create_container_if_not_exists(
        id=SCORES_CONTAINER,
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy={
            "indexingMode": "consistent",
            "automatic": True,
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [
                {"path": "/gameMode/?"},
                {"path": "/schemaVersion/?"},
                {"path": '/"_etag"/?'},
            ],
        },
        offer_throughput={"maxThroughput": 4000},
    )

    # Leaderboards container — synthetic partition key /leaderboardKey
    leaderboards_container = await database.create_container_if_not_exists(
        id=LEADERBOARDS_CONTAINER,
        partition_key=PartitionKey(path="/leaderboardKey"),
        indexing_policy={
            "indexingMode": "consistent",
            "automatic": True,
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [
                {"path": "/displayName/?"},
                {"path": "/schemaVersion/?"},
                {"path": '/"_etag"/?'},
            ],
            "compositeIndexes": [
                [
                    {"path": "/bestScore", "order": "descending"},
                    {"path": "/displayName", "order": "ascending"},
                ]
            ],
        },
        offer_throughput={"maxThroughput": 4000},
    )


async def close_cosmos():
    global cosmos_client
    if cosmos_client:
        await cosmos_client.close()
        cosmos_client = None


@asynccontextmanager
async def lifespan(_app: FastAPI):
    await init_cosmos()
    yield
    await close_cosmos()


app = FastAPI(lifespan=lifespan)


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
# Helpers
# ---------------------------------------------------------------------------
def player_response(doc: dict) -> dict:
    return {
        "playerId": doc["playerId"],
        "displayName": doc["displayName"],
        "region": doc["region"],
        "totalGames": doc.get("totalGames", 0),
        "bestScore": doc.get("bestScore", 0),
        "averageScore": doc.get("averageScore", 0.0),
    }


async def upsert_leaderboard_entry(player_doc: dict):
    """Upsert denormalized leaderboard entries (global + regional)."""
    best = player_doc.get("bestScore", 0)
    if best <= 0:
        return
    pid = player_doc["playerId"]
    region = player_doc["region"]
    display = player_doc["displayName"]

    for key in ["global_all-time", f"{region}_all-time"]:
        doc = {
            "id": f"{key}_{pid}",
            "leaderboardKey": key,
            "playerId": pid,
            "displayName": display,
            "region": region,
            "bestScore": best,
            "type": "leaderboardEntry",
            "schemaVersion": 1,
        }
        await leaderboards_container.upsert_item(doc)


async def remove_leaderboard_entries(player_id: str, region: str):
    """Remove all leaderboard entries for a player."""
    for key in ["global_all-time", f"{region}_all-time"]:
        try:
            await leaderboards_container.delete_item(
                item=f"{key}_{player_id}", partition_key=key
            )
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
    doc = {
        "id": req.playerId,
        "playerId": req.playerId,
        "displayName": req.displayName,
        "region": req.region,
        "totalGames": 0,
        "bestScore": 0,
        "averageScore": 0.0,
        "totalScore": 0,
        "type": "player",
        "schemaVersion": 1,
    }
    try:
        created = await players_container.create_item(body=doc)
    except CosmosHttpResponseError as e:
        if e.status_code == 409:
            raise HTTPException(status_code=409, detail="Player already exists")
        raise
    return player_response(created)


# ---------------------------------------------------------------------------
# GET /api/players/{playerId} — Get player profile
# ---------------------------------------------------------------------------
@app.get("/api/players/{playerId}")
async def get_player(playerId: str):
    try:
        doc = await players_container.read_item(item=playerId, partition_key=playerId)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")
    return player_response(doc)


# ---------------------------------------------------------------------------
# PATCH /api/players/{playerId} — Update player
# ---------------------------------------------------------------------------
@app.patch("/api/players/{playerId}")
async def update_player(playerId: str, req: UpdatePlayerRequest):
    try:
        doc = await players_container.read_item(item=playerId, partition_key=playerId)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    old_region = doc["region"]
    if req.displayName is not None:
        doc["displayName"] = req.displayName
    if req.region is not None:
        doc["region"] = req.region

    updated = await players_container.replace_item(
        item=doc["id"], body=doc, partition_key=playerId
    )

    # Refresh leaderboard entries (handles displayName / region changes)
    if updated.get("bestScore", 0) > 0:
        if req.region is not None and req.region != old_region:
            # Remove from old regional partition
            old_key = f"{old_region}_all-time"
            try:
                await leaderboards_container.delete_item(
                    item=f"{old_key}_{playerId}", partition_key=old_key
                )
            except CosmosResourceNotFoundError:
                pass
        await upsert_leaderboard_entry(updated)

    return player_response(updated)


# ---------------------------------------------------------------------------
# DELETE /api/players/{playerId} — Delete player + scores + leaderboard
# ---------------------------------------------------------------------------
@app.delete("/api/players/{playerId}")
async def delete_player(playerId: str):
    try:
        doc = await players_container.read_item(item=playerId, partition_key=playerId)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    region = doc["region"]

    # Delete player document
    await players_container.delete_item(item=playerId, partition_key=playerId)

    # Delete all score documents for this player
    score_items = scores_container.query_items(
        query="SELECT c.id FROM c WHERE c.playerId = @pid",
        parameters=[{"name": "@pid", "value": playerId}],
        partition_key=playerId,
    )
    async for s in score_items:
        try:
            await scores_container.delete_item(item=s["id"], partition_key=playerId)
        except CosmosResourceNotFoundError:
            pass

    # Remove leaderboard entries
    await remove_leaderboard_entries(playerId, region)

    return JSONResponse(status_code=204, content=None)


# ---------------------------------------------------------------------------
# POST /api/scores — Submit score (ETag-based optimistic concurrency)
# ---------------------------------------------------------------------------
@app.post("/api/scores", status_code=201)
async def submit_score(req: SubmitScoreRequest):
    # Verify player exists
    try:
        player_doc = await players_container.read_item(
            item=req.playerId, partition_key=req.playerId
        )
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Create score document
    score_id = str(uuid.uuid4())
    score_doc = {
        "id": score_id,
        "scoreId": score_id,
        "playerId": req.playerId,
        "score": req.score,
        "gameMode": req.gameMode or "default",
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "type": "score",
        "schemaVersion": 1,
    }
    await scores_container.create_item(body=score_doc)

    # Update player stats with optimistic concurrency (retry on ETag mismatch)
    max_retries = 25
    for attempt in range(max_retries):
        try:
            player_doc = await players_container.read_item(
                item=req.playerId, partition_key=req.playerId
            )
            etag = player_doc.get("_etag")

            total_games = player_doc.get("totalGames", 0) + 1
            total_score = player_doc.get("totalScore", 0) + req.score
            best_score = max(player_doc.get("bestScore", 0), req.score)
            avg = total_score / total_games if total_games > 0 else 0.0

            player_doc["totalGames"] = total_games
            player_doc["totalScore"] = total_score
            player_doc["bestScore"] = best_score
            player_doc["averageScore"] = round(avg, 2)

            updated = await players_container.replace_item(
                item=player_doc["id"],
                body=player_doc,
                partition_key=req.playerId,
                etag=etag,
                match_condition=MatchConditions.IfNotModified,
            )

            await upsert_leaderboard_entry(updated)
            break
        except CosmosHttpResponseError as e:
            if e.status_code == 412 and attempt < max_retries - 1:
                continue
            raise

    return {"scoreId": score_id, "playerId": req.playerId, "score": req.score}


# ---------------------------------------------------------------------------
# GET /api/players/{playerId}/scores — Score history
# ---------------------------------------------------------------------------
@app.get("/api/players/{playerId}/scores")
async def get_player_scores(
    playerId: str, limit: int = Query(default=10, ge=1, le=100)
):
    # Verify player exists
    try:
        await players_container.read_item(item=playerId, partition_key=playerId)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    results = []
    async for item in scores_container.query_items(
        query="SELECT * FROM c WHERE c.playerId = @pid AND c.type = 'score' ORDER BY c.timestamp DESC",
        parameters=[{"name": "@pid", "value": playerId}],
        partition_key=playerId,
        max_item_count=limit,
    ):
        results.append(
            {
                "scoreId": item.get("scoreId", item["id"]),
                "playerId": item["playerId"],
                "score": item["score"],
                "gameMode": item.get("gameMode"),
                "timestamp": item.get("timestamp"),
            }
        )
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

    entries = []
    async for item in leaderboards_container.query_items(
        query="SELECT * FROM c WHERE c.type = 'leaderboardEntry' ORDER BY c.bestScore DESC, c.displayName ASC",
        partition_key="global_all-time",
        max_item_count=top,
    ):
        entries.append(item)
        if len(entries) >= top:
            break

    return [
        {
            "rank": i + 1,
            "playerId": e["playerId"],
            "displayName": e["displayName"],
            "score": e["bestScore"],
        }
        for i, e in enumerate(entries)
    ]


# ---------------------------------------------------------------------------
# GET /api/leaderboards/regional/{region} — Regional leaderboard
# ---------------------------------------------------------------------------
@app.get("/api/leaderboards/regional/{region}")
async def regional_leaderboard(
    region: str, top: int = Query(default=100, ge=0, le=100)
):
    if top == 0:
        return []

    entries = []
    async for item in leaderboards_container.query_items(
        query="SELECT * FROM c WHERE c.type = 'leaderboardEntry' ORDER BY c.bestScore DESC, c.displayName ASC",
        partition_key=f"{region}_all-time",
        max_item_count=top,
    ):
        entries.append(item)
        if len(entries) >= top:
            break

    return [
        {
            "rank": i + 1,
            "playerId": e["playerId"],
            "displayName": e["displayName"],
            "score": e["bestScore"],
        }
        for i, e in enumerate(entries)
    ]


# ---------------------------------------------------------------------------
# GET /api/players/{playerId}/rank — Player rank with neighbors
# ---------------------------------------------------------------------------
@app.get("/api/players/{playerId}/rank")
async def get_player_rank(playerId: str):
    try:
        player_doc = await players_container.read_item(
            item=playerId, partition_key=playerId
        )
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    if player_doc.get("bestScore", 0) == 0 and player_doc.get("totalGames", 0) == 0:
        raise HTTPException(status_code=404, detail="Player has no scores")

    # Fetch full global leaderboard
    all_entries = []
    async for item in leaderboards_container.query_items(
        query="SELECT * FROM c WHERE c.type = 'leaderboardEntry' ORDER BY c.bestScore DESC, c.displayName ASC",
        partition_key="global_all-time",
    ):
        all_entries.append(item)

    # Find player position
    player_index = None
    for i, entry in enumerate(all_entries):
        if entry["playerId"] == playerId:
            player_index = i
            break

    if player_index is None:
        raise HTTPException(status_code=404, detail="Player not on leaderboard")

    player_rank = player_index + 1
    player_score = all_entries[player_index]["bestScore"]

    # Neighbors ±10
    start = max(0, player_index - 10)
    end = min(len(all_entries), player_index + 11)
    neighbors = [
        {
            "rank": idx + 1,
            "playerId": all_entries[idx]["playerId"],
            "displayName": all_entries[idx]["displayName"],
            "score": all_entries[idx]["bestScore"],
        }
        for idx in range(start, end)
    ]

    return {
        "playerId": playerId,
        "rank": player_rank,
        "score": player_score,
        "neighbors": neighbors,
    }
