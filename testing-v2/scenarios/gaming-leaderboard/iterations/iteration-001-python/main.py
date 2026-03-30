"""Gaming Leaderboard API - FastAPI application with Azure Cosmos DB."""

import os
import uuid
from datetime import datetime, timezone
from typing import Optional

from azure.cosmos.aio import CosmosClient
from azure.cosmos import PartitionKey
from azure.cosmos.exceptions import CosmosResourceNotFoundError, CosmosHttpResponseError
from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel

app = FastAPI(title="Gaming Leaderboard API")

# ---------------------------------------------------------------------------
# Configuration — read from environment variables (Rule 4.18: singleton client)
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
# Cosmos DB singleton client and containers (Rule 4.18, Rule 4.15)
# ---------------------------------------------------------------------------
cosmos_client: Optional[CosmosClient] = None
players_container = None
scores_container = None


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
# Startup / shutdown — initialise Cosmos DB resources
# ---------------------------------------------------------------------------
@app.on_event("startup")
async def startup():
    """Create singleton CosmosClient, database, and containers."""
    global cosmos_client, players_container, scores_container

    # Rule 4.18: Reuse CosmosClient as singleton
    cosmos_client = CosmosClient(COSMOS_ENDPOINT, credential=COSMOS_KEY)
    database = await cosmos_client.create_database_if_not_exists(id=DATABASE_NAME)

    # Players container — partitioned by /playerId (high cardinality, immutable,
    # aligns with point-read pattern for player profiles)
    players_container = await database.create_container_if_not_exists(
        id=PLAYERS_CONTAINER,
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy={
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [
                {"path": '/"_etag"/?'},
                {"path": "/schemaVersion/?"},
                {"path": "/type/?"},
            ],
            "compositeIndexes": [
                # For ORDER BY bestScore DESC, displayName ASC (leaderboard queries)
                [
                    {"path": "/bestScore", "order": "descending"},
                    {"path": "/displayName", "order": "ascending"},
                ],
                # Inverse pair (Rule 5.1)
                [
                    {"path": "/bestScore", "order": "ascending"},
                    {"path": "/displayName", "order": "descending"},
                ],
            ],
        },
    )

    # Scores container — partitioned by /playerId (aligns with player score
    # history queries; point reads use scoreId+playerId)
    scores_container = await database.create_container_if_not_exists(
        id=SCORES_CONTAINER,
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy={
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [
                {"path": '/"_etag"/?'},
                {"path": "/schemaVersion/?"},
                {"path": "/type/?"},
            ],
            "compositeIndexes": [
                # For ORDER BY timestamp DESC within a partition
                [
                    {"path": "/playerId", "order": "ascending"},
                    {"path": "/timestamp", "order": "descending"},
                ],
                [
                    {"path": "/playerId", "order": "descending"},
                    {"path": "/timestamp", "order": "ascending"},
                ],
            ],
        },
    )


@app.on_event("shutdown")
async def shutdown():
    """Close the singleton CosmosClient."""
    global cosmos_client
    if cosmos_client:
        await cosmos_client.close()


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
async def create_player(req: CreatePlayerRequest):
    """Create a new player profile."""
    player = {
        "id": req.playerId,
        "playerId": req.playerId,
        "displayName": req.displayName,
        "region": req.region,
        "totalGames": 0,
        "bestScore": 0,
        "averageScore": 0.0,
        "type": "player",
        "schemaVersion": 1,
    }
    try:
        await players_container.create_item(body=player)
    except CosmosHttpResponseError as e:
        if e.status_code == 409:
            raise HTTPException(status_code=409, detail="Player already exists")
        raise
    return _player_response(player)


@app.get("/api/players/{player_id}")
async def get_player(player_id: str):
    """Get player profile with stats (Rule 3.7: point read)."""
    try:
        player = await players_container.read_item(item=player_id, partition_key=player_id)
        return _player_response(player)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")


