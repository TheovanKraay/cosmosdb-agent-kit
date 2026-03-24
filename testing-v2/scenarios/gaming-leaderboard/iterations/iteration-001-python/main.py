"""
Gaming Leaderboard API - FastAPI application using Azure Cosmos DB (NoSQL API).

Implements a mobile game leaderboard system with:
- Player profile management (CRUD)
- Score submission and history
- Global and regional leaderboards
- Player ranking with neighbors

Best practices applied from Cosmos DB skills:
- Singleton CosmosClient reuse
- Async SDK (aio) for better throughput
- Partition key aligned with query patterns
- Parameterized queries
- Denormalized player stats for read-heavy leaderboard queries
- Composite indexes for ORDER BY
"""

import os
import uuid
from datetime import datetime, timezone
from typing import Optional

from azure.cosmos.aio import CosmosClient
from azure.cosmos import PartitionKey
from azure.cosmos.exceptions import CosmosResourceNotFoundError, CosmosHttpResponseError
from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
COSMOS_ENDPOINT = os.environ.get("COSMOS_ENDPOINT", "https://localhost:8081")
COSMOS_KEY = os.environ.get(
    "COSMOS_KEY",
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==",
)
DATABASE_NAME = "gaming-leaderboard"

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
# FastAPI application
# ---------------------------------------------------------------------------
app = FastAPI(title="Gaming Leaderboard API")

# Singleton Cosmos client – reused across all requests (SDK best practice 4.18)
_cosmos_client: Optional[CosmosClient] = None
_database = None
_players_container = None
_scores_container = None


async def get_cosmos_resources():
    """Return (database, players_container, scores_container), lazily initialised."""
    global _cosmos_client, _database, _players_container, _scores_container
    if _players_container is not None:
        return _database, _players_container, _scores_container

    _cosmos_client = CosmosClient(COSMOS_ENDPOINT, credential=COSMOS_KEY)
    _database = await _cosmos_client.create_database_if_not_exists(DATABASE_NAME)

    # Players container – partition key on playerId for point reads
    _players_container = await _database.create_container_if_not_exists(
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

    # Scores container – partition key on playerId for efficient per-player queries
    _scores_container = await _database.create_container_if_not_exists(
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
                ]
            ],
        },
    )
    return _database, _players_container, _scores_container


