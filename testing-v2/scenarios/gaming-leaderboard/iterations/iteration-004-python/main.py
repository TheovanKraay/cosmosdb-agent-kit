"""
Gaming Leaderboard API
======================
FastAPI application backed by Azure Cosmos DB (NoSQL API).

Best practices applied:
- Async SDK (azure.cosmos.aio) for better throughput
- Singleton CosmosClient reused across requests
- Point reads for known id + partition key
- Literal integers for TOP (not parameterized)
- Composite indexes matching ORDER BY directions
- Partition key aligned with query patterns
- Denormalized player stats updated inline at write time
- ETag-based optimistic concurrency for safe concurrent updates
- Type discriminator and schema version on all documents
- Excluded unused paths from indexing to reduce write RU cost
"""

import os
import uuid
from datetime import datetime, timezone
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import JSONResponse
from pydantic import BaseModel, field_validator
from azure.cosmos.aio import CosmosClient
from azure.cosmos import PartitionKey
from azure.cosmos.exceptions import CosmosResourceNotFoundError, CosmosHttpResponseError

MAX_RETRIES = 10

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
COSMOS_ENDPOINT = os.environ.get("COSMOS_ENDPOINT", "https://localhost:8081")
COSMOS_KEY = os.environ.get(
    "COSMOS_KEY",
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==",
)
DATABASE_NAME = "gaming-leaderboard"

PLAYERS_CONTAINER = "players"
SCORES_CONTAINER = "scores"

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

    @field_validator("score")
    @classmethod
    def score_must_be_non_negative(cls, v: int) -> int:
        if v < 0:
            raise ValueError("score must be a non-negative integer")
        return v


# ---------------------------------------------------------------------------
# Cosmos DB initialisation (singleton client)
# ---------------------------------------------------------------------------
cosmos_client: Optional[CosmosClient] = None
players_container = None
scores_container = None