@app.patch("/api/players/{player_id}")
async def update_player(player_id: str, req: UpdatePlayerRequest):
    """Update player profile fields."""
    try:
        player = await players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    if req.displayName is not None:
        player["displayName"] = req.displayName
    if req.region is not None:
        player["region"] = req.region

    player = await players_container.replace_item(item=player_id, body=player)
    return _player_response(player)


@app.delete("/api/players/{player_id}", status_code=204)
async def delete_player(player_id: str):
    """Delete player and all associated score data."""
    # Verify player exists
    try:
        await players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Delete all scores for this player within same partition
    query = "SELECT c.id FROM c WHERE c.playerId = @pid"
    params = [{"name": "@pid", "value": player_id}]
    score_ids = []
    async for item in scores_container.query_items(
        query=query, parameters=params, partition_key=player_id
    ):
        score_ids.append(item["id"])

    for sid in score_ids:
        await scores_container.delete_item(item=sid, partition_key=player_id)

    # Delete the player document
    await players_container.delete_item(item=player_id, partition_key=player_id)


# ---------------------------------------------------------------------------
# Score Submission
# ---------------------------------------------------------------------------
@app.post("/api/scores", status_code=201)
async def submit_score(req: SubmitScoreRequest):
    """Submit a game score for a player."""
    if req.score < 0:
        raise HTTPException(status_code=400, detail="Score must be a non-negative integer")

    # Verify player exists
    try:
        player = await players_container.read_item(item=req.playerId, partition_key=req.playerId)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

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

    # Update player stats (denormalised — Rule 1.2)
    total_games = player.get("totalGames", 0) + 1
    best_score = max(player.get("bestScore", 0), req.score)
    prev_avg = player.get("averageScore", 0.0)
    average_score = prev_avg + (req.score - prev_avg) / total_games

    player["totalGames"] = total_games
    player["bestScore"] = best_score
    player["averageScore"] = average_score
    await players_container.replace_item(item=req.playerId, body=player)

    return {"scoreId": score_id, "playerId": req.playerId, "score": req.score}


# ---------------------------------------------------------------------------
# Leaderboards
# ---------------------------------------------------------------------------
@app.get("/api/leaderboards/global")
async def global_leaderboard(top: int = Query(default=100, ge=1, le=100)):
    """Global top N leaderboard sorted by bestScore DESC, displayName ASC."""
    # Cross-partition query with composite index (Rule 5.1, 5.2)
    # Rule 3.8: use literal integer for TOP
    query = (
        f"SELECT c.playerId, c.displayName, c.bestScore FROM c "
        f"WHERE c.bestScore > 0 "
        f"ORDER BY c.bestScore DESC, c.displayName ASC "
        f"OFFSET 0 LIMIT {int(top)}"
    )
    entries = []
    async for item in players_container.query_items(
        query=query, enable_cross_partition_query=True
    ):
        entries.append(item)

    return [
        {
            "rank": idx + 1,
            "playerId": e["playerId"],
            "displayName": e["displayName"],
            "score": e["bestScore"],
        }
        for idx, e in enumerate(entries)
    ]


@app.get("/api/leaderboards/regional/{region}")
async def regional_leaderboard(region: str, top: int = Query(default=100, ge=1, le=100)):
    """Regional top N leaderboard for a specific region."""
    query = (
        f"SELECT c.playerId, c.displayName, c.bestScore FROM c "
        f"WHERE c.region = @region AND c.bestScore > 0 "
        f"ORDER BY c.bestScore DESC, c.displayName ASC "
        f"OFFSET 0 LIMIT {int(top)}"
    )
    params = [{"name": "@region", "value": region}]
    entries = []
    async for item in players_container.query_items(
        query=query, parameters=params, enable_cross_partition_query=True
    ):
        entries.append(item)

    return [
        {
            "rank": idx + 1,
            "playerId": e["playerId"],
            "displayName": e["displayName"],
            "score": e["bestScore"],
        }
        for idx, e in enumerate(entries)
    ]


