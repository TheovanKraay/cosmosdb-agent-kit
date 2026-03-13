"""
Gaming Leaderboard API — FastAPI + Azure Cosmos DB (NoSQL)
"""
import os
import uuid
import logging
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from azure.cosmos.aio import CosmosClient
from azure.cosmos import exceptions, PartitionKey

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Configuration — read from environment variables (never hardcoded)
# ---------------------------------------------------------------------------
COSMOS_ENDPOINT: str = os.environ.get("COSMOS_ENDPOINT", "https://localhost:8081")
COSMOS_KEY: str = os.environ.get(
    "COSMOS_KEY",
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==",
)
DATABASE_NAME: str = "gaming-leaderboard"

# ---------------------------------------------------------------------------
# Singleton CosmosClient (rule 4.18 — reuse client across requests)
# ---------------------------------------------------------------------------
_cosmos_client: Optional[CosmosClient] = None
_players_container = None
_scores_container = None
_leaderboard_container = None


def _build_cosmos_client() -> CosmosClient:
    """Create the CosmosClient, disabling SSL verification for the local emulator
    (rule 4.6 — configure SSL for Cosmos DB Emulator)."""
    is_emulator = "localhost" in COSMOS_ENDPOINT or "127.0.0.1" in COSMOS_ENDPOINT
    return CosmosClient(
        COSMOS_ENDPOINT,
        credential=COSMOS_KEY,
        connection_verify=not is_emulator,
    )


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Startup: initialise Cosmos DB containers. Shutdown: close client."""
    global _cosmos_client, _players_container, _scores_container, _leaderboard_container

    _cosmos_client = _build_cosmos_client()

    db = await _cosmos_client.create_database_if_not_exists(DATABASE_NAME)

    # Players container — partition key: /playerId (high cardinality, rule 2.4)
    _players_container = await db.create_container_if_not_exists(
        id="players",
        partition_key=PartitionKey(path="/playerId"),
        offer_throughput=400,
    )

    # Scores container — partition key: /playerId (writes distributed per player,
    # avoids hot partitions, rule 2.2)
    _scores_container = await db.create_container_if_not_exists(
        id="scores",
        partition_key=PartitionKey(path="/playerId"),
        offer_throughput=400,
    )

    # Leaderboard materialized-view container (rule 9.1).
    # Partition key: /leaderboardType — values are "global" or a region code.
    # Composite index on /score DESC enables efficient ORDER BY (rules 5.1, 5.2).
    _leaderboard_container = await db.create_container_if_not_exists(
        id="leaderboard",
        partition_key=PartitionKey(path="/leaderboardType"),
        indexing_policy={
            "indexingMode": "consistent",
            "automatic": True,
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [{"path": "/_etag/?"}],
            "compositeIndexes": [
                [
                    {"path": "/score", "order": "descending"},
                    {"path": "/playerId", "order": "ascending"},
                ]
            ],
        },
        offer_throughput=400,
    )

    yield

    await _cosmos_client.close()


app = FastAPI(lifespan=lifespan)


# ---------------------------------------------------------------------------
# Request models
# ---------------------------------------------------------------------------

class CreatePlayerRequest(BaseModel):
    playerId: str
    displayName: str
    region: str


class SubmitScoreRequest(BaseModel):
    playerId: str
    score: int
    gameMode: Optional[str] = None


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

@app.get("/health")
async def health():
    """Readiness probe — returns 200 when the app is up."""
    return {"status": "ok"}


@app.post("/api/players", status_code=201)
async def create_player(request: CreatePlayerRequest):
    """Create a new player profile with zeroed stats."""
    player = {
        "id": request.playerId,
        "playerId": request.playerId,
        "displayName": request.displayName,
        "region": request.region,
        "totalGames": 0,
        "bestScore": 0,
        "averageScore": 0.0,
        "type": "player",
    }
    try:
        await _players_container.create_item(body=player)
    except exceptions.CosmosResourceExistsError:
        raise HTTPException(status_code=409, detail="Player already exists")

    return JSONResponse(
        status_code=201,
        content={
            "playerId": player["playerId"],
            "displayName": player["displayName"],
            "region": player["region"],
            "totalGames": player["totalGames"],
            "bestScore": player["bestScore"],
            "averageScore": player["averageScore"],
        },
    )


@app.get("/api/players/{playerId}")
async def get_player(playerId: str):
    """Return a player's profile and cumulative stats."""
    try:
        item = await _players_container.read_item(
            item=playerId, partition_key=playerId
        )
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    return {
        "playerId": item["playerId"],
        "displayName": item["displayName"],
        "region": item["region"],
        "totalGames": item["totalGames"],
        "bestScore": item["bestScore"],
        "averageScore": item["averageScore"],
    }


