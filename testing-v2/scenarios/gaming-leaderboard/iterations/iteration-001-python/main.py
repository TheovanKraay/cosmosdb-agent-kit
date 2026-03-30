"""
Gaming Leaderboard API — FastAPI + Azure Cosmos DB (NoSQL API)

Implements the gaming-leaderboard API contract with Cosmos DB best practices:
- Async SDK usage (azure.cosmos.aio) for better throughput
- Singleton CosmosClient reused across requests
- Point reads (read_item) instead of queries when ID and partition key are known
- Parameterized queries to prevent injection
- Composite indexes for ORDER BY on leaderboards
- Partition key aligned with query patterns (playerId for players/scores)
- Gateway connection mode for emulator compatibility
- SSL verification disabled for local emulator
"""

import os
import uuid
import asyncio
from datetime import datetime, timezone
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import JSONResponse

from azure.cosmos.aio import CosmosClient
from azure.cosmos import PartitionKey
from azure.cosmos.exceptions import CosmosResourceNotFoundError, CosmosHttpResponseError

import urllib3

# Suppress SSL warnings for local emulator development
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# ---------------------------------------------------------------------------
# Configuration from environment variables
# ---------------------------------------------------------------------------
COSMOS_ENDPOINT = os.environ.get(
    "COSMOS_ENDPOINT", "https://localhost:8081"
)
COSMOS_KEY = os.environ.get(
    "COSMOS_KEY",
    # Well-known emulator key — NOT for production use
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==",
)
DATABASE_NAME = "gaming-leaderboard"

# ---------------------------------------------------------------------------
# Cosmos DB client (singleton, created once at startup)
# ---------------------------------------------------------------------------
cosmos_client: CosmosClient | None = None
database = None
players_container = None
scores_container = None


async def init_cosmos():
    """Initialize the Cosmos DB client, database, and containers."""
    global cosmos_client, database, players_container, scores_container

    # Singleton client with Gateway mode for emulator compatibility
    cosmos_client = CosmosClient(
        url=COSMOS_ENDPOINT,
        credential=COSMOS_KEY,
        connection_verify=False,  # Disable SSL verification for emulator
    )

    # Create database if not exists
    database = await cosmos_client.create_database_if_not_exists(id=DATABASE_NAME)

    # Players container — partition key: /playerId (high cardinality, aligns with lookups)
    players_container = await database.create_container_if_not_exists(
        id="players",
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy={
            "indexingMode": "consistent",
            "automatic": True,
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [{"path": '/"_etag"/?'}],
            "compositeIndexes": [
                [
                    {"path": "/bestScore", "order": "descending"},
                    {"path": "/displayName", "order": "ascending"},
                ],
                [
                    {"path": "/region", "order": "ascending"},
                    {"path": "/bestScore", "order": "descending"},
                    {"path": "/displayName", "order": "ascending"},
                ],
            ],
        },
    )

    # Scores container — partition key: /playerId (aligns with per-player queries)
    scores_container = await database.create_container_if_not_exists(
        id="scores",
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy={
            "indexingMode": "consistent",
            "automatic": True,
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [{"path": '/"_etag"/?'}],
            "compositeIndexes": [
                [
                    {"path": "/playerId", "order": "ascending"},
                    {"path": "/timestamp", "order": "descending"},
                ],
            ],
        },
    )


