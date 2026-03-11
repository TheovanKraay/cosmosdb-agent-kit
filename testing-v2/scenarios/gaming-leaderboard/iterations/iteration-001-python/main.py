"""
Gaming Leaderboard API — FastAPI + Azure Cosmos DB (NoSQL, async SDK)

Implements the api-contract.yaml for the gaming-leaderboard scenario.
Best practices applied:
  - Rule 1.2  Denormalize for read-heavy workloads (bestScore/displayName embedded)
  - Rule 2.4  High-cardinality partition key (/playerId for players, /playerId for scores)
  - Rule 3.1  Minimise cross-partition queries (point reads wherever possible)
  - Rule 3.5  Parameterised queries
  - Rule 4.1  Async SDK for better throughput
  - Rule 4.6  Disable SSL verification when connecting to the emulator
  - Rule 4.15 Include aiohttp (declared in requirements.txt)
  - Rule 4.16 SDK-level 429 retry handled automatically; logged here
  - Rule 4.18 Singleton CosmosClient (created once via FastAPI lifespan)
  - Rule 5.1  Composite indexes for cross-partition ORDER BY queries
  - Rule 9.2  Count-based rank calculation (no expensive full scan)
  - No OFFSET/LIMIT for ranking — uses score-based neighbour queries instead
"""

import logging
import os
import uuid
from contextlib import asynccontextmanager
from typing import Optional

import urllib3
from azure.cosmos import PartitionKey, exceptions
from azure.cosmos.aio import CosmosClient
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Configuration — read from environment variables (never hardcode!)
# ---------------------------------------------------------------------------

COSMOS_ENDPOINT: str = os.environ.get("COSMOS_ENDPOINT", "https://localhost:8081")
COSMOS_KEY: str = os.environ.get(
    "COSMOS_KEY",
    # Well-known emulator key (safe as a fallback — not a production secret)
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==",
)
DATABASE_NAME = "gaming-leaderboard"
PLAYERS_CONTAINER_ID = "players"
SCORES_CONTAINER_ID = "scores"

# ---------------------------------------------------------------------------
# Module-level singletons (populated during lifespan startup)
# ---------------------------------------------------------------------------

_cosmos_client: Optional[CosmosClient] = None
_players_container = None
_scores_container = None

# ---------------------------------------------------------------------------
# FastAPI lifespan — create / destroy the singleton CosmosClient (Rule 4.18)
# ---------------------------------------------------------------------------


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _cosmos_client, _players_container, _scores_container

    # Rule 4.6 — disable SSL verification when talking to the local emulator
    is_emulator = "localhost" in COSMOS_ENDPOINT or "127.0.0.1" in COSMOS_ENDPOINT
    if is_emulator:
        urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
        logger.warning(
            "Connecting to Cosmos DB Emulator — SSL verification is disabled."
        )

    _cosmos_client = CosmosClient(
        url=COSMOS_ENDPOINT,
        credential=COSMOS_KEY,
        connection_verify=not is_emulator,  # Rule 4.6
    )

    database = await _cosmos_client.create_database_if_not_exists(id=DATABASE_NAME)

    # Players container
    # Composite indexes enable efficient cross-partition ORDER BY (Rule 5.1):
    #   • Global leaderboard:   ORDER BY c.bestScore DESC
    #   • Regional leaderboard: WHERE c.region = @r ORDER BY c.bestScore DESC
    _players_container = await database.create_container_if_not_exists(
        id=PLAYERS_CONTAINER_ID,
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy={
            "indexingMode": "consistent",
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [{"path": '/"_etag"/?'}],
            "compositeIndexes": [
                # For global leaderboard ORDER BY bestScore DESC
                [
                    {"path": "/bestScore", "order": "descending"},
                    {"path": "/playerId", "order": "ascending"},
                ],
                # For regional leaderboard WHERE region = ? ORDER BY bestScore DESC
                [
                    {"path": "/region", "order": "ascending"},
                    {"path": "/bestScore", "order": "descending"},
                    {"path": "/playerId", "order": "ascending"},
                ],
            ],
        },
    )

    # Scores container — each score record stored under its player's partition
    _scores_container = await database.create_container_if_not_exists(
        id=SCORES_CONTAINER_ID,
        partition_key=PartitionKey(path="/playerId"),
    )

    logger.info("Cosmos DB containers ready.")
    yield

    await _cosmos_client.close()
    logger.info("Cosmos DB client closed.")


