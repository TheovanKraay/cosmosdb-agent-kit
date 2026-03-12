"""
Gaming Leaderboard API — FastAPI + Azure Cosmos DB (async SDK)

Implements the gaming-leaderboard API contract:
  GET  /health
  POST /api/players
  GET  /api/players/{playerId}
  POST /api/scores
  GET  /api/leaderboards/global
  GET  /api/leaderboards/regional/{region}
  GET  /api/players/{playerId}/rank

Best practices applied:
- Async Cosmos DB SDK (azure.cosmos.aio) for better throughput
- High-cardinality partition keys to avoid hot partitions
- Materialized leaderboard view container for efficient top-N queries
- Composite index on leaderboard container for ORDER BY score DESC
- Denormalized player data in leaderboard entries (avoids cross-partition joins)
- connection_verify=False for emulator SSL (rule 4.6)
- Singleton CosmosClient reused across requests (rule 4.18)
- aiohttp included in requirements for async SDK (rule 4.15)
"""

import os
import uuid
import urllib3
import logging
from contextlib import asynccontextmanager
from typing import Optional, List

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from azure.cosmos.aio import CosmosClient
from azure.cosmos import PartitionKey, exceptions

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

COSMOS_ENDPOINT = os.environ.get("COSMOS_ENDPOINT", "https://localhost:8081")
COSMOS_KEY = os.environ.get(
    "COSMOS_KEY",
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==",
)
DATABASE_NAME = "gaming-leaderboard"
MAX_LEADERBOARD_SIZE = 100  # Maximum number of entries returned by leaderboard queries

logger = logging.getLogger(__name__)

# Suppress SSL warnings when connecting to the emulator (dev only)
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# ---------------------------------------------------------------------------
# Global Cosmos DB handles (singleton pattern — rule 4.18)
# ---------------------------------------------------------------------------

cosmos_client: Optional[CosmosClient] = None
players_container = None
scores_container = None
leaderboard_container = None


# ---------------------------------------------------------------------------
# Application lifespan — initialise / tear-down Cosmos DB resources
# ---------------------------------------------------------------------------

@asynccontextmanager
async def lifespan(app: FastAPI):
    global cosmos_client, players_container, scores_container, leaderboard_container

    # connection_verify=False disables SSL cert check for the emulator (rule 4.6)
    cosmos_client = CosmosClient(
        COSMOS_ENDPOINT,
        credential=COSMOS_KEY,
        connection_verify=False,
    )

    db = await cosmos_client.create_database_if_not_exists(id=DATABASE_NAME)

    # Players container — partitioned by playerId (high cardinality, point reads)
    players_container = await db.create_container_if_not_exists(
        id="players",
        partition_key=PartitionKey(path="/playerId"),
    )

    # Scores container — partitioned by playerId (avoids hot partitions per rule 2.2)
    scores_container = await db.create_container_if_not_exists(
        id="scores",
        partition_key=PartitionKey(path="/playerId"),
    )

    # Leaderboard container — materialized view for efficient top-N queries (rule 9.1)
    # Partition key is leaderboardId ("global" or region code like "US").
    # Composite index enables ORDER BY score DESC within a single partition (rules 5.1, 5.2).
    leaderboard_indexing_policy = {
        "indexingMode": "consistent",
        "automatic": True,
        "includedPaths": [{"path": "/*"}],
        "excludedPaths": [],
        "compositeIndexes": [
            [
                {"path": "/score", "order": "descending"},
                {"path": "/playerId", "order": "ascending"},
            ]
        ],
    }

    leaderboard_container = await db.create_container_if_not_exists(
        id="leaderboard",
        partition_key=PartitionKey(path="/leaderboardId"),
        indexing_policy=leaderboard_indexing_policy,
    )

    yield

    await cosmos_client.close()


app = FastAPI(title="Gaming Leaderboard API", lifespan=lifespan)


# ---------------------------------------------------------------------------
# Request / Response models
# ---------------------------------------------------------------------------

class CreatePlayerRequest(BaseModel):
    playerId: str
    displayName: str
    region: str


class PlayerResponse(BaseModel):
    playerId: str
    displayName: str
    region: str
    totalGames: int
    bestScore: int
    averageScore: float


