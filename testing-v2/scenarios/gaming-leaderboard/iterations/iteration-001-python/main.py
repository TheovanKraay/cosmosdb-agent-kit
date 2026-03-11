"""
Gaming Leaderboard API — FastAPI + Azure Cosmos DB (NoSQL)
==========================================================

Design decisions (following Cosmos DB best practices):

* Three containers:
  - `players`    — partition key /playerId  (high-cardinality, efficient point reads)
  - `scores`     — partition key /playerId  (distributes writes, avoids hot partitions)
  - `leaderboard`— partition key /leaderboardId  (materialized view per leaderboard)
    leaderboardId values: "global" or a region code (e.g. "US", "EU", "JP")

* The leaderboard container is a denormalized, pre-aggregated view (rule 1.2, 9.1).
  It is updated on every score submission so that top-N queries hit a single
  logical partition — no cross-partition fan-out needed at read time.

* Rank is computed with a cheap single-partition COUNT query (rule 9.2).

* Singleton CosmosClient (rule 4.17) via FastAPI lifespan.

* Async SDK throughout (rule 4.1).

* SSL verification disabled for localhost/emulator (rule 4.6).
"""

import os
import uuid
import logging
from contextlib import asynccontextmanager
from typing import List, Optional

from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel

from azure.cosmos.aio import CosmosClient
from azure.cosmos import PartitionKey, exceptions

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

COSMOS_ENDPOINT: str = os.getenv("COSMOS_ENDPOINT", "https://localhost:8081")
COSMOS_KEY: str = os.getenv(
    "COSMOS_KEY",
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==",
)
DATABASE_NAME = "gaming-leaderboard"

# Disable SSL verification when connecting to the local emulator (rule 4.6)
_connection_verify: bool = "localhost" not in COSMOS_ENDPOINT

# ---------------------------------------------------------------------------
# Pydantic models
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


class ScoreResponse(BaseModel):
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
# Application state (singleton containers)
# ---------------------------------------------------------------------------

_cosmos_client: Optional[CosmosClient] = None
_players_container = None
_scores_container = None
_leaderboard_container = None


# ---------------------------------------------------------------------------
# Lifespan: initialise Cosmos DB once on startup, close cleanly on shutdown
# ---------------------------------------------------------------------------


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _cosmos_client, _players_container, _scores_container, _leaderboard_container

    _cosmos_client = CosmosClient(
        COSMOS_ENDPOINT,
        COSMOS_KEY,
        connection_verify=_connection_verify,
    )

    db = await _cosmos_client.create_database_if_not_exists(DATABASE_NAME)

    # players — partition by /playerId for efficient point reads and updates
    _players_container = await db.create_container_if_not_exists(
        id="players",
        partition_key=PartitionKey(path="/playerId"),
        offer_throughput=400,
    )

    # scores — partition by /playerId to distribute writes evenly (rule 2.2)
    _scores_container = await db.create_container_if_not_exists(
        id="scores",
        partition_key=PartitionKey(path="/playerId"),
        offer_throughput=400,
    )

    # leaderboard — materialised view partitioned by leaderboardId (rule 9.1)
    # Each logical partition ("global", "US", "EU", …) holds one entry per player.
    # This enables single-partition top-N queries and count-based ranking (rule 9.2).
    _leaderboard_container = await db.create_container_if_not_exists(
        id="leaderboard",
        partition_key=PartitionKey(path="/leaderboardId"),
        offer_throughput=400,
    )

    logger.info("Cosmos DB initialised (database=%s)", DATABASE_NAME)
    yield

    if _cosmos_client:
        await _cosmos_client.close()


app = FastAPI(lifespan=lifespan)


# ---------------------------------------------------------------------------
# Health
# ---------------------------------------------------------------------------


@app.get("/health")
async def health():
    return {"status": "ok"}


# ---------------------------------------------------------------------------
# Players
# ---------------------------------------------------------------------------


@app.post("/api/players", status_code=201, response_model=PlayerResponse)
async def create_player(req: CreatePlayerRequest):
    doc = {
        "id": req.playerId,
        "playerId": req.playerId,
        "displayName": req.displayName,
        "region": req.region,
        "totalGames": 0,
        "bestScore": 0,
        "averageScore": 0.0,
        # Internal field to track running total for accurate average
        "_totalScore": 0,
    }
    try:
        await _players_container.create_item(body=doc)
    except exceptions.CosmosResourceExistsError:
        raise HTTPException(status_code=409, detail="Player already exists")

    return PlayerResponse(
        playerId=req.playerId,
        displayName=req.displayName,
        region=req.region,
        totalGames=0,
        bestScore=0,
        averageScore=0.0,
    )