# ---------------------------------------------------------------------------
# FastAPI app
# ---------------------------------------------------------------------------

app = FastAPI(title="Gaming Leaderboard API", lifespan=lifespan)

# ---------------------------------------------------------------------------
# Request / Response models
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
# Helper — build the canonical player response dict
# ---------------------------------------------------------------------------


def _player_response(doc: dict) -> dict:
    return {
        "playerId": doc["playerId"],
        "displayName": doc["displayName"],
        "region": doc["region"],
        "totalGames": doc["totalGames"],
        "bestScore": doc["bestScore"],
        "averageScore": doc["averageScore"],
    }


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------


@app.get("/health")
async def health():
    """Health check — returns 200 when the app is ready."""
    return {"status": "ok"}


@app.post("/api/players", status_code=201)
async def create_player(request: CreatePlayerRequest):
    """Create a new player profile with zeroed stats."""
    player_doc = {
        "id": request.playerId,
        "playerId": request.playerId,
        "displayName": request.displayName,
        "region": request.region,
        "totalGames": 0,
        "bestScore": 0,
        "averageScore": 0.0,
    }
    try:
        await _players_container.create_item(body=player_doc)
    except exceptions.CosmosResourceExistsError:
        raise HTTPException(status_code=409, detail="Player already exists.")

    return _player_response(player_doc)


@app.get("/api/players/{playerId}")
async def get_player(playerId: str):
    """Get a player's profile and cumulative stats."""
    try:
        # Single-partition point read — O(1) RU cost (Rule 3.1)
        doc = await _players_container.read_item(
            item=playerId, partition_key=playerId
        )
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found.")

    return _player_response(doc)


@app.post("/api/scores", status_code=201)
async def submit_score(request: SubmitScoreRequest):
    """Submit a game score and update the player's cumulative stats."""
    # Verify player exists (point read — cheap)
    try:
        player = await _players_container.read_item(
            item=request.playerId, partition_key=request.playerId
        )
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found.")

    # Persist the individual score record
    score_id = str(uuid.uuid4())
    score_doc = {
        "id": score_id,
        "scoreId": score_id,
        "playerId": request.playerId,
        "score": request.score,
        "gameMode": request.gameMode,
    }
    await _scores_container.create_item(body=score_doc)

    # Update player stats in-place (denormalized, Rule 1.2)
    total_games = player["totalGames"] + 1
    new_best = max(player["bestScore"], request.score)
    new_avg = (
        player["averageScore"] * player["totalGames"] + request.score
    ) / total_games

    player["totalGames"] = total_games
    player["bestScore"] = new_best
    player["averageScore"] = new_avg

    # replace_item is a targeted single-partition write
    await _players_container.replace_item(item=request.playerId, body=player)

    return {"scoreId": score_id, "playerId": request.playerId, "score": request.score}


@app.get("/api/leaderboards/global")
async def global_leaderboard(top: int = 100):
    """Return the global top-N leaderboard sorted by bestScore descending."""
    top = min(max(top, 1), 100)

    # Rule 3.5 — fully parameterised query (TOP supports @param in Cosmos SQL)
    query = (
        "SELECT TOP @top c.playerId, c.displayName, c.bestScore AS score "
        "FROM c ORDER BY c.bestScore DESC"
    )
    params = [{"name": "@top", "value": top}]

    entries = []
    async for item in _players_container.query_items(
        query=query,
        parameters=params,
        enable_cross_partition_query=True,
    ):
        entries.append(item)

    return [
        {
            "rank": i + 1,
            "playerId": e["playerId"],
            "displayName": e["displayName"],
            "score": e["score"],
        }
        for i, e in enumerate(entries)
    ]