class SubmitScoreRequest(BaseModel):
    playerId: str
    score: int
    gameMode: Optional[str] = None


class SubmitScoreResponse(BaseModel):
    scoreId: str
    playerId: str
    score: int


class LeaderboardEntry(BaseModel):
    rank: int
    playerId: str
    displayName: str
    score: int


class PlayerRankResponse(BaseModel):
    playerId: str
    rank: int
    score: int
    neighbors: List[LeaderboardEntry]


# ---------------------------------------------------------------------------
# Helper — upsert a leaderboard entry for one scope ("global" or region)
# ---------------------------------------------------------------------------

async def _upsert_leaderboard_entry(
    leaderboard_id: str, player_id: str, display_name: str, score: int
) -> None:
    """
    Upsert a single leaderboard entry (materialized view update).
    The document id is deterministic so that upsert replaces the old entry.
    """
    entry = {
        "id": f"{player_id}-{leaderboard_id}",
        "leaderboardId": leaderboard_id,
        "playerId": player_id,
        "displayName": display_name,
        "score": score,
    }
    await leaderboard_container.upsert_item(entry)


# ---------------------------------------------------------------------------
# Health check
# ---------------------------------------------------------------------------

@app.get("/health")
async def health():
    return {"status": "ok"}


# ---------------------------------------------------------------------------
# Player management
# ---------------------------------------------------------------------------

@app.post("/api/players", status_code=201, response_model=PlayerResponse)
async def create_player(req: CreatePlayerRequest):
    player = {
        "id": req.playerId,
        "playerId": req.playerId,
        "displayName": req.displayName,
        "region": req.region,
        "totalGames": 0,
        "bestScore": 0,
        "averageScore": 0.0,
        "totalScore": 0,
        "type": "player",
    }
    try:
        created = await players_container.create_item(player)
    except exceptions.CosmosResourceExistsError:
        raise HTTPException(status_code=409, detail="Player already exists")

    return PlayerResponse(
        playerId=created["playerId"],
        displayName=created["displayName"],
        region=created["region"],
        totalGames=created["totalGames"],
        bestScore=created["bestScore"],
        averageScore=created["averageScore"],
    )


@app.get("/api/players/{player_id}", response_model=PlayerResponse)
async def get_player(player_id: str):
    try:
        item = await players_container.read_item(
            item=player_id, partition_key=player_id
        )
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    return PlayerResponse(
        playerId=item["playerId"],
        displayName=item["displayName"],
        region=item["region"],
        totalGames=item["totalGames"],
        bestScore=item["bestScore"],
        averageScore=item["averageScore"],
    )


# ---------------------------------------------------------------------------
# Score submission
# ---------------------------------------------------------------------------

@app.post("/api/scores", status_code=201, response_model=SubmitScoreResponse)
async def submit_score(req: SubmitScoreRequest):
    # Verify the player exists
    try:
        player = await players_container.read_item(
            item=req.playerId, partition_key=req.playerId
        )
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    score_id = str(uuid.uuid4())

    # Record the individual score (partitioned by playerId — rule 2.2)
    score_doc = {
        "id": score_id,
        "scoreId": score_id,
        "playerId": req.playerId,
        "score": req.score,
    }
    if req.gameMode is not None:
        score_doc["gameMode"] = req.gameMode

    await scores_container.create_item(score_doc)

    # Update player stats
    prev_best = player["bestScore"]
    prev_total_games = player["totalGames"]
    # Prefer stored totalScore; fall back to reconstructing from averageScore
    prev_total_score = player.get(
        "totalScore",
        round(player.get("averageScore", 0.0) * prev_total_games),
    )

    new_total_games = prev_total_games + 1
    new_total_score = prev_total_score + req.score
    new_best = max(prev_best, req.score)
    new_average = new_total_score / new_total_games

    player["totalGames"] = new_total_games
    player["totalScore"] = new_total_score
    player["bestScore"] = new_best
    player["averageScore"] = new_average

    await players_container.replace_item(item=req.playerId, body=player)

    # Update materialized leaderboard views if best score improved or first score ever
    if new_best > prev_best or prev_total_games == 0:
        await _upsert_leaderboard_entry(
            "global", req.playerId, player["displayName"], new_best
        )
        await _upsert_leaderboard_entry(
            player["region"], req.playerId, player["displayName"], new_best
        )

    return SubmitScoreResponse(
        scoreId=score_id,
        playerId=req.playerId,
        score=req.score,
    )


