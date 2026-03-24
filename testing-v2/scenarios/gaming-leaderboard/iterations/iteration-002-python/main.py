"""
Gaming Leaderboard API – FastAPI + Azure Cosmos DB (NoSQL)

Cosmos DB best practices applied:
- Singleton CosmosClient (rule 4.18)
- Async SDK with aiohttp (rules 4.1, 4.15)
- Gateway mode + SSL disabled for emulator (rule 4.6)
- Parameterized queries (rule 3.5)
- Partition key aligned with query patterns (rule 2.6)
- High-cardinality partition keys (rule 2.4)
- Composite indexes for ORDER BY (rule 5.2)
- COUNT-based ranking (rule 9.2)
- Type discriminators for polymorphic data (rule 1.11)
- Denormalized player stats for read-heavy workload (rule 1.2)
"""

import os
import uuid
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from typing import Optional

import urllib3
from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import JSONResponse, Response
from azure.cosmos.aio import CosmosClient
from azure.cosmos import PartitionKey
from azure.cosmos.exceptions import CosmosResourceNotFoundError, CosmosHttpResponseError
from azure.core import MatchConditions

# Suppress SSL warnings for emulator (local development only)
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# ---------------------------------------------------------------------------
# Startup / Shutdown (lifespan context manager)
# ---------------------------------------------------------------------------

@asynccontextmanager
async def lifespan(application: FastAPI):
    """Initialise database and containers on startup, close client on shutdown."""
    await get_players_container()
    await get_scores_container()
    yield
    global _cosmos_client
    if _cosmos_client is not None:
        await _cosmos_client.close()
        _cosmos_client = None


app = FastAPI(title="Gaming Leaderboard API", lifespan=lifespan)

# ---------------------------------------------------------------------------
# Configuration from environment variables (no hardcoded connection strings)
# ---------------------------------------------------------------------------
COSMOS_ENDPOINT = os.environ.get("COSMOS_ENDPOINT", "https://localhost:8081")
COSMOS_KEY = os.environ.get(
    "COSMOS_KEY",
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==",
)
DATABASE_NAME = os.environ.get("DATABASE_NAME", "gaming-leaderboard")

# Container names
PLAYERS_CONTAINER = "players"
SCORES_CONTAINER = "scores"

# ---------------------------------------------------------------------------
# Singleton CosmosClient (rule 4.18 – reuse across requests)
# ---------------------------------------------------------------------------
_cosmos_client: Optional[CosmosClient] = None
_database = None
_players_container = None
_scores_container = None


async def get_cosmos_client() -> CosmosClient:
    """Return the singleton CosmosClient, creating it on first call."""
    global _cosmos_client
    if _cosmos_client is None:
        _cosmos_client = CosmosClient(
            url=COSMOS_ENDPOINT,
            credential=COSMOS_KEY,
            connection_verify=False,  # Emulator SSL (rule 4.6)
        )
    return _cosmos_client


async def get_database():
    """Return the database proxy, creating the DB if needed."""
    global _database
    if _database is None:
        client = await get_cosmos_client()
        _database = await client.create_database_if_not_exists(id=DATABASE_NAME)
    return _database