@app.on_event("shutdown")
async def shutdown():
    global _cosmos_client
    if _cosmos_client:
        await _cosmos_client.close()
        _cosmos_client = None


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
    _, players, _ = await get_cosmos_resources()
    player_doc = {
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
        created = await players.create_item(body=player_doc)
    except CosmosHttpResponseError as e:
        if e.status_code == 409:
            raise HTTPException(status_code=409, detail="Player already exists")
        raise
    return _player_response(created)


@app.get("/api/players/{player_id}")
async def get_player(player_id: str):
    _, players, _ = await get_cosmos_resources()
    try:
        item = await players.read_item(item=player_id, partition_key=player_id)
        return _player_response(item)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")


@app.patch("/api/players/{player_id}")
async def update_player(player_id: str, req: UpdatePlayerRequest):
    _, players, _ = await get_cosmos_resources()
    try:
        item = await players.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    if req.displayName is not None:
        item["displayName"] = req.displayName
    if req.region is not None:
        item["region"] = req.region

    replaced = await players.replace_item(item=item["id"], body=item)
    return _player_response(replaced)


@app.delete("/api/players/{player_id}", status_code=204)
async def delete_player(player_id: str):
    _, players, scores = await get_cosmos_resources()
    try:
        await players.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Delete all scores for this player
    query = "SELECT c.id FROM c WHERE c.playerId = @pid"
    params = [{"name": "@pid", "value": player_id}]
    score_ids = []
    async for item in scores.query_items(query=query, parameters=params, partition_key=player_id):
        score_ids.append(item["id"])
    for sid in score_ids:
        await scores.delete_item(item=sid, partition_key=player_id)

    # Delete the player
    await players.delete_item(item=player_id, partition_key=player_id)
    return None


# ---------------------------------------------------------------------------
# Score Submission
# ---------------------------------------------------------------------------

@app.post("/api/scores", status_code=201)
async def submit_score(req: SubmitScoreRequest):
    _, players, scores = await get_cosmos_resources()

    # Validate score is non-negative
    if req.score < 0:
        raise HTTPException(status_code=400, detail="Score must be a non-negative integer")

    # Verify player exists
    try:
        player = await players.read_item(item=req.playerId, partition_key=req.playerId)
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

    await scores.create_item(body=score_doc)

    # Update player stats (denormalized for read-heavy leaderboard queries)
    total_games = player.get("totalGames", 0) + 1
    best_score = max(player.get("bestScore", 0), req.score)
    prev_avg = player.get("averageScore", 0.0)
    new_avg = prev_avg + (req.score - prev_avg) / total_games

    player["totalGames"] = total_games
    player["bestScore"] = best_score
    player["averageScore"] = new_avg
    await players.replace_item(item=player["id"], body=player)

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
    _, players, _ = await get_cosmos_resources()
    query = (
        "SELECT c.playerId, c.displayName, c.bestScore "
        "FROM c WHERE c.type = 'player' AND c.bestScore > 0 "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )
    results = []
    rank = 0
    async for item in players.query_items(
        query=query,
        enable_cross_partition_query=True,
    ):
        rank += 1
        if rank > top:
            break
        results.append({
            "rank": rank,
            "playerId": item["playerId"],
            "displayName": item["displayName"],
            "score": item["bestScore"],
        })
    return results


@app.get("/api/leaderboards/regional/{region}")
async def regional_leaderboard(region: str, top: int = Query(default=100, le=100, ge=1)):
    _, players, _ = await get_cosmos_resources()
    query = (
        "SELECT c.playerId, c.displayName, c.bestScore "
        "FROM c WHERE c.type = 'player' AND c.region = @region AND c.bestScore > 0 "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )
    params = [{"name": "@region", "value": region}]
    results = []
    rank = 0
    async for item in players.query_items(
        query=query,
        parameters=params,
        enable_cross_partition_query=True,
    ):
        rank += 1
        if rank > top:
            break
        results.append({
            "rank": rank,
            "playerId": item["playerId"],
            "displayName": item["displayName"],
            "score": item["bestScore"],
        })
    return results


# ---------------------------------------------------------------------------
# Player Ranking
# ---------------------------------------------------------------------------

@app.get("/api/players/{player_id}/rank")
async def player_rank(player_id: str):
    _, players, _ = await get_cosmos_resources()

    # Verify player exists and has scores
    try:
        player = await players.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    if player.get("bestScore", 0) == 0 and player.get("totalGames", 0) == 0:
        raise HTTPException(status_code=404, detail="Player has no scores")

    # Get full leaderboard to compute rank and neighbors
    query = (
        "SELECT c.playerId, c.displayName, c.bestScore "
        "FROM c WHERE c.type = 'player' AND c.bestScore > 0 "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )
    all_entries = []
    async for item in players.query_items(
        query=query,
        enable_cross_partition_query=True,
    ):
        all_entries.append(item)

    # Find the player's position
    player_index = None
    for i, entry in enumerate(all_entries):
        if entry["playerId"] == player_id:
            player_index = i
            break

    if player_index is None:
        raise HTTPException(status_code=404, detail="Player not found on leaderboard")

    player_rank_val = player_index + 1

    # Get neighbors (±10 positions)
    start = max(0, player_index - 10)
    end = min(len(all_entries), player_index + 11)
    neighbors = []
    for i in range(start, end):
        if i == player_index:
            continue
        neighbors.append({
            "rank": i + 1,
            "playerId": all_entries[i]["playerId"],
            "displayName": all_entries[i]["displayName"],
            "score": all_entries[i]["bestScore"],
        })

    return {
        "playerId": player_id,
        "rank": player_rank_val,
        "score": player.get("bestScore", 0),
        "neighbors": neighbors,
    }


# ---------------------------------------------------------------------------
# Score History
# ---------------------------------------------------------------------------

@app.get("/api/players/{player_id}/scores")
async def get_player_scores(player_id: str, limit: int = Query(default=10, ge=1, le=100)):
    _, players, scores = await get_cosmos_resources()

    # Verify player exists
    try:
        await players.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    query = (
        "SELECT c.scoreId, c.playerId, c.score, c.gameMode, c.timestamp "
        "FROM c WHERE c.playerId = @pid "
        "ORDER BY c.timestamp DESC"
    )
    params = [{"name": "@pid", "value": player_id}]
    results = []
    async for item in scores.query_items(
        query=query,
        parameters=params,
        partition_key=player_id,
    ):
        if len(results) >= limit:
            break
        result = {
            "scoreId": item["scoreId"],
            "playerId": item["playerId"],
            "score": item["score"],
            "timestamp": item["timestamp"],
        }
        if "gameMode" in item:
            result["gameMode"] = item["gameMode"]
        results.append(result)
    return results


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _player_response(doc: dict) -> dict:
    """Extract only API-contract fields from a player document."""
    return {
        "playerId": doc["playerId"],
        "displayName": doc["displayName"],
        "region": doc["region"],
        "totalGames": doc["totalGames"],
        "bestScore": doc["bestScore"],
        "averageScore": doc["averageScore"],
    }