@app.post("/api/scores", status_code=201)
async def submit_score(request: SubmitScoreRequest):
    """Submit a game score, update player stats and leaderboard entries."""
    # 1. Load the player (point read — single-partition, rule 3.1)
    try:
        player = await _players_container.read_item(
            item=request.playerId, partition_key=request.playerId
        )
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # 2. Persist the raw score document
    score_id = str(uuid.uuid4())
    score_doc = {
        "id": score_id,
        "playerId": request.playerId,
        "score": request.score,
        "gameMode": request.gameMode,
        "type": "score",
    }
    await _scores_container.create_item(body=score_doc)

    # 3. Update player cumulative stats
    old_total: int = player["totalGames"]
    old_best: int = player["bestScore"]
    old_avg: float = float(player.get("averageScore", 0.0))

    new_total = old_total + 1
    new_best = max(old_best, request.score)
    new_avg = ((old_avg * old_total) + request.score) / new_total

    player["totalGames"] = new_total
    player["bestScore"] = new_best
    player["averageScore"] = new_avg
    await _players_container.upsert_item(body=player)

    # 4. Keep materialized leaderboard entries up to date (rule 9.1).
    #    Each player has exactly one entry per leaderboard, keyed by playerId.
    region: str = player["region"]

    global_entry = {
        "id": f"{request.playerId}_global",
        "leaderboardType": "global",
        "playerId": request.playerId,
        "displayName": player["displayName"],
        "score": new_best,
        "region": region,
    }
    await _leaderboard_container.upsert_item(body=global_entry)

    regional_entry = {
        "id": f"{request.playerId}_{region}",
        "leaderboardType": region,
        "playerId": request.playerId,
        "displayName": player["displayName"],
        "score": new_best,
        "region": region,
    }
    await _leaderboard_container.upsert_item(body=regional_entry)

    return JSONResponse(
        status_code=201,
        content={
            "scoreId": score_id,
            "playerId": request.playerId,
            "score": request.score,
        },
    )


@app.get("/api/leaderboards/global")
async def global_leaderboard(top: int = 100):
    """Return the global top-N players sorted by best score descending."""
    # Clamp to 1–100 and coerce to int to ensure safe SQL interpolation (rule 3.6)
    top = int(min(max(top, 1), 100))

    # Use a literal integer for OFFSET/LIMIT (rule 3.6 — never use parameters
    # for TOP/OFFSET/LIMIT in Cosmos DB SQL).
    # The partition key filter is applied via the SDK, not in SQL.
    query = (
        f"SELECT c.playerId, c.displayName, c.score "
        f"FROM c ORDER BY c.score DESC OFFSET 0 LIMIT {top}"
    )

    items = []
    async for item in _leaderboard_container.query_items(
        query=query,
        partition_key="global",
    ):
        items.append(item)

    return [
        {
            "rank": i + 1,
            "playerId": item["playerId"],
            "displayName": item["displayName"],
            "score": item["score"],
        }
        for i, item in enumerate(items)
    ]


@app.get("/api/leaderboards/regional/{region}")
async def regional_leaderboard(region: str, top: int = 100):
    """Return the top-N players for a specific region sorted by best score descending."""
    # Clamp to 1–100 and coerce to int to ensure safe SQL interpolation (rule 3.6)
    top = int(min(max(top, 1), 100))

    # Partition key = region code; literal integer for LIMIT (rule 3.6)
    query = (
        f"SELECT c.playerId, c.displayName, c.score "
        f"FROM c ORDER BY c.score DESC OFFSET 0 LIMIT {top}"
    )

    items = []
    async for item in _leaderboard_container.query_items(
        query=query,
        partition_key=region,
    ):
        items.append(item)

    return [
        {
            "rank": i + 1,
            "playerId": item["playerId"],
            "displayName": item["displayName"],
            "score": item["score"],
        }
        for i, item in enumerate(items)
    ]


@app.get("/api/players/{playerId}/rank")
async def player_rank(playerId: str):
    """Return a player's global rank and the ±10 nearest neighbours."""
    # Point read for the player (rule 3.1)
    try:
        player = await _players_container.read_item(
            item=playerId, partition_key=playerId
        )
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    if player["totalGames"] == 0:
        raise HTTPException(status_code=404, detail="Player has no scores")

    best_score: int = player["bestScore"]

    # Count-based rank (rule 9.2 — count-based approach instead of full scan).
    # Parameterised WHERE clause (rule 3.5); literal integers handled elsewhere.
    count_query = "SELECT VALUE COUNT(1) FROM c WHERE c.score > @score"
    above_count = 0
    async for value in _leaderboard_container.query_items(
        query=count_query,
        parameters=[{"name": "@score", "value": best_score}],
        partition_key="global",
    ):
        above_count = value

    rank = above_count + 1

    # Coerce to int to ensure safe SQL interpolation (rule 3.6)
    offset = int(max(0, rank - 11))
    neighbours_query = (
        f"SELECT c.playerId, c.displayName, c.score "
        f"FROM c ORDER BY c.score DESC OFFSET {offset} LIMIT 21"
    )

    neighbors = []
    current_rank = offset + 1
    async for item in _leaderboard_container.query_items(
        query=neighbours_query,
        partition_key="global",
    ):
        if item["playerId"] != playerId:
            neighbors.append(
                {
                    "rank": current_rank,
                    "playerId": item["playerId"],
                    "displayName": item["displayName"],
                    "score": item["score"],
                }
            )
        current_rank += 1

    return {
        "playerId": playerId,
        "rank": rank,
        "score": best_score,
        "neighbors": neighbors,
    }
