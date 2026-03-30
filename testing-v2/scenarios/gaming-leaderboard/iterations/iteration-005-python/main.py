"""
Gaming Leaderboard API — FastAPI + Azure Cosmos DB (NoSQL API)

Implements the gaming-leaderboard API contract with Cosmos DB best practices:
- Async SDK with aiohttp (Rule 4.1, 4.15)
- Singleton CosmosClient (Rule 4.18)
- Gateway mode + SSL disabled for emulator (Rule 4.6)
- Point reads when id and partition key are known (Rule 3.7)
- Parameterized queries (Rule 3.6)
- Partition key aligned with query patterns (Rule 2.7)
- Composite indexes for ORDER BY (Rule 5.1, 5.2)
- COUNT-based rank approach (Rule 9.2)
- Denormalized player stats updated at write time (Rule 1.2)
"""

import os
import uuid
from datetime import datetime, timezone
from contextlib import asynccontextmanager
from typing import Optional

import urllib3
from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel
from azure.cosmos.aio import CosmosClient
from azure.cosmos import PartitionKey, exceptions

# Suppress SSL warnings for emulator (Rule 4.6)
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# Environment variables for Cosmos DB connection
COSMOS_ENDPOINT = os.environ.get(
    "COSMOS_ENDPOINT", "https://localhost:8081"
)
COSMOS_KEY = os.environ.get(
    "COSMOS_KEY",
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==",
)
DATABASE_NAME = "gaming-leaderboard"

# Singleton client reference (Rule 4.18)
cosmos_client: Optional[CosmosClient] = None
database = None
players_container = None
scores_container = None


async def init_cosmos():
    """Initialize Cosmos DB client, database, and containers."""
    global cosmos_client, database, players_container, scores_container

    # Gateway mode is default for Python SDK; disable SSL for emulator (Rule 4.6)
    cosmos_client = CosmosClient(
        url=COSMOS_ENDPOINT,
        credential=COSMOS_KEY,
        connection_verify=False,
    )

    # Create database if not exists
    database = await cosmos_client.create_database_if_not_exists(id=DATABASE_NAME)

    # Players container — partitioned by /playerId for point reads (Rule 2.7, 3.7)
    # Indexing policy includes root path /* (required) plus composite indexes
    players_indexing_policy = {
        "includedPaths": [{"path": "/*"}],
        "excludedPaths": [{"path": '/"_etag"/?'}],
        "compositeIndexes": [
            # For global/regional leaderboard: ORDER BY bestScore DESC, displayName ASC
            [
                {"path": "/bestScore", "order": "descending"},
                {"path": "/displayName", "order": "ascending"},
            ]
        ],
    }
    players_container = await database.create_container_if_not_exists(
        id="players",
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy=players_indexing_policy,
    )

    # Scores container — partitioned by /playerId for per-player queries (Rule 2.7)
    # Single-field ORDER BY (timestamp DESC) is covered by the default /* index;
    # composite indexes require at least 2 paths, so none needed here.
    scores_indexing_policy = {
        "includedPaths": [{"path": "/*"}],
        "excludedPaths": [{"path": '/"_etag"/?'}],
    }
    scores_container = await database.create_container_if_not_exists(
        id="scores",
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy=scores_indexing_policy,
    )


async def close_cosmos():
    """Close the Cosmos DB client."""
    global cosmos_client
    if cosmos_client:
        await cosmos_client.close()
        cosmos_client = None


@asynccontextmanager
async def lifespan(application: FastAPI):
    """Application lifespan: init and close Cosmos DB."""
    await init_cosmos()
    yield
    await close_cosmos()


app = FastAPI(title="Gaming Leaderboard API", lifespan=lifespan)


# --------------- Pydantic models ---------------

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


# --------------- Helper functions ---------------

def _strip_system_fields(doc: dict) -> dict:
    """Remove Cosmos DB system metadata fields from a document."""
    return {k: v for k, v in doc.items() if not k.startswith("_")}


def _player_response(doc: dict) -> dict:
    """Build a player response with the required fields."""
    return {
        "playerId": doc["playerId"],
        "displayName": doc["displayName"],
        "region": doc["region"],
        "totalGames": doc.get("totalGames", 0),
        "bestScore": doc.get("bestScore", 0),
        "averageScore": doc.get("averageScore", 0),
    }


# --------------- Health endpoint ---------------

@app.get("/health")
async def health():
    return {"status": "ok"}


# --------------- Player Management ---------------

