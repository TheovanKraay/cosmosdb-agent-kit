"""
Gaming Leaderboard API - FastAPI + Azure Cosmos DB (NoSQL API)

Implements the gaming-leaderboard API contract with Cosmos DB best practices:
- Rule 1.2: Denormalize for read-heavy workloads (leaderboard entries)
- Rule 2.4: High-cardinality partition keys (playerId for players/scores)
- Rule 3.1: Minimize cross-partition queries (partition-aligned queries)
- Rule 3.5: Parameterized queries
- Rule 3.7: Point reads for known ID + partition key
- Rule 4.17: Singleton CosmosClient
- Rule 5.1/5.2: Composite indexes matching ORDER BY
- Rule 9.2: COUNT-based ranking approach
"""

import os
import uuid
import logging
from datetime import datetime, timezone
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from azure.cosmos import CosmosClient, PartitionKey
from azure.cosmos.exceptions import CosmosResourceNotFoundError, CosmosHttpResponseError

# ---------------------------------------------------------------------------
# Configuration (Rule 4.17: read from env vars, never hardcode)
# ---------------------------------------------------------------------------
COSMOS_ENDPOINT = os.environ.get(
    "COSMOS_ENDPOINT", "https://localhost:8081"
)
COSMOS_KEY = os.environ.get(
    "COSMOS_KEY",
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==",
)
DATABASE_NAME = os.environ.get("COSMOS_DATABASE", "gaming-leaderboard")

logger = logging.getLogger("leaderboard")
logging.basicConfig(level=logging.INFO)

# ---------------------------------------------------------------------------
# Pydantic models (request / response)
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
# Cosmos DB initialisation helpers
# ---------------------------------------------------------------------------

_cosmos_client: Optional[CosmosClient] = None
_players_container = None
_scores_container = None


def _is_emulator(endpoint: str) -> bool:
    return "localhost" in endpoint or "127.0.0.1" in endpoint


def get_cosmos_client() -> CosmosClient:
    """Rule 4.17: Reuse CosmosClient as a singleton."""
    global _cosmos_client
    if _cosmos_client is None:
        _cosmos_client = CosmosClient(
            url=COSMOS_ENDPOINT,
            credential=COSMOS_KEY,
            connection_verify=not _is_emulator(COSMOS_ENDPOINT),
        )
    return _cosmos_client


def initialize_database():
    """Create database and containers with proper indexing policies."""
    global _players_container, _scores_container

    client = get_cosmos_client()
    database = client.create_database_if_not_exists(id=DATABASE_NAME)

    # Players container – partition key /id for point reads (Rule 3.7)
    # Composite index for leaderboard queries: bestScore DESC, displayName ASC
    players_indexing = {
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
            ],
        ],
    }

    _players_container = database.create_container_if_not_exists(
        id="players",
        partition_key=PartitionKey(path="/id"),
        indexing_policy=players_indexing,
    )

    # Scores container – partition key /playerId for per-player queries (Rule 2.7)
    scores_indexing = {
        "includedPaths": [{"path": "/*"}],
        "excludedPaths": [{"path": '/"_etag"/?'}],
        "compositeIndexes": [
            [
                {"path": "/playerId", "order": "ascending"},
                {"path": "/timestamp", "order": "descending"},
            ]
        ],
    }

    _scores_container = database.create_container_if_not_exists(
        id="scores",
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy=scores_indexing,
    )

    logger.info("Cosmos DB database and containers initialized.")


# ---------------------------------------------------------------------------
# Application lifespan
# ---------------------------------------------------------------------------

@asynccontextmanager
async def lifespan(app: FastAPI):
    initialize_database()
    yield


app = FastAPI(lifespan=lifespan)


# ---------------------------------------------------------------------------
# Health endpoint
# ---------------------------------------------------------------------------

@app.get("/health")
async def health():
    return JSONResponse(content={"status": "ok"}, status_code=200)


# ---------------------------------------------------------------------------
# Player Management
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
        "type": "player",
    }
    result = _players_container.create_item(body=doc)
    return _player_response(result)