@app.get("/api/players/{player_id}", response_model=PlayerResponse)
async def get_player(player_id: str):
    try:
        item = await _players_container.read_item(
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
# Scores
# ---------------------------------------------------------------------------


@app.post("/api/scores", status_code=201, response_model=ScoreResponse)
async def submit_score(req: SubmitScoreRequest):
    # Verify player exists and load current stats
    try:
        player = await _players_container.read_item(
            item=req.playerId, partition_key=req.playerId
        )
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Persist the raw score record
    score_id = str(uuid.uuid4())
    await _scores_container.create_item(
        body={
            "id": score_id,
            "scoreId": score_id,
            "playerId": req.playerId,
            "score": req.score,
            "gameMode": req.gameMode,
        }
    )

    # Update player cumulative stats
    new_total_games = player["totalGames"] + 1
    new_best_score = max(player["bestScore"], req.score)
    new_total_score = player.get("_totalScore", 0) + req.score
    new_average_score = new_total_score / new_total_games

    player["totalGames"] = new_total_games
    player["bestScore"] = new_best_score
    player["_totalScore"] = new_total_score
    player["averageScore"] = new_average_score
    await _players_container.replace_item(item=req.playerId, body=player)

    # -----------------------------------------------------------------------
    # Update materialised leaderboard entries (rule 1.2, 9.1)
    # One entry per player in the "global" partition, one in the region partition.
    # We upsert so score only advances (tracked via new_best_score).
    # -----------------------------------------------------------------------
    entry_base = {
        "id": req.playerId,
        "playerId": req.playerId,
        "displayName": player["displayName"],
        "score": new_best_score,
        "region": player["region"],
    }
    await _leaderboard_container.upsert_item(
        body={**entry_base, "leaderboardId": "global"}
    )
    await _leaderboard_container.upsert_item(
        body={**entry_base, "leaderboardId": player["region"]}
    )

    return ScoreResponse(scoreId=score_id, playerId=req.playerId, score=req.score)


# ---------------------------------------------------------------------------
# Leaderboards
# ---------------------------------------------------------------------------


@app.get("/api/leaderboards/global", response_model=List[LeaderboardEntry])
async def global_leaderboard(top: int = Query(default=100, ge=1, le=100)):
    """
    Single-partition query on the "global" leaderboard (rule 9.1, 3.1).
    No cross-partition fan-out — all entries live in the "global" logical partition.
    """
    query = (
        "SELECT c.playerId, c.displayName, c.score "
        "FROM c "
        "ORDER BY c.score DESC OFFSET 0 LIMIT @top"
    )
    items = []
    async for item in _leaderboard_container.query_items(
        query=query,
        parameters=[{"name": "@top", "value": top}],
        partition_key="global",
    ):
        items.append(item)

    return [
        LeaderboardEntry(
            rank=i + 1,
            playerId=item["playerId"],
            displayName=item["displayName"],
            score=item["score"],
        )
        for i, item in enumerate(items)
    ]


@app.get(
    "/api/leaderboards/regional/{region}", response_model=List[LeaderboardEntry]
)
async def regional_leaderboard(
    region: str, top: int = Query(default=100, ge=1, le=100)
):
    """
    Single-partition query on the region leaderboard (rule 9.1, 3.1).
    """
    query = (
        "SELECT c.playerId, c.displayName, c.score "
        "FROM c "
        "ORDER BY c.score DESC OFFSET 0 LIMIT @top"
    )
    items = []
    async for item in _leaderboard_container.query_items(
        query=query,
        parameters=[{"name": "@top", "value": top}],
        partition_key=region,
    ):
        items.append(item)

    return [
        LeaderboardEntry(
            rank=i + 1,
            playerId=item["playerId"],
            displayName=item["displayName"],
            score=item["score"],
        )
        for i, item in enumerate(items)
    ]


# ---------------------------------------------------------------------------
# Player rank
# ---------------------------------------------------------------------------


@app.get("/api/players/{player_id}/rank", response_model=PlayerRankResponse)
async def get_player_rank(player_id: str):
    # Point-read for the player (rule 3.1 — avoid cross-partition for lookup)
    try:
        player = await _players_container.read_item(
            item=player_id, partition_key=player_id
        )
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    if player["totalGames"] == 0:
        raise HTTPException(status_code=404, detail="Player has no scores")

    player_score: int = player["bestScore"]

    # Count players in the global leaderboard with a higher best score.
    # This is a single-partition COUNT query — efficient (rule 9.2, 3.1).
    count_query = (
        "SELECT VALUE COUNT(1) FROM c WHERE c.score > @score"
    )
    players_above = 0
    async for val in _leaderboard_container.query_items(
        query=count_query,
        parameters=[{"name": "@score", "value": player_score}],
        partition_key="global",
    ):
        players_above = val
        break

    player_rank: int = players_above + 1

    # Fetch surrounding players (up to ±10 positions) in a single query.
    above_count = min(10, player_rank - 1)
    offset = (player_rank - 1) - above_count
    limit = above_count + 1 + 10  # above + self + 10 below

    neighbors_query = (
        "SELECT c.playerId, c.displayName, c.score "
        "FROM c "
        "ORDER BY c.score DESC OFFSET @offset LIMIT @limit"
    )
    neighbor_items = []
    async for item in _leaderboard_container.query_items(
        query=neighbors_query,
        parameters=[
            {"name": "@offset", "value": offset},
            {"name": "@limit", "value": limit},
        ],
        partition_key="global",
    ):
        neighbor_items.append(item)

    neighbors = [
        LeaderboardEntry(
            rank=offset + i + 1,
            playerId=item["playerId"],
            displayName=item["displayName"],
            score=item["score"],
        )
        for i, item in enumerate(neighbor_items)
        if item["playerId"] != player_id  # exclude self
    ]

    return PlayerRankResponse(
        playerId=player_id,
        rank=player_rank,
        score=player_score,
        neighbors=neighbors,
    )