@app.post("/api/players", status_code=201)
async def create_player(req: CreatePlayerRequest):
    player_doc = {
        "id": req.playerId,
        "playerId": req.playerId,
        "displayName": req.displayName,
        "region": req.region,
        "totalGames": 0,
        "bestScore": 0,
        "averageScore": 0.0,
        "totalScore": 0,
    }
    try:
        result = await players_container.create_item(body=player_doc)
    except exceptions.CosmosResourceExistsError:
        raise HTTPException(status_code=409, detail="Player already exists")
    return _player_response(result)


@app.get("/api/players/{player_id}")
async def get_player(player_id: str):
    try:
        # Point read — 1 RU (Rule 3.7)
        result = await players_container.read_item(
            item=player_id, partition_key=player_id
        )
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")
    return _player_response(result)


@app.patch("/api/players/{player_id}")
async def update_player(player_id: str, req: UpdatePlayerRequest):
    try:
        # Point read first (Rule 3.7)
        player = await players_container.read_item(
            item=player_id, partition_key=player_id
        )
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    if req.displayName is not None:
        player["displayName"] = req.displayName
    if req.region is not None:
        player["region"] = req.region

    result = await players_container.replace_item(item=player_id, body=player)
    return _player_response(result)


@app.delete("/api/players/{player_id}", status_code=204)
async def delete_player(player_id: str):
    # Verify player exists
    try:
        await players_container.read_item(item=player_id, partition_key=player_id)
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Delete the player document
    await players_container.delete_item(item=player_id, partition_key=player_id)

    # Delete all associated scores (same partition key)
    query = "SELECT c.id FROM c WHERE c.playerId = @playerId"
    params = [{"name": "@playerId", "value": player_id}]
    score_ids = []
    async for item in scores_container.query_items(
        query=query,
        parameters=params,
        partition_key=player_id,
    ):
        score_ids.append(item["id"])

    for score_id in score_ids:
        await scores_container.delete_item(item=score_id, partition_key=player_id)

    return None


# --------------- Score Submission ---------------

@app.post("/api/scores", status_code=201)
async def submit_score(req: SubmitScoreRequest):
    # Verify player exists
    try:
        player = await players_container.read_item(
            item=req.playerId, partition_key=req.playerId
        )
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    score_id = str(uuid.uuid4())
    now = datetime.now(timezone.utc).isoformat()

    score_doc = {
        "id": score_id,
        "scoreId": score_id,
        "playerId": req.playerId,
        "score": req.score,
        "timestamp": now,
    }
    if req.gameMode is not None:
        score_doc["gameMode"] = req.gameMode

    await scores_container.create_item(body=score_doc)

    # Update player stats inline (denormalize — Rule 1.2)
    total_games = player.get("totalGames", 0) + 1
    total_score = player.get("totalScore", 0) + req.score
    best_score = max(player.get("bestScore", 0), req.score)
    average_score = total_score / total_games

    player["totalGames"] = total_games
    player["totalScore"] = total_score
    player["bestScore"] = best_score
    player["averageScore"] = average_score

    await players_container.replace_item(item=req.playerId, body=player)

    return {
        "scoreId": score_id,
        "playerId": req.playerId,
        "score": req.score,
    }


# --------------- Leaderboards ---------------

@app.get("/api/leaderboards/global")
async def global_leaderboard(top: int = Query(default=100, ge=1, le=100)):
    # Cross-partition query needed for global leaderboard
    # Composite index on (bestScore DESC, displayName ASC) for efficient ORDER BY
    # LIMIT uses f-string because Cosmos DB requires literal integers (Rule 3.8);
    # 'top' is validated by FastAPI Query(ge=1, le=100) so int(top) is safe.
    query = f"SELECT c.playerId, c.displayName, c.bestScore FROM c WHERE c.bestScore >= 0 ORDER BY c.bestScore DESC, c.displayName ASC OFFSET 0 LIMIT {int(top)}"

    entries = []
    async for item in players_container.query_items(
        query=query,
        enable_cross_partition_query=True,
    ):
        entries.append(item)

    result = []
    for rank, entry in enumerate(entries, start=1):
        result.append({
            "rank": rank,
            "playerId": entry["playerId"],
            "displayName": entry["displayName"],
            "score": entry["bestScore"],
        })
    return result


@app.get("/api/leaderboards/regional/{region}")
async def regional_leaderboard(
    region: str, top: int = Query(default=100, ge=1, le=100)
):
    # LIMIT uses f-string: Cosmos DB requires literal integers (Rule 3.8);
    # 'top' validated by FastAPI Query(ge=1, le=100).
    query = f"SELECT c.playerId, c.displayName, c.bestScore FROM c WHERE c.region = @region AND c.bestScore >= 0 ORDER BY c.bestScore DESC, c.displayName ASC OFFSET 0 LIMIT {int(top)}"
    params = [{"name": "@region", "value": region}]

    entries = []
    async for item in players_container.query_items(
        query=query,
        parameters=params,
        enable_cross_partition_query=True,
    ):
        entries.append(item)

    result = []
    for rank, entry in enumerate(entries, start=1):
        result.append({
            "rank": rank,
            "playerId": entry["playerId"],
            "displayName": entry["displayName"],
            "score": entry["bestScore"],
        })
    return result