async def init_cosmos():
    """Create database and containers with optimal configuration."""
    global cosmos_client, players_container, scores_container

    cosmos_client = CosmosClient(COSMOS_ENDPOINT, credential=COSMOS_KEY)
    database = await cosmos_client.create_database_if_not_exists(id=DATABASE_NAME)

    # Players container – partitioned by /playerId for point reads.
    # Includes denormalized stats (bestScore, totalGames, averageScore)
    # so leaderboard queries can read from this container directly.
    players_container = await database.create_container_if_not_exists(
        id=PLAYERS_CONTAINER,
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy={
            "indexingMode": "consistent",
            "automatic": True,
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [
                {"path": '/"_etag"/?'},
                {"path": "/totalScore/?"},
                {"path": "/schemaVersion/?"},
            ],
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

    # Scores container – partitioned by /playerId for efficient score history
    scores_container = await database.create_container_if_not_exists(
        id=SCORES_CONTAINER,
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy={
            "indexingMode": "consistent",
            "automatic": True,
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [
                {"path": '/"_etag"/?'},
                {"path": "/gameMode/?"},
                {"path": "/schemaVersion/?"},
            ],
            "compositeIndexes": [
                [
                    {"path": "/playerId", "order": "ascending"},
                    {"path": "/timestamp", "order": "descending"},
                ]
            ],
        },
    )


async def close_cosmos():
    global cosmos_client
    if cosmos_client:
        await cosmos_client.close()
        cosmos_client = None


# ---------------------------------------------------------------------------
# FastAPI lifespan
# ---------------------------------------------------------------------------
@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_cosmos()
    yield
    await close_cosmos()


app = FastAPI(lifespan=lifespan)


# ---------------------------------------------------------------------------
# Health
# ---------------------------------------------------------------------------
@app.get("/health")
async def health():
    return {"status": "ok"}


# ---------------------------------------------------------------------------
# Player Management
# ---------------------------------------------------------------------------

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
        "type": "player",
        "schemaVersion": 1,
    }
    try:
        await players_container.create_item(body=player_doc)
    except CosmosHttpResponseError as e:
        if e.status_code == 409:
            raise HTTPException(status_code=409, detail="Player already exists")
        raise
    return _player_response(player_doc)


@app.get("/api/players/{player_id}")
async def get_player(player_id: str):
    doc = await _read_player(player_id)
    return _player_response(doc)


@app.patch("/api/players/{player_id}")
async def update_player(player_id: str, req: UpdatePlayerRequest):
    doc = await _read_player(player_id)

    if req.displayName is not None:
        doc["displayName"] = req.displayName
    if req.region is not None:
        doc["region"] = req.region

    await players_container.replace_item(
        item=doc["id"],
        body=doc,
        partition_key=player_id,
        etag=doc.get("_etag"),
        match_condition="IfMatch",
    )
    return _player_response(doc)


@app.delete("/api/players/{player_id}", status_code=204)
async def delete_player(player_id: str):
    await _read_player(player_id)

    # Delete all scores for this player
    query = "SELECT c.id FROM c WHERE c.playerId = @pid"
    params = [{"name": "@pid", "value": player_id}]
    score_ids = []
    async for item in scores_container.query_items(
        query=query, parameters=params, partition_key=player_id
    ):
        score_ids.append(item["id"])

    for sid in score_ids:
        try:
            await scores_container.delete_item(item=sid, partition_key=player_id)
        except CosmosResourceNotFoundError:
            pass

    # Delete the player
    try:
        await players_container.delete_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        pass
    return JSONResponse(status_code=204, content=None)


# ---------------------------------------------------------------------------
# Score Submission
# ---------------------------------------------------------------------------

@app.post("/api/scores", status_code=201)
async def submit_score(req: SubmitScoreRequest):
    # Verify the player exists (returns 404 → maps to 4xx for nonexistent player)
    player_doc = await _read_player(req.playerId)

    score_id = str(uuid.uuid4())
    now = datetime.now(timezone.utc).isoformat()

    score_doc = {
        "id": score_id,
        "scoreId": score_id,
        "playerId": req.playerId,
        "score": req.score,
        "timestamp": now,
        "type": "score",
        "schemaVersion": 1,
    }
    if req.gameMode is not None:
        score_doc["gameMode"] = req.gameMode

    await scores_container.create_item(body=score_doc)

    # Update player stats with ETag-based optimistic concurrency (retry on conflict)
    for attempt in range(MAX_RETRIES):
        total_games = player_doc.get("totalGames", 0) + 1
        total_score = player_doc.get("totalScore", 0) + req.score
        best_score = max(player_doc.get("bestScore", 0), req.score)
        average_score = total_score / total_games if total_games > 0 else 0.0

        player_doc["totalGames"] = total_games
        player_doc["totalScore"] = total_score
        player_doc["bestScore"] = best_score
        player_doc["averageScore"] = average_score

        try:
            await players_container.replace_item(
                item=player_doc["id"],
                body=player_doc,
                partition_key=req.playerId,
                etag=player_doc.get("_etag"),
                match_condition="IfMatch",
            )
            break
        except CosmosHttpResponseError as e:
            if e.status_code == 412 and attempt < MAX_RETRIES - 1:
                # ETag mismatch — re-read and retry
                player_doc = await players_container.read_item(
                    item=req.playerId, partition_key=req.playerId
                )
            else:
                raise

    return {
        "scoreId": score_id,
        "playerId": req.playerId,
        "score": req.score,
    }


# ---------------------------------------------------------------------------
# Leaderboards
# ---------------------------------------------------------------------------

@app.get("/api/leaderboards/global")
async def global_leaderboard(top: int = Query(default=100, ge=0, le=100)):
    top = int(top)
    if top == 0:
        return []

    query = (
        f"SELECT TOP {top} c.playerId, c.displayName, c.bestScore "
        "FROM c WHERE c.bestScore > 0 "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )

    entries = []
    async for item in players_container.query_items(
        query=query, enable_cross_partition_query=True
    ):
        entries.append(item)

    return _ranked_entries(entries)


@app.get("/api/leaderboards/regional/{region}")
async def regional_leaderboard(
    region: str, top: int = Query(default=100, ge=0, le=100)
):
    top = int(top)
    if top == 0:
        return []

    query = (
        f"SELECT TOP {top} c.playerId, c.displayName, c.bestScore "
        "FROM c WHERE c.region = @region AND c.bestScore > 0 "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )
    params = [{"name": "@region", "value": region}]

    entries = []
    async for item in players_container.query_items(
        query=query, parameters=params, enable_cross_partition_query=True
    ):
        entries.append(item)

    return _ranked_entries(entries)


# ---------------------------------------------------------------------------
# Player Ranking
# ---------------------------------------------------------------------------

@app.get("/api/players/{player_id}/rank")
async def player_rank(player_id: str):
    player_doc = await _read_player(player_id)
    best_score = player_doc.get("bestScore", 0)

    if best_score == 0 and player_doc.get("totalGames", 0) == 0:
        raise HTTPException(status_code=404, detail="Player has no scores")

    # COUNT-based rank: count players with higher score,
    # or same score but earlier in tiebreak (displayName ascending)
    count_query = (
        "SELECT VALUE COUNT(1) FROM c WHERE "
        "(c.bestScore > @score) OR "
        "(c.bestScore = @score AND c.displayName < @name)"
    )
    params = [
        {"name": "@score", "value": best_score},
        {"name": "@name", "value": player_doc["displayName"]},
    ]

    rank = 1
    async for item in players_container.query_items(
        query=count_query, parameters=params, enable_cross_partition_query=True
    ):
        rank = item + 1

    # Get neighbours: fetch all ranked players, find position, return ±10
    all_query = (
        "SELECT c.playerId, c.displayName, c.bestScore "
        "FROM c WHERE c.bestScore > 0 "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )
    all_entries = []
    async for item in players_container.query_items(
        query=all_query, enable_cross_partition_query=True
    ):
        all_entries.append(item)

    # Find index of this player
    player_index = -1
    for i, entry in enumerate(all_entries):
        if entry["playerId"] == player_id:
            player_index = i
            break

    neighbors = []
    if player_index >= 0:
        start = max(0, player_index - 10)
        end = min(len(all_entries), player_index + 11)
        for i in range(start, end):
            if all_entries[i]["playerId"] == player_id:
                continue
            neighbors.append(
                {
                    "rank": i + 1,
                    "playerId": all_entries[i]["playerId"],
                    "displayName": all_entries[i]["displayName"],
                    "score": all_entries[i]["bestScore"],
                }
            )

    return {
        "playerId": player_id,
        "rank": rank,
        "score": best_score,
        "neighbors": neighbors,
    }


# ---------------------------------------------------------------------------
# Score History
# ---------------------------------------------------------------------------

@app.get("/api/players/{player_id}/scores")
async def get_player_scores(
    player_id: str, limit: int = Query(default=10, ge=1, le=100)
):
    await _read_player(player_id)

    limit = int(limit)
    query = (
        f"SELECT TOP {limit} c.scoreId, c.playerId, c.score, c.gameMode, c.timestamp "
        "FROM c WHERE c.playerId = @pid "
        "ORDER BY c.timestamp DESC"
    )
    params = [{"name": "@pid", "value": player_id}]

    scores = []
    async for item in scores_container.query_items(
        query=query, parameters=params, partition_key=player_id
    ):
        scores.append(item)

    return scores


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

async def _read_player(player_id: str) -> dict:
    """Point read a player by id and partition key. Raises 404 if not found."""
    try:
        return await players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")
    except CosmosHttpResponseError as e:
        if e.status_code == 404:
            raise HTTPException(status_code=404, detail="Player not found")
        raise


def _player_response(doc: dict) -> dict:
    """Return only the fields required by the API contract."""
    return {
        "playerId": doc["playerId"],
        "displayName": doc["displayName"],
        "region": doc["region"],
        "totalGames": doc["totalGames"],
        "bestScore": doc["bestScore"],
        "averageScore": doc["averageScore"],
    }


def _ranked_entries(entries: list) -> list:
    """Assign 1-based rank to leaderboard entries."""
    return [
        {
            "rank": i + 1,
            "playerId": e["playerId"],
            "displayName": e["displayName"],
            "score": e["bestScore"],
        }
        for i, e in enumerate(entries)
    ]