@app.get("/api/leaderboards/regional/{region}")
async def regional_leaderboard(region: str, top: int = 100):
    """Return the top-N leaderboard for a specific region."""
    top = min(max(top, 1), 100)

    # Rule 3.5 — fully parameterised query for both TOP and region
    query = (
        "SELECT TOP @top c.playerId, c.displayName, c.bestScore AS score "
        "FROM c WHERE c.region = @region ORDER BY c.bestScore DESC"
    )
    params = [
        {"name": "@top", "value": top},
        {"name": "@region", "value": region},
    ]

    entries = []
    async for item in _players_container.query_items(
        query=query,
        parameters=params,
        enable_cross_partition_query=True,
    ):
        entries.append(item)

    return [
        {
            "rank": i + 1,
            "playerId": e["playerId"],
            "displayName": e["displayName"],
            "score": e["score"],
        }
        for i, e in enumerate(entries)
    ]


@app.get("/api/players/{playerId}/rank")
async def player_rank(playerId: str):
    """
    Return a player's global rank and the ±10 surrounding players.

    Rank calculation uses a COUNT aggregate (Rule 9.2 — count-based approach).
    Neighbour queries use score-based bounds instead of OFFSET/LIMIT to avoid
    the costly OFFSET anti-pattern (Rule 3.4).
    """
    try:
        player = await _players_container.read_item(
            item=playerId, partition_key=playerId
        )
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found.")

    if player["totalGames"] == 0:
        raise HTTPException(status_code=404, detail="Player has no scores yet.")

    best_score = player["bestScore"]

    # --- Rank: count players with a strictly higher bestScore (Rule 9.2) ---
    count_query = (
        "SELECT VALUE COUNT(1) FROM c WHERE c.bestScore > @score"
    )
    count_params = [{"name": "@score", "value": best_score}]

    rank = 1
    async for count in _players_container.query_items(
        query=count_query,
        parameters=count_params,
        enable_cross_partition_query=True,
    ):
        rank = count + 1
        break

    # --- Neighbours above (better rank): lowest scores that are still > mine ---
    # Sorted ASC so the closest above me comes first; we reverse for rank order.
    above_query = (
        "SELECT TOP 10 c.playerId, c.displayName, c.bestScore AS score "
        "FROM c WHERE c.bestScore > @score AND c.playerId != @pid "
        "ORDER BY c.bestScore ASC"
    )
    above_params = [
        {"name": "@score", "value": best_score},
        {"name": "@pid", "value": playerId},
    ]
    above = []
    async for item in _players_container.query_items(
        query=above_query,
        parameters=above_params,
        enable_cross_partition_query=True,
    ):
        above.append(item)
    above.reverse()  # highest score first → lowest rank number first

    # --- Neighbours below (worse rank): highest scores that are still < mine ---
    below_query = (
        "SELECT TOP 10 c.playerId, c.displayName, c.bestScore AS score "
        "FROM c WHERE c.bestScore < @score AND c.playerId != @pid "
        "ORDER BY c.bestScore DESC"
    )
    below_params = [
        {"name": "@score", "value": best_score},
        {"name": "@pid", "value": playerId},
    ]
    below = []
    async for item in _players_container.query_items(
        query=below_query,
        parameters=below_params,
        enable_cross_partition_query=True,
    ):
        below.append(item)

    # Assign ranks relative to the current player's rank
    neighbors = []
    for i, n in enumerate(above):
        neighbors.append(
            {
                "rank": rank - len(above) + i,
                "playerId": n["playerId"],
                "displayName": n["displayName"],
                "score": n["score"],
            }
        )
    for i, n in enumerate(below):
        neighbors.append(
            {
                "rank": rank + 1 + i,
                "playerId": n["playerId"],
                "displayName": n["displayName"],
                "score": n["score"],
            }
        )

    return {
        "playerId": playerId,
        "rank": rank,
        "score": best_score,
        "neighbors": neighbors,
    }