# --------------- Player Ranking ---------------

@app.get("/api/players/{player_id}/rank")
async def player_rank(player_id: str):
    # Point read to get the player (Rule 3.7)
    try:
        player = await players_container.read_item(
            item=player_id, partition_key=player_id
        )
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    best_score = player.get("bestScore", 0)
    display_name = player.get("displayName", "")

    if best_score == 0 and player.get("totalGames", 0) == 0:
        raise HTTPException(status_code=404, detail="Player has no scores")

    # COUNT-based rank (Rule 9.2) — count players with higher score,
    # or same score but displayName comes before this player alphabetically
    count_query = (
        "SELECT VALUE COUNT(1) FROM c WHERE "
        "c.bestScore > @score OR "
        "(c.bestScore = @score AND c.displayName < @displayName)"
    )
    params = [
        {"name": "@score", "value": best_score},
        {"name": "@displayName", "value": display_name},
    ]

    rank = 1
    async for count_val in players_container.query_items(
        query=count_query,
        parameters=params,
        enable_cross_partition_query=True,
    ):
        rank = count_val + 1

    # Get neighbors: ±10 positions around the player
    # Fetch players ranked above (higher score or same score + earlier name)
    above_query = (
        f"SELECT c.playerId, c.displayName, c.bestScore FROM c "
        f"WHERE c.bestScore > @score OR "
        f"(c.bestScore = @score AND c.displayName < @displayName) "
        f"ORDER BY c.bestScore ASC, c.displayName DESC "
        f"OFFSET 0 LIMIT 10"
    )
    above_entries = []
    async for item in players_container.query_items(
        query=above_query,
        parameters=params,
        enable_cross_partition_query=True,
    ):
        above_entries.append(item)
    above_entries.reverse()  # Now highest to lowest

    # Fetch players ranked below (lower score or same score + later name)
    below_query = (
        f"SELECT c.playerId, c.displayName, c.bestScore FROM c "
        f"WHERE c.bestScore < @score OR "
        f"(c.bestScore = @score AND c.displayName > @displayName) "
        f"ORDER BY c.bestScore DESC, c.displayName ASC "
        f"OFFSET 0 LIMIT 10"
    )
    below_entries = []
    async for item in players_container.query_items(
        query=below_query,
        parameters=params,
        enable_cross_partition_query=True,
    ):
        below_entries.append(item)

    # Build neighbors list with ranks
    neighbors = []
    for i, entry in enumerate(above_entries):
        neighbor_rank = rank - len(above_entries) + i
        neighbors.append({
            "rank": neighbor_rank,
            "playerId": entry["playerId"],
            "displayName": entry["displayName"],
            "score": entry["bestScore"],
        })

    # Add the player themselves
    neighbors.append({
        "rank": rank,
        "playerId": player_id,
        "displayName": display_name,
        "score": best_score,
    })

    for i, entry in enumerate(below_entries):
        neighbors.append({
            "rank": rank + 1 + i,
            "playerId": entry["playerId"],
            "displayName": entry["displayName"],
            "score": entry["bestScore"],
        })

    return {
        "playerId": player_id,
        "rank": rank,
        "score": best_score,
        "neighbors": neighbors,
    }


# --------------- Score History ---------------

@app.get("/api/players/{player_id}/scores")
async def get_player_scores(
    player_id: str, limit: int = Query(default=10, ge=1, le=100)
):
    # Verify player exists (Rule 3.7 — point read)
    try:
        await players_container.read_item(item=player_id, partition_key=player_id)
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Single-partition query (Rule 3.1), parameterized (Rule 3.6).
    # LIMIT uses f-string: Cosmos DB requires literal integers (Rule 3.8);
    # 'limit' validated by FastAPI Query(ge=1, le=100).
    query = f"SELECT c.scoreId, c.playerId, c.score, c.gameMode, c.timestamp FROM c WHERE c.playerId = @playerId ORDER BY c.timestamp DESC OFFSET 0 LIMIT {int(limit)}"
    params = [{"name": "@playerId", "value": player_id}]

    scores = []
    async for item in scores_container.query_items(
        query=query,
        parameters=params,
        partition_key=player_id,
    ):
        scores.append(item)

    return scores
