"""
Gaming Leaderboard API - Azure Cosmos DB

FastAPI application implementing a gaming leaderboard system with:
- Player management (CRUD)
- Score submission and history
- Global and regional leaderboards
- Player ranking with neighbors

Best practices applied:
- Async Cosmos DB client (azure.cosmos.aio) with aiohttp
- Singleton CosmosClient reuse
- Gateway mode + SSL disabled for emulator compatibility
- Point reads where possible (known id + partition key)
- Parameterized queries
- Pre-computed aggregates on player documents (totalGames, bestScore, averageScore)
- Composite indexes for ORDER BY queries
- Partition key aligned with query patterns (playerId for players/scores)
- COUNT-based rank approach for efficient ranking
- ETag-based optimistic concurrency for read-modify-write operations
- Type discriminator and schema version on all documents
- camelCase field naming throughout
"""

import uuid
from datetime import datetime, timezone
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import JSONResponse
from azure.cosmos.exceptions import CosmosResourceExistsError, CosmosHttpResponseError

from cosmos_db import get_cosmos_manager

MAX_RETRIES = 10


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Initialize Cosmos DB on startup, cleanup on shutdown."""
    manager = get_cosmos_manager()
    await manager.initialize()
    yield
    await manager.close()


app = FastAPI(title="Gaming Leaderboard API", lifespan=lifespan)


# ---------------------------------------------------------------------------
# Health
# ---------------------------------------------------------------------------

@app.get("/health")
async def health():
    return JSONResponse(status_code=200, content={"status": "ok"})


# ---------------------------------------------------------------------------
# Player Management
# ---------------------------------------------------------------------------

@app.post("/api/players", status_code=201)
async def create_player(body: dict):
    manager = get_cosmos_manager()
    players = manager.players_container

    player_id = body.get("playerId")
    display_name = body.get("displayName")
    region = body.get("region")

    if not player_id or not display_name or not region:
        raise HTTPException(status_code=400, detail="playerId, displayName, and region are required")

    player_doc = {
        "id": player_id,
        "playerId": player_id,
        "displayName": display_name,
        "region": region,
        "totalGames": 0,
        "bestScore": 0,
        "averageScore": 0.0,
        "type": "player",
        "schemaVersion": 1,
    }

    try:
        await players.create_item(body=player_doc)
    except CosmosResourceExistsError:
        raise HTTPException(status_code=409, detail="Player already exists")

    return _player_response(player_doc)


@app.get("/api/players/{player_id}")
async def get_player(player_id: str):
    manager = get_cosmos_manager()
    players = manager.players_container

    try:
        doc = await players.read_item(item=player_id, partition_key=player_id)
    except Exception:
        raise HTTPException(status_code=404, detail="Player not found")

    return _player_response(doc)


@app.patch("/api/players/{player_id}")
async def update_player(player_id: str, body: dict):
    manager = get_cosmos_manager()
    players = manager.players_container

    try:
        doc = await players.read_item(item=player_id, partition_key=player_id)
    except Exception:
        raise HTTPException(status_code=404, detail="Player not found")

    if "displayName" in body:
        doc["displayName"] = body["displayName"]
    if "region" in body:
        doc["region"] = body["region"]

    doc = await players.replace_item(item=doc["id"], body=doc)
    return _player_response(doc)


@app.delete("/api/players/{player_id}", status_code=204)
async def delete_player(player_id: str):
    manager = get_cosmos_manager()
    players = manager.players_container
    scores_container = manager.scores_container

    # Verify player exists
    try:
        await players.read_item(item=player_id, partition_key=player_id)
    except Exception:
        raise HTTPException(status_code=404, detail="Player not found")

    # Delete all scores for this player
    query = "SELECT c.id FROM c WHERE c.playerId = @playerId"
    params = [{"name": "@playerId", "value": player_id}]
    items = scores_container.query_items(query=query, parameters=params, partition_key=player_id)
    async for score_doc in items:
        await scores_container.delete_item(item=score_doc["id"], partition_key=player_id)

    # Delete player document
    await players.delete_item(item=player_id, partition_key=player_id)

    return JSONResponse(status_code=204, content=None)


# ---------------------------------------------------------------------------
# Score Submission
# ---------------------------------------------------------------------------

@app.post("/api/scores", status_code=201)
async def submit_score(body: dict):
    manager = get_cosmos_manager()
    players = manager.players_container
    scores_container = manager.scores_container

    player_id = body.get("playerId")
    score = body.get("score")
    game_mode = body.get("gameMode")

    if not player_id or score is None:
        raise HTTPException(status_code=400, detail="playerId and score are required")

    if not isinstance(score, (int, float)) or score < 0:
        raise HTTPException(status_code=400, detail="score must be a non-negative number")

    score_id = str(uuid.uuid4())
    timestamp = datetime.now(timezone.utc).isoformat()

    score_doc = {
        "id": score_id,
        "scoreId": score_id,
        "playerId": player_id,
        "score": score,
        "timestamp": timestamp,
        "type": "score",
        "schemaVersion": 1,
    }
    if game_mode is not None:
        score_doc["gameMode"] = game_mode

    # Read player and update with ETag-based optimistic concurrency
    for attempt in range(MAX_RETRIES):
        try:
            player_doc = await players.read_item(item=player_id, partition_key=player_id)
        except Exception:
            raise HTTPException(status_code=404, detail="Player not found")

        etag = player_doc.get("_etag")

        total_games = player_doc.get("totalGames", 0) + 1
        current_best = player_doc.get("bestScore", 0)
        current_avg = player_doc.get("averageScore", 0.0)

        new_best = max(current_best, score)
        new_avg = ((current_avg * (total_games - 1)) + score) / total_games

        player_doc["totalGames"] = total_games
        player_doc["bestScore"] = new_best
        player_doc["averageScore"] = new_avg

        try:
            await players.replace_item(
                item=player_doc["id"],
                body=player_doc,
                if_match=etag,
            )
            break
        except CosmosHttpResponseError as e:
            if e.status_code == 412 and attempt < MAX_RETRIES - 1:
                continue
            raise

    await scores_container.create_item(body=score_doc)

    return {
        "scoreId": score_id,
        "playerId": player_id,
        "score": score,
    }


# ---------------------------------------------------------------------------
# Score History
# ---------------------------------------------------------------------------

@app.get("/api/players/{player_id}/scores")
async def get_player_scores(player_id: str, limit: int = Query(default=10, ge=1, le=100)):
    manager = get_cosmos_manager()
    players = manager.players_container
    scores_container = manager.scores_container

    # Verify player exists
    try:
        await players.read_item(item=player_id, partition_key=player_id)
    except Exception:
        raise HTTPException(status_code=404, detail="Player not found")

    query = (
        "SELECT c.scoreId, c.playerId, c.score, c.gameMode, c.timestamp "
        "FROM c WHERE c.playerId = @playerId AND c.type = 'score' "
        "ORDER BY c.timestamp DESC"
    )
    params = [{"name": "@playerId", "value": player_id}]

    results = []
    items = scores_container.query_items(
        query=query,
        parameters=params,
        partition_key=player_id,
        max_item_count=limit,
    )
    async for item in items:
        results.append(item)
        if len(results) >= limit:
            break

    return results


# ---------------------------------------------------------------------------
# Leaderboards
# ---------------------------------------------------------------------------

@app.get("/api/leaderboards/global")
async def global_leaderboard(top: int = Query(default=100, ge=0, le=100)):
    if top == 0:
        return []

    manager = get_cosmos_manager()
    players = manager.players_container

    query = (
        "SELECT c.playerId, c.displayName, c.bestScore "
        "FROM c WHERE c.type = 'player' AND c.bestScore > 0 "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )

    results = []
    items = players.query_items(query=query, enable_cross_partition_query=True)
    rank = 0
    async for item in items:
        rank += 1
        results.append({
            "rank": rank,
            "playerId": item["playerId"],
            "displayName": item["displayName"],
            "score": item["bestScore"],
        })
        if rank >= top:
            break

    return results


@app.get("/api/leaderboards/regional/{region}")
async def regional_leaderboard(region: str, top: int = Query(default=100, ge=0, le=100)):
    if top == 0:
        return []

    manager = get_cosmos_manager()
    players = manager.players_container

    query = (
        "SELECT c.playerId, c.displayName, c.bestScore "
        "FROM c WHERE c.type = 'player' AND c.region = @region AND c.bestScore > 0 "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )
    params = [{"name": "@region", "value": region}]

    results = []
    items = players.query_items(
        query=query,
        parameters=params,
        enable_cross_partition_query=True,
    )
    rank = 0
    async for item in items:
        rank += 1
        results.append({
            "rank": rank,
            "playerId": item["playerId"],
            "displayName": item["displayName"],
            "score": item["bestScore"],
        })
        if rank >= top:
            break

    return results


# ---------------------------------------------------------------------------
# Player Ranking
# ---------------------------------------------------------------------------

@app.get("/api/players/{player_id}/rank")
async def player_rank(player_id: str):
    manager = get_cosmos_manager()
    players = manager.players_container

    # Get the player document via point read
    try:
        player_doc = await players.read_item(item=player_id, partition_key=player_id)
    except Exception:
        raise HTTPException(status_code=404, detail="Player not found")

    player_best = player_doc.get("bestScore", 0)
    player_display = player_doc.get("displayName", "")

    if player_best == 0:
        raise HTTPException(status_code=404, detail="Player has no scores")

    # COUNT-based rank: count players with higher score,
    # plus players with equal score but displayName < this player's (tiebreaking)
    count_query = (
        "SELECT VALUE COUNT(1) FROM c WHERE c.type = 'player' "
        "AND (c.bestScore > @score OR (c.bestScore = @score AND c.displayName < @displayName))"
    )
    params = [
        {"name": "@score", "value": player_best},
        {"name": "@displayName", "value": player_display},
    ]

    count_items = players.query_items(
        query=count_query,
        parameters=params,
        enable_cross_partition_query=True,
    )
    count_above = 0
    async for val in count_items:
        count_above = val

    my_rank = count_above + 1

    # Get all ranked players for neighbors (sorted by score desc, displayName asc)
    all_query = (
        "SELECT c.playerId, c.displayName, c.bestScore "
        "FROM c WHERE c.type = 'player' AND c.bestScore > 0 "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )

    all_ranked = []
    all_items = players.query_items(
        query=all_query,
        enable_cross_partition_query=True,
    )
    idx = 0
    player_index = -1
    async for item in all_items:
        all_ranked.append(item)
        if item["playerId"] == player_id:
            player_index = idx
        idx += 1

    # Build neighbors (±10 positions around the player)
    neighbors = []
    if player_index >= 0:
        start = max(0, player_index - 10)
        end = min(len(all_ranked), player_index + 11)
        for i in range(start, end):
            if i == player_index:
                continue
            neighbors.append({
                "rank": i + 1,
                "playerId": all_ranked[i]["playerId"],
                "displayName": all_ranked[i]["displayName"],
                "score": all_ranked[i]["bestScore"],
            })

    return {
        "playerId": player_id,
        "rank": my_rank,
        "score": player_best,
        "neighbors": neighbors,
    }


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _player_response(doc: dict) -> dict:
    """Format a player document for API response."""
    return {
        "playerId": doc["playerId"],
        "displayName": doc["displayName"],
        "region": doc["region"],
        "totalGames": doc.get("totalGames", 0),
        "bestScore": doc.get("bestScore", 0),
        "averageScore": doc.get("averageScore", 0.0),
    }