@app.get("/api/players/{player_id}")
async def get_player(player_id: str):
    try:
        # Rule 3.7: Point read by id + partition key
        result = _players_container.read_item(item=player_id, partition_key=player_id)
        return _player_response(result)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")


@app.patch("/api/players/{player_id}")
async def update_player(player_id: str, req: UpdatePlayerRequest):
    try:
        existing = _players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    if req.displayName is not None:
        existing["displayName"] = req.displayName
    if req.region is not None:
        existing["region"] = req.region

    result = _players_container.replace_item(item=player_id, body=existing)
    return _player_response(result)


@app.delete("/api/players/{player_id}", status_code=204)
async def delete_player(player_id: str):
    # Verify the player exists first
    try:
        _players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Delete player document
    _players_container.delete_item(item=player_id, partition_key=player_id)

    # Delete all associated score documents (Rule: cascade delete)
    scores = list(
        _scores_container.query_items(
            query="SELECT c.id FROM c WHERE c.playerId = @pid",
            parameters=[{"name": "@pid", "value": player_id}],
            partition_key=player_id,
        )
    )
    for score_doc in scores:
        _scores_container.delete_item(item=score_doc["id"], partition_key=player_id)

    return JSONResponse(content=None, status_code=204)


def _player_response(doc: dict) -> dict:
    """Build the canonical player response object."""
    return {
        "playerId": doc["playerId"],
        "displayName": doc["displayName"],
        "region": doc["region"],
        "totalGames": doc.get("totalGames", 0),
        "bestScore": doc.get("bestScore", 0),
        "averageScore": doc.get("averageScore", 0),
    }


# ---------------------------------------------------------------------------
# Score Submission
# ---------------------------------------------------------------------------

@app.post("/api/scores", status_code=201)
async def submit_score(req: SubmitScoreRequest):
    # Verify the player exists
    try:
        player = _players_container.read_item(
            item=req.playerId, partition_key=req.playerId
        )
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

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
    }
    _scores_container.create_item(body=score_doc)

    # Update player stats (Rule 1.2: denormalize for reads)
    total_games = player.get("totalGames", 0) + 1
    best_score = max(player.get("bestScore", 0), req.score)
    # Compute running average
    prev_avg = player.get("averageScore", 0)
    prev_total = player.get("totalGames", 0)
    new_avg = ((prev_avg * prev_total) + req.score) / total_games

    player["totalGames"] = total_games
    player["bestScore"] = best_score
    player["averageScore"] = round(new_avg, 2)

    _players_container.replace_item(item=req.playerId, body=player)

    return {
        "scoreId": score_id,
        "playerId": req.playerId,
        "score": req.score,
    }


# ---------------------------------------------------------------------------
# Leaderboards
# ---------------------------------------------------------------------------

@app.get("/api/leaderboards/global")
async def global_leaderboard(top: int = Query(default=100, le=100, ge=1)):
    """
    Global leaderboard: all players sorted by bestScore DESC,
    then displayName ASC for deterministic tiebreaking.
    Cross-partition query (required for global view).
    """
    query = (
        "SELECT c.playerId, c.displayName, c.bestScore "
        "FROM c WHERE c.type = 'player' AND c.bestScore > 0 "
        "ORDER BY c.bestScore DESC, c.displayName ASC "
        "OFFSET 0 LIMIT @top"
    )
    items = list(
        _players_container.query_items(
            query=query,
            parameters=[{"name": "@top", "value": top}],
            enable_cross_partition_query=True,
        )
    )
    return _build_ranked_list(items)


@app.get("/api/leaderboards/regional/{region}")
async def regional_leaderboard(region: str, top: int = Query(default=100, le=100, ge=1)):
    """
    Regional leaderboard for a specific region.
    Cross-partition query filtered by region.
    """
    query = (
        "SELECT c.playerId, c.displayName, c.bestScore "
        "FROM c WHERE c.type = 'player' AND c.region = @region AND c.bestScore > 0 "
        "ORDER BY c.bestScore DESC, c.displayName ASC "
        "OFFSET 0 LIMIT @top"
    )
    items = list(
        _players_container.query_items(
            query=query,
            parameters=[
                {"name": "@region", "value": region},
                {"name": "@top", "value": top},
            ],
            enable_cross_partition_query=True,
        )
    )
    return _build_ranked_list(items)