async def close_cosmos():
    """Close the Cosmos DB client."""
    global cosmos_client
    if cosmos_client:
        await cosmos_client.close()
        cosmos_client = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan: initialize and tear down Cosmos DB resources."""
    await init_cosmos()
    yield
    await close_cosmos()


# ---------------------------------------------------------------------------
# FastAPI Application
# ---------------------------------------------------------------------------
app = FastAPI(title="Gaming Leaderboard API", lifespan=lifespan)


# ---------------------------------------------------------------------------
# Health Check
# ---------------------------------------------------------------------------
@app.get("/health")
async def health():
    return {"status": "ok"}


# ---------------------------------------------------------------------------
# Helper: format player response
# ---------------------------------------------------------------------------
def _player_response(doc: dict) -> dict:
    """Extract API-contract fields from a player document."""
    return {
        "playerId": doc["playerId"],
        "displayName": doc["displayName"],
        "region": doc["region"],
        "totalGames": doc.get("totalGames", 0),
        "bestScore": doc.get("bestScore", 0),
        "averageScore": doc.get("averageScore", 0),
    }


# ---------------------------------------------------------------------------
# Player Management
# ---------------------------------------------------------------------------
@app.post("/api/players", status_code=201)
async def create_player(body: dict):
    # Validate required fields
    missing = [f for f in ("playerId", "displayName", "region") if f not in body or not body[f]]
    if missing:
        raise HTTPException(status_code=400, detail=f"Missing required fields: {missing}")

    player_doc = {
        "id": body["playerId"],
        "playerId": body["playerId"],
        "displayName": body["displayName"],
        "region": body["region"],
        "totalGames": 0,
        "bestScore": 0,
        "averageScore": 0,
        "totalScore": 0,  # Internal: for computing averageScore
        "type": "player",
    }

    try:
        created = await players_container.create_item(body=player_doc)
    except CosmosHttpResponseError as e:
        if e.status_code == 409:
            raise HTTPException(status_code=409, detail="Player already exists")
        raise

    return _player_response(created)


@app.get("/api/players/{player_id}")
async def get_player(player_id: str):
    try:
        # Point read: id + partition key known
        doc = await players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")
    return _player_response(doc)


@app.patch("/api/players/{player_id}")
async def update_player(player_id: str, body: dict):
    try:
        doc = await players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    if "displayName" in body:
        doc["displayName"] = body["displayName"]
    if "region" in body:
        doc["region"] = body["region"]

    replaced = await players_container.replace_item(item=doc["id"], body=doc)
    return _player_response(replaced)


@app.delete("/api/players/{player_id}", status_code=204)
async def delete_player(player_id: str):
    # Verify player exists
    try:
        await players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Delete player document
    await players_container.delete_item(item=player_id, partition_key=player_id)

    # Delete all associated scores (cross-partition not needed since scores are partitioned by playerId)
    query = "SELECT c.id FROM c WHERE c.playerId = @playerId"
    params = [{"name": "@playerId", "value": player_id}]
    score_ids = []
    async for item in scores_container.query_items(
        query=query, parameters=params, partition_key=player_id
    ):
        score_ids.append(item["id"])

    for sid in score_ids:
        await scores_container.delete_item(item=sid, partition_key=player_id)

    return JSONResponse(status_code=204, content=None)


# ---------------------------------------------------------------------------
# Score Submission
# ---------------------------------------------------------------------------
@app.post("/api/scores", status_code=201)
async def submit_score(body: dict):
    # Validate required fields
    if "playerId" not in body or not body["playerId"]:
        raise HTTPException(status_code=400, detail="Missing required field: playerId")
    if "score" not in body:
        raise HTTPException(status_code=400, detail="Missing required field: score")

    score_val = body["score"]
    if not isinstance(score_val, int) or score_val < 0:
        raise HTTPException(status_code=400, detail="Score must be a non-negative integer")

    player_id = body["playerId"]

    # Validate player exists
    try:
        player_doc = await players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    score_id = str(uuid.uuid4())
    now = datetime.now(timezone.utc).isoformat()

    score_doc = {
        "id": score_id,
        "scoreId": score_id,
        "playerId": player_id,
        "score": score_val,
        "gameMode": body.get("gameMode"),
        "timestamp": now,
        "type": "score",
    }

    await scores_container.create_item(body=score_doc)

    # Update player stats with optimistic concurrency (ETag) to handle concurrent writes
    max_retries = 10
    for attempt in range(max_retries):
        try:
            # Re-read the latest player document to get fresh ETag
            player_doc = await players_container.read_item(
                item=player_id, partition_key=player_id
            )
            etag = player_doc.get("_etag")

            total_games = player_doc.get("totalGames", 0) + 1
            total_score = player_doc.get("totalScore", 0) + score_val
            best_score = max(player_doc.get("bestScore", 0), score_val)
            average_score = total_score / total_games if total_games > 0 else 0

            player_doc["totalGames"] = total_games
            player_doc["totalScore"] = total_score
            player_doc["bestScore"] = best_score
            player_doc["averageScore"] = round(average_score, 2)

            # Use if_match with ETag for optimistic concurrency control
            await players_container.replace_item(
                item=player_doc["id"],
                body=player_doc,
                if_match=etag,
            )
            break  # Success
        except CosmosHttpResponseError as e:
            if e.status_code == 412 and attempt < max_retries - 1:
                # Precondition failed (ETag mismatch) — retry with fresh read
                await asyncio.sleep(0.05 * (attempt + 1))
                continue
            raise

    return {
        "scoreId": score_id,
        "playerId": player_id,
        "score": score_val,
    }


# ---------------------------------------------------------------------------
# Score History
# ---------------------------------------------------------------------------
@app.get("/api/players/{player_id}/scores")
async def get_player_scores(player_id: str, limit: int = Query(default=10, ge=1, le=100)):
    # Verify player exists
    try:
        await players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    query = (
        "SELECT c.scoreId, c.playerId, c.score, c.gameMode, c.timestamp "
        "FROM c WHERE c.playerId = @playerId "
        "ORDER BY c.timestamp DESC"
    )
    params = [{"name": "@playerId", "value": player_id}]

    results = []
    async for item in scores_container.query_items(
        query=query, parameters=params, partition_key=player_id
    ):
        results.append(item)
        if len(results) >= limit:
            break

    return results


# ---------------------------------------------------------------------------
# Global Leaderboard
# ---------------------------------------------------------------------------
@app.get("/api/leaderboards/global")
async def global_leaderboard(top: int = Query(default=100, ge=1, le=100)):
    query = (
        "SELECT c.playerId, c.displayName, c.bestScore, c.region "
        "FROM c WHERE c.bestScore > 0 "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )

    results = []
    async for item in players_container.query_items(
        query=query, enable_cross_partition_query=True
    ):
        results.append(item)
        if len(results) >= top:
            break

    # Build leaderboard with 1-based ranks
    leaderboard = []
    for i, item in enumerate(results):
        leaderboard.append({
            "rank": i + 1,
            "playerId": item["playerId"],
            "displayName": item["displayName"],
            "score": item["bestScore"],
        })

    return leaderboard


# ---------------------------------------------------------------------------
# Regional Leaderboard
# ---------------------------------------------------------------------------
@app.get("/api/leaderboards/regional/{region}")
async def regional_leaderboard(region: str, top: int = Query(default=100, ge=1, le=100)):
    query = (
        "SELECT c.playerId, c.displayName, c.bestScore "
        "FROM c WHERE c.region = @region AND c.bestScore > 0 "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )
    params = [{"name": "@region", "value": region}]

    results = []
    async for item in players_container.query_items(
        query=query, parameters=params, enable_cross_partition_query=True
    ):
        results.append(item)
        if len(results) >= top:
            break

    leaderboard = []
    for i, item in enumerate(results):
        leaderboard.append({
            "rank": i + 1,
            "playerId": item["playerId"],
            "displayName": item["displayName"],
            "score": item["bestScore"],
        })

    return leaderboard


# ---------------------------------------------------------------------------
# Player Rank
# ---------------------------------------------------------------------------
@app.get("/api/players/{player_id}/rank")
async def get_player_rank(player_id: str):
    # Verify player exists and has scores
    try:
        player_doc = await players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    if player_doc.get("bestScore", 0) == 0:
        raise HTTPException(status_code=404, detail="Player has no scores")

    # Get all players sorted by bestScore desc, displayName asc for ranking
    query = (
        "SELECT c.playerId, c.displayName, c.bestScore "
        "FROM c WHERE c.bestScore > 0 "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )

    all_players = []
    async for item in players_container.query_items(
        query=query, enable_cross_partition_query=True
    ):
        all_players.append(item)

    # Find the player's position
    player_rank = None
    player_index = None
    for i, p in enumerate(all_players):
        if p["playerId"] == player_id:
            player_rank = i + 1  # 1-based
            player_index = i
            break

    if player_rank is None:
        raise HTTPException(status_code=404, detail="Player not found in rankings")

    # Get neighbors: ±10 positions
    start = max(0, player_index - 10)
    end = min(len(all_players), player_index + 11)

    neighbors = []
    for i in range(start, end):
        if i == player_index:
            continue  # Skip the player themselves
        neighbors.append({
            "rank": i + 1,
            "playerId": all_players[i]["playerId"],
            "displayName": all_players[i]["displayName"],
            "score": all_players[i]["bestScore"],
        })

    return {
        "playerId": player_id,
        "rank": player_rank,
        "score": player_doc["bestScore"],
        "neighbors": neighbors,
    }