# ---------------------------------------------------------------------------
# Leaderboards
# ---------------------------------------------------------------------------

async def _query_leaderboard(leaderboard_id: str, top: int) -> List[LeaderboardEntry]:
    """
    Query the materialized leaderboard view for a given scope.
    Uses a single-partition query (no cross-partition fan-out) — rule 3.1.
    Uses composite index for ORDER BY score DESC — rule 5.2.
    Uses literal integer for TOP — rule 3.6.
    """
    # Build query with literal TOP value (rule 3.6 — no parameter for TOP)
    safe_top = max(1, min(top, MAX_LEADERBOARD_SIZE))
    query = (
        f"SELECT TOP {safe_top} c.playerId, c.displayName, c.score "
        f"FROM c WHERE c.leaderboardId = @leaderboardId "
        f"ORDER BY c.score DESC"
    )
    params = [{"name": "@leaderboardId", "value": leaderboard_id}]

    items = [
        item
        async for item in leaderboard_container.query_items(
            query=query,
            parameters=params,
            partition_key=leaderboard_id,
        )
    ]

    return [
        LeaderboardEntry(
            rank=i + 1,
            playerId=item["playerId"],
            displayName=item["displayName"],
            score=item["score"],
        )
        for i, item in enumerate(items)
    ]


@app.get("/api/leaderboards/global", response_model=List[LeaderboardEntry])
async def global_leaderboard(top: int = 100):
    return await _query_leaderboard("global", top)


@app.get(
    "/api/leaderboards/regional/{region}", response_model=List[LeaderboardEntry]
)
async def regional_leaderboard(region: str, top: int = 100):
    return await _query_leaderboard(region, top)


# ---------------------------------------------------------------------------
# Player rank
# ---------------------------------------------------------------------------

@app.get("/api/players/{player_id}/rank", response_model=PlayerRankResponse)
async def get_player_rank(player_id: str):
    # Fetch the player to get their best score
    try:
        player = await players_container.read_item(
            item=player_id, partition_key=player_id
        )
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    if player["totalGames"] == 0:
        raise HTTPException(status_code=404, detail="Player has no scores")

    best_score = player["bestScore"]

    # Count players with a higher score than the requested player (rule 9.2).
    # This is a single-partition aggregate query — no cross-partition fan-out.
    count_query = (
        "SELECT VALUE COUNT(1) FROM c "
        "WHERE c.leaderboardId = 'global' AND c.score > @score"
    )
    count_params = [{"name": "@score", "value": best_score}]
    count_results = [
        item
        async for item in leaderboard_container.query_items(
            query=count_query,
            parameters=count_params,
            partition_key="global",
        )
    ]
    rank = (count_results[0] if count_results else 0) + 1

    # Retrieve ±10 neighbours around the player's rank.
    # Offset is a literal integer embedded in the SQL (rule 3.6).
    offset = max(0, rank - 11)
    limit = 21
    neighbor_query = (
        f"SELECT c.playerId, c.displayName, c.score "
        f"FROM c WHERE c.leaderboardId = 'global' "
        f"ORDER BY c.score DESC "
        f"OFFSET {offset} LIMIT {limit}"
    )
    neighbor_items = [
        item
        async for item in leaderboard_container.query_items(
            query=neighbor_query,
            partition_key="global",
        )
    ]

    neighbors: List[LeaderboardEntry] = []
    for i, item in enumerate(neighbor_items):
        neighbor_rank = offset + i + 1
        if item["playerId"] != player_id:
            neighbors.append(
                LeaderboardEntry(
                    rank=neighbor_rank,
                    playerId=item["playerId"],
                    displayName=item["displayName"],
                    score=item["score"],
                )
            )

    return PlayerRankResponse(
        playerId=player_id,
        rank=rank,
        score=best_score,
        neighbors=neighbors,
    )


# ---------------------------------------------------------------------------
# Entry point (for running directly with `python main.py`)
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    import uvicorn

    port = int(os.environ.get("PORT", "8000"))
    uvicorn.run("main:app", host="0.0.0.0", port=port, reload=False)