async def get_players_container():
    """
    Return the players container proxy.

    Partition key: /playerId  (high cardinality – rule 2.4)
    Players and their stats live here.
    """
    global _players_container
    if _players_container is None:
        db = await get_database()
        _players_container = await db.create_container_if_not_exists(
            id=PLAYERS_CONTAINER,
            partition_key=PartitionKey(path="/playerId"),
            indexing_policy={
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
    return _players_container


async def get_scores_container():
    """
    Return the scores container proxy.

    Partition key: /playerId  (aligns with query pattern – rule 2.6)
    Each score document references a player.
    """
    global _scores_container
    if _scores_container is None:
        db = await get_database()
        _scores_container = await db.create_container_if_not_exists(
            id=SCORES_CONTAINER,
            partition_key=PartitionKey(path="/playerId"),
            indexing_policy={
                "includedPaths": [{"path": "/*"}],
                "excludedPaths": [{"path": '/"_etag"/?'}],
                "compositeIndexes": [
                    [
                        {"path": "/playerId", "order": "ascending"},
                        {"path": "/timestamp", "order": "descending"},
                    ]
                ],
            },
        )
    return _scores_container


# ---------------------------------------------------------------------------
# Health endpoint
# ---------------------------------------------------------------------------

@app.get("/health")
async def health():
    return {"status": "ok"}


# ---------------------------------------------------------------------------
# Player Management
# ---------------------------------------------------------------------------

@app.post("/api/players", status_code=201)
async def create_player(body: dict):
    """Create a new player profile."""
    player_id = body.get("playerId")
    display_name = body.get("displayName")
    region = body.get("region")

    if not player_id or not display_name or not region:
        raise HTTPException(status_code=400, detail="playerId, displayName, and region are required")

    container = await get_players_container()

    player = {
        "id": player_id,
        "playerId": player_id,
        "displayName": display_name,
        "region": region,
        "totalGames": 0,
        "bestScore": 0,
        "averageScore": 0.0,
        "type": "player",
    }

    try:
        await container.create_item(body=player)
    except CosmosHttpResponseError as e:
        if e.status_code == 409:
            raise HTTPException(status_code=409, detail="Player already exists")
        raise

    return _player_response(player)


@app.get("/api/players/{player_id}")
async def get_player(player_id: str):
    """Get player profile with stats."""
    container = await get_players_container()
    try:
        player = await container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    return _player_response(player)


@app.patch("/api/players/{player_id}")
async def update_player(player_id: str, body: dict):
    """Update a player's profile fields (displayName and/or region)."""
    container = await get_players_container()
    try:
        player = await container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    if "displayName" in body:
        player["displayName"] = body["displayName"]
    if "region" in body:
        player["region"] = body["region"]

    await container.replace_item(item=player_id, body=player)

    return _player_response(player)


@app.delete("/api/players/{player_id}", status_code=204)
async def delete_player(player_id: str):
    """Delete a player and all their associated score data."""
    players = await get_players_container()
    scores = await get_scores_container()

    # Verify the player exists
    try:
        await players.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Delete all score documents for this player (same partition key – rule 2.6)
    query = "SELECT c.id FROM c WHERE c.playerId = @playerId"
    params = [{"name": "@playerId", "value": player_id}]
    score_ids = []
    async for item in scores.query_items(query=query, parameters=params, partition_key=player_id):
        score_ids.append(item["id"])

    for sid in score_ids:
        await scores.delete_item(item=sid, partition_key=player_id)

    # Delete the player document
    await players.delete_item(item=player_id, partition_key=player_id)

    return Response(status_code=204)


# ---------------------------------------------------------------------------
# Score Submission
# ---------------------------------------------------------------------------

@app.post("/api/scores", status_code=201)
async def submit_score(body: dict):
    """Submit a game score for a player."""
    player_id = body.get("playerId")
    score = body.get("score")
    game_mode = body.get("gameMode")

    if not player_id or score is None:
        raise HTTPException(status_code=400, detail="playerId and score are required")

    if not isinstance(score, int) or score < 0:
        raise HTTPException(status_code=400, detail="score must be a non-negative integer")

    # Verify player exists
    players = await get_players_container()
    try:
        player = await players.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Create score document
    score_id = str(uuid.uuid4())
    now = datetime.now(timezone.utc).isoformat()

    score_doc = {
        "id": score_id,
        "scoreId": score_id,
        "playerId": player_id,
        "score": score,
        "timestamp": now,
        "type": "score",
    }
    if game_mode is not None:
        score_doc["gameMode"] = game_mode

    scores_container = await get_scores_container()
    await scores_container.create_item(body=score_doc)

    # Update player stats with ETag-based optimistic concurrency (rule 4.7)
    # Retry loop handles concurrent read-modify-write conflicts
    max_retries = 10
    for attempt in range(max_retries):
        try:
            # Re-read to get latest ETag
            player = await players.read_item(item=player_id, partition_key=player_id)
            etag = player.get("_etag")

            total_games = player.get("totalGames", 0) + 1
            best_score = max(player.get("bestScore", 0), score)

            prev_avg = player.get("averageScore", 0.0)
            prev_total = player.get("totalGames", 0)
            new_average = ((prev_avg * prev_total) + score) / total_games

            player["totalGames"] = total_games
            player["bestScore"] = best_score
            player["averageScore"] = round(new_average, 2)

            await players.replace_item(
                item=player_id,
                body=player,
                etag=etag,
                match_condition=MatchConditions.IfNotModified,
            )
            break  # Success
        except CosmosHttpResponseError as e:
            if e.status_code == 412 and attempt < max_retries - 1:
                continue  # ETag mismatch – retry with fresh read
            raise

    response = {
        "scoreId": score_id,
        "playerId": player_id,
        "score": score,
    }
    return JSONResponse(content=response, status_code=201)


# ---------------------------------------------------------------------------
# Leaderboards
# ---------------------------------------------------------------------------

@app.get("/api/leaderboards/global")
async def global_leaderboard(top: int = Query(default=100, ge=1, le=100)):
    """
    Global top N leaderboard sorted by bestScore descending.
    Tiebreaker: displayName ascending (deterministic).
    Uses composite index (rule 5.2).
    """
    container = await get_players_container()

    # Parameterized queries: TOP value must be a literal integer (rule 3.6)
    query = (
        f"SELECT c.playerId, c.displayName, c.bestScore "
        f"FROM c WHERE c.type = 'player' AND c.bestScore > 0 "
        f"ORDER BY c.bestScore DESC, c.displayName ASC "
        f"OFFSET 0 LIMIT {int(top)}"
    )

    entries = []
    async for item in container.query_items(query=query, enable_cross_partition_query=True):
        entries.append(item)

    result = []
    for idx, entry in enumerate(entries, start=1):
        result.append({
            "rank": idx,
            "playerId": entry["playerId"],
            "displayName": entry["displayName"],
            "score": entry["bestScore"],
        })

    return result


@app.get("/api/leaderboards/regional/{region}")
async def regional_leaderboard(region: str, top: int = Query(default=100, ge=1, le=100)):
    """
    Regional top N leaderboard.
    Same tiebreaking as global (displayName asc).
    """
    container = await get_players_container()

    query = (
        f"SELECT c.playerId, c.displayName, c.bestScore "
        f"FROM c WHERE c.type = 'player' AND c.region = @region AND c.bestScore > 0 "
        f"ORDER BY c.bestScore DESC, c.displayName ASC "
        f"OFFSET 0 LIMIT {int(top)}"
    )
    params = [{"name": "@region", "value": region}]

    entries = []
    async for item in container.query_items(query=query, parameters=params, enable_cross_partition_query=True):
        entries.append(item)

    result = []
    for idx, entry in enumerate(entries, start=1):
        result.append({
            "rank": idx,
            "playerId": entry["playerId"],
            "displayName": entry["displayName"],
            "score": entry["bestScore"],
        })

    return result


# ---------------------------------------------------------------------------
# Player Ranking
# ---------------------------------------------------------------------------

@app.get("/api/players/{player_id}/rank")
async def player_rank(player_id: str):
    """
    Get a player's rank on the global leaderboard and the players
    immediately above and below them (±10 positions).

    Uses COUNT-based rank approach (rule 9.2) instead of full partition scan.
    """
    container = await get_players_container()

    # Get the player first
    try:
        player = await container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    player_best = player.get("bestScore", 0)

    if player_best == 0 and player.get("totalGames", 0) == 0:
        raise HTTPException(status_code=404, detail="Player has no scores")

    # COUNT-based rank query (rule 9.2)
    # Count players with strictly higher bestScore, or same bestScore and displayName < this player's
    count_query = (
        "SELECT VALUE COUNT(1) FROM c WHERE c.type = 'player' AND c.bestScore > 0 AND "
        "("
        "  c.bestScore > @score OR "
        "  (c.bestScore = @score AND c.displayName < @displayName)"
        ")"
    )
    count_params = [
        {"name": "@score", "value": player_best},
        {"name": "@displayName", "value": player["displayName"]},
    ]

    rank = 1
    async for item in container.query_items(query=count_query, parameters=count_params, enable_cross_partition_query=True):
        rank = item + 1

    # Get neighbors (±10 positions)
    # We need players ranked (rank-10) to (rank+10), which means
    # OFFSET (rank-11) LIMIT 21 on the sorted leaderboard
    offset = max(0, rank - 11)
    limit = 21

    neighbors_query = (
        f"SELECT c.playerId, c.displayName, c.bestScore "
        f"FROM c WHERE c.type = 'player' AND c.bestScore > 0 "
        f"ORDER BY c.bestScore DESC, c.displayName ASC "
        f"OFFSET {offset} LIMIT {limit}"
    )

    neighbors = []
    neighbor_rank = offset + 1
    async for item in container.query_items(query=neighbors_query, enable_cross_partition_query=True):
        if item["playerId"] != player_id:
            neighbors.append({
                "rank": neighbor_rank,
                "playerId": item["playerId"],
                "displayName": item["displayName"],
                "score": item["bestScore"],
            })
        neighbor_rank += 1

    return {
        "playerId": player_id,
        "rank": rank,
        "score": player_best,
        "neighbors": neighbors,
    }


# ---------------------------------------------------------------------------
# Score History
# ---------------------------------------------------------------------------

@app.get("/api/players/{player_id}/scores")
async def get_player_scores(player_id: str, limit: int = Query(default=10, ge=1, le=100)):
    """
    Get a player's score history ordered by most recent first.
    Single-partition query (partition key = playerId) – rule 3.1.
    """
    # Verify player exists
    players = await get_players_container()
    try:
        await players.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    scores_container = await get_scores_container()

    query = (
        f"SELECT c.scoreId, c.playerId, c.score, c.gameMode, c.timestamp "
        f"FROM c WHERE c.playerId = @playerId "
        f"ORDER BY c.timestamp DESC "
        f"OFFSET 0 LIMIT {int(limit)}"
    )
    params = [{"name": "@playerId", "value": player_id}]

    result = []
    async for item in scores_container.query_items(
        query=query,
        parameters=params,
        partition_key=player_id,  # Single-partition query (rule 3.1)
    ):
        entry = {
            "scoreId": item["scoreId"],
            "playerId": item["playerId"],
            "score": item["score"],
            "timestamp": item["timestamp"],
        }
        if "gameMode" in item and item["gameMode"] is not None:
            entry["gameMode"] = item["gameMode"]
        result.append(entry)

    return result


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _player_response(player: dict) -> dict:
    """Build a consistent player response body matching the API contract."""
    return {
        "playerId": player["playerId"],
        "displayName": player["displayName"],
        "region": player["region"],
        "totalGames": player.get("totalGames", 0),
        "bestScore": player.get("bestScore", 0),
        "averageScore": player.get("averageScore", 0.0),
    }