# ---------------------------------------------------------------------------
# Player Ranking
# ---------------------------------------------------------------------------
@app.get("/api/players/{player_id}/rank")
async def player_rank(player_id: str):
    """Get a player's global rank and ±10 neighbours."""
    # Point read for the target player (Rule 3.7)
    try:
        player = await players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    best_score = player.get("bestScore", 0)
    if best_score == 0:
        raise HTTPException(status_code=404, detail="Player has no scores")

    display_name = player["displayName"]

    # Count players with a strictly higher score (Rule 9.2: count-based rank)
    count_query = (
        "SELECT VALUE COUNT(1) FROM c WHERE c.bestScore > @score"
    )
    count_params = [{"name": "@score", "value": best_score}]
    higher_count = 0
    async for val in players_container.query_items(
        query=count_query, parameters=count_params, enable_cross_partition_query=True
    ):
        higher_count = val

    # Count players with the same score but displayName < this player's (tiebreak)
    tie_query = (
        "SELECT VALUE COUNT(1) FROM c "
        "WHERE c.bestScore = @score AND c.displayName < @name"
    )
    tie_params = [
        {"name": "@score", "value": best_score},
        {"name": "@name", "value": display_name},
    ]
    tie_count = 0
    async for val in players_container.query_items(
        query=tie_query, parameters=tie_params, enable_cross_partition_query=True
    ):
        tie_count = val

    player_global_rank = higher_count + tie_count + 1

    # Fetch neighbours: get surrounding players from the full leaderboard
    # We need players ranked from (rank - 10) to (rank + 10)
    offset = max(0, player_global_rank - 11)
    fetch_count = 21 + (player_global_rank - 1 - offset)  # ensure enough entries
    if offset == 0:
        fetch_count = player_global_rank + 10

    neighbor_query = (
        f"SELECT c.playerId, c.displayName, c.bestScore FROM c "
        f"WHERE c.bestScore > 0 "
        f"ORDER BY c.bestScore DESC, c.displayName ASC "
        f"OFFSET {offset} LIMIT {fetch_count}"
    )
    all_nearby = []
    async for item in players_container.query_items(
        query=neighbor_query, enable_cross_partition_query=True
    ):
        all_nearby.append(item)

    neighbors = []
    for idx, e in enumerate(all_nearby):
        r = offset + idx + 1
        if e["playerId"] == player_id:
            continue
        if abs(r - player_global_rank) <= 10:
            neighbors.append(
                {
                    "rank": r,
                    "playerId": e["playerId"],
                    "displayName": e["displayName"],
                    "score": e["bestScore"],
                }
            )

    return {
        "playerId": player_id,
        "rank": player_global_rank,
        "score": best_score,
        "neighbors": neighbors,
    }


# ---------------------------------------------------------------------------
# Score History
# ---------------------------------------------------------------------------
@app.get("/api/players/{player_id}/scores")
async def get_player_scores(player_id: str, limit: int = Query(default=10, ge=1, le=100)):
    """Get a player's score history ordered by most recent first."""
    # Verify player exists (Rule 3.7: point read)
    try:
        await players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    query = (
        f"SELECT c.scoreId, c.playerId, c.score, c.gameMode, c.timestamp FROM c "
        f"WHERE c.playerId = @pid "
        f"ORDER BY c.timestamp DESC "
        f"OFFSET 0 LIMIT {int(limit)}"
    )
    params = [{"name": "@pid", "value": player_id}]

    scores = []
    async for item in scores_container.query_items(
        query=query, parameters=params, partition_key=player_id
    ):
        entry = {
            "scoreId": item["scoreId"],
            "playerId": item["playerId"],
            "score": item["score"],
            "timestamp": item["timestamp"],
        }
        if "gameMode" in item and item["gameMode"] is not None:
            entry["gameMode"] = item["gameMode"]
        scores.append(entry)

    return scores


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
def _player_response(player: dict) -> dict:
    """Return only the contract-defined fields for a player."""
    return {
        "playerId": player["playerId"],
        "displayName": player["displayName"],
        "region": player["region"],
        "totalGames": player["totalGames"],
        "bestScore": player["bestScore"],
        "averageScore": player["averageScore"],
    }