def _build_ranked_list(items: list[dict]) -> list[dict]:
    """Convert query results into ranked leaderboard entries (1-based rank)."""
    return [
        {
            "rank": idx + 1,
            "playerId": item["playerId"],
            "displayName": item["displayName"],
            "score": item["bestScore"],
        }
        for idx, item in enumerate(items)
    ]


# ---------------------------------------------------------------------------
# Player Ranking
# ---------------------------------------------------------------------------

@app.get("/api/players/{player_id}/rank")
async def player_rank(player_id: str):
    """
    Return a player's global rank and surrounding ±10 neighbours.
    Uses COUNT-based ranking (Rule 9.2) for O(1) rank calculation.
    """
    # Get the player first
    try:
        player = _players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    player_best = player.get("bestScore", 0)
    if player_best == 0:
        raise HTTPException(status_code=404, detail="Player has no scores")

    # Rule 9.2: COUNT-based rank – count players with higher score
    count_query = (
        "SELECT VALUE COUNT(1) FROM c "
        "WHERE c.type = 'player' AND c.bestScore > @score"
    )
    count_result = list(
        _players_container.query_items(
            query=count_query,
            parameters=[{"name": "@score", "value": player_best}],
            enable_cross_partition_query=True,
        )
    )
    # Players with same score but displayName before this player alphabetically
    same_score_query = (
        "SELECT VALUE COUNT(1) FROM c "
        "WHERE c.type = 'player' AND c.bestScore = @score AND c.displayName < @name"
    )
    same_score_result = list(
        _players_container.query_items(
            query=same_score_query,
            parameters=[
                {"name": "@score", "value": player_best},
                {"name": "@name", "value": player["displayName"]},
            ],
            enable_cross_partition_query=True,
        )
    )
    player_rank_value = count_result[0] + same_score_result[0] + 1

    # Get neighbours: fetch enough surrounding players for ±10
    # Fetch top players around this rank
    offset = max(0, player_rank_value - 11)
    fetch_count = 21  # ±10 + self
    neighbour_query = (
        "SELECT c.playerId, c.displayName, c.bestScore "
        "FROM c WHERE c.type = 'player' AND c.bestScore > 0 "
        "ORDER BY c.bestScore DESC, c.displayName ASC "
        "OFFSET @offset LIMIT @limit"
    )
    neighbour_items = list(
        _players_container.query_items(
            query=neighbour_query,
            parameters=[
                {"name": "@offset", "value": offset},
                {"name": "@limit", "value": fetch_count},
            ],
            enable_cross_partition_query=True,
        )
    )

    neighbors = []
    for idx, item in enumerate(neighbour_items):
        entry_rank = offset + idx + 1
        if item["playerId"] == player_id:
            continue
        neighbors.append(
            {
                "rank": entry_rank,
                "playerId": item["playerId"],
                "displayName": item["displayName"],
                "score": item["bestScore"],
            }
        )

    return {
        "playerId": player_id,
        "rank": player_rank_value,
        "score": player_best,
        "neighbors": neighbors,
    }


# ---------------------------------------------------------------------------
# Score History
# ---------------------------------------------------------------------------

@app.get("/api/players/{player_id}/scores")
async def get_player_scores(player_id: str, limit: int = Query(default=10, ge=1, le=100)):
    """Return a player's score history, most recent first."""
    # Verify the player exists
    try:
        _players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    query = (
        "SELECT c.scoreId, c.playerId, c.score, c.gameMode, c.timestamp "
        "FROM c WHERE c.playerId = @pid "
        "ORDER BY c.timestamp DESC "
        "OFFSET 0 LIMIT @limit"
    )
    items = list(
        _scores_container.query_items(
            query=query,
            parameters=[
                {"name": "@pid", "value": player_id},
                {"name": "@limit", "value": limit},
            ],
            partition_key=player_id,  # Rule 3.1: single-partition query
        )
    )
    return items
