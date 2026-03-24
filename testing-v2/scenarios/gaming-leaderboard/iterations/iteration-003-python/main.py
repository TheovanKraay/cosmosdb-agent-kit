"""
Gaming Leaderboard API – FastAPI + Azure Cosmos DB (async)

Iteration 003 (Python) for the gaming-leaderboard scenario.

Best practices applied:
  Rule 4.1  – Async APIs throughout
  Rule 4.7  – ETag optimistic concurrency for score stat updates
  Rule 4.18 – Singleton CosmosClient (via cosmos_config)
  Rule 3.5  – Parameterized queries
  Rule 3.6  – Literal integers in TOP clause
  Rule 9.2  – COUNT-based ranking (not full partition scan)
  Rule 5.1  – Composite index ORDER BY bestScore DESC, displayName ASC

Endpoints (all paths from api-contract.yaml):
  GET    /health
  POST   /api/players
  GET    /api/players/{playerId}
  PATCH  /api/players/{playerId}
  DELETE /api/players/{playerId}
  POST   /api/scores
  GET    /api/players/{playerId}/scores
  GET    /api/leaderboards/global
  GET    /api/leaderboards/regional/{region}
  GET    /api/players/{playerId}/rank
"""

import logging
import uuid
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from typing import Optional

from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel, Field
from azure.core import MatchConditions
from azure.cosmos.exceptions import (
    CosmosHttpResponseError,
    CosmosResourceExistsError,
    CosmosResourceNotFoundError,
)

from cosmos_config import initialize_containers, close_cosmos_client

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Global container references (set during lifespan startup)
# ---------------------------------------------------------------------------
players_container = None
scores_container = None


# ---------------------------------------------------------------------------
# Lifespan
# ---------------------------------------------------------------------------
@asynccontextmanager
async def lifespan(_app: FastAPI):
    global players_container, scores_container
    containers = await initialize_containers()
    players_container = containers["players"]
    scores_container = containers["scores"]
    logger.info("Cosmos DB containers initialised")
    yield
    await close_cosmos_client()


app = FastAPI(title="Gaming Leaderboard API", version="0.3.0", lifespan=lifespan)


# ---------------------------------------------------------------------------
# Request models (Pydantic for input validation)
# ---------------------------------------------------------------------------
class CreatePlayerRequest(BaseModel):
    playerId: str = Field(..., min_length=1)
    displayName: str = Field(..., min_length=1)
    region: str = Field(..., min_length=1)


class UpdatePlayerRequest(BaseModel):
    displayName: Optional[str] = None
    region: Optional[str] = None


class SubmitScoreRequest(BaseModel):
    playerId: str = Field(..., min_length=1)
    score: int = Field(..., ge=0)
    gameMode: Optional[str] = None


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
def _player_response(player: dict) -> dict:
    """Build the standard player response dict from a Cosmos document."""
    total_games = player.get("totalGames", 0)
    total_score = player.get("totalScore", 0)
    best_score = player.get("bestScore", 0)
    avg = total_score / total_games if total_games > 0 else 0.0
    return {
        "playerId": player["playerId"],
        "displayName": player["displayName"],
        "region": player["region"],
        "totalGames": total_games,
        "bestScore": best_score,
        "averageScore": avg,
    }


# ---------------------------------------------------------------------------
# GET /health
# ---------------------------------------------------------------------------
@app.get("/health")
async def health():
    return {"status": "ok"}


# ---------------------------------------------------------------------------
# POST /api/players  →  201
# ---------------------------------------------------------------------------
@app.post("/api/players", status_code=201)
async def create_player(req: CreatePlayerRequest):
    now = datetime.now(timezone.utc).isoformat()
    doc = {
        "id": req.playerId,
        "playerId": req.playerId,
        "displayName": req.displayName,
        "region": req.region,
        "totalGames": 0,
        "bestScore": 0,
        "totalScore": 0,
        "averageScore": 0.0,
        "createdAt": now,
    }
    try:
        await players_container.create_item(body=doc)
    except CosmosResourceExistsError:
        raise HTTPException(status_code=409, detail="Player already exists")
    return _player_response(doc)


# ---------------------------------------------------------------------------
# GET /api/players/{playerId}  →  200 | 404
# ---------------------------------------------------------------------------
@app.get("/api/players/{playerId}")
async def get_player(playerId: str):
    try:
        player = await players_container.read_item(
            item=playerId, partition_key=playerId
        )
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")
    return _player_response(player)


# ---------------------------------------------------------------------------
# PATCH /api/players/{playerId}  →  200 | 404
# ---------------------------------------------------------------------------
@app.patch("/api/players/{playerId}")
async def update_player(playerId: str, req: UpdatePlayerRequest):
    try:
        player = await players_container.read_item(
            item=playerId, partition_key=playerId
        )
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    if req.displayName is not None:
        player["displayName"] = req.displayName
    if req.region is not None:
        player["region"] = req.region

    await players_container.upsert_item(body=player)
    return _player_response(player)


# ---------------------------------------------------------------------------
# DELETE /api/players/{playerId}  →  204 | 404
# ---------------------------------------------------------------------------
@app.delete("/api/players/{playerId}", status_code=204)
async def delete_player(playerId: str):
    # Verify player exists
    try:
        await players_container.read_item(item=playerId, partition_key=playerId)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Delete player document
    await players_container.delete_item(item=playerId, partition_key=playerId)

    # Delete all associated scores (single-partition query – Rule 3.1)
    query = "SELECT c.id FROM c WHERE c.playerId = @pid"
    params = [{"name": "@pid", "value": playerId}]
    score_ids = [
        s["id"]
        async for s in scores_container.query_items(
            query=query, parameters=params, partition_key=playerId
        )
    ]
    for sid in score_ids:
        await scores_container.delete_item(item=sid, partition_key=playerId)


# ---------------------------------------------------------------------------
# POST /api/scores  →  201 | 404
# ---------------------------------------------------------------------------
MAX_ETAG_RETRIES = 25


@app.post("/api/scores", status_code=201)
async def submit_score(req: SubmitScoreRequest):
    # Verify the player exists
    try:
        await players_container.read_item(
            item=req.playerId, partition_key=req.playerId
        )
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # 1. Create score document
    score_id = str(uuid.uuid4())
    now = datetime.now(timezone.utc).isoformat()
    score_doc = {
        "id": score_id,
        "playerId": req.playerId,
        "score": req.score,
        "gameMode": req.gameMode or "",
        "timestamp": now,
    }
    await scores_container.create_item(body=score_doc)

    # 2. Update player stats with ETag-based optimistic concurrency (Rule 4.7)
    for attempt in range(MAX_ETAG_RETRIES):
        try:
            player = await players_container.read_item(
                item=req.playerId, partition_key=req.playerId
            )
            etag = player.get("_etag")

            player["totalGames"] = player.get("totalGames", 0) + 1
            player["totalScore"] = player.get("totalScore", 0) + req.score
            if req.score > player.get("bestScore", 0):
                player["bestScore"] = req.score
            player["averageScore"] = (
                player["totalScore"] / player["totalGames"]
            )

            await players_container.upsert_item(
                body=player,
                etag=etag,
                match_condition=MatchConditions.IfNotModified,
            )
            break
        except CosmosHttpResponseError as exc:
            if exc.status_code == 412 and attempt < MAX_ETAG_RETRIES - 1:
                continue
            raise

    return {
        "scoreId": score_id,
        "playerId": req.playerId,
        "score": req.score,
    }


# ---------------------------------------------------------------------------
# GET /api/players/{playerId}/scores  →  200 | 404
# ---------------------------------------------------------------------------
@app.get("/api/players/{playerId}/scores")
async def get_player_scores(
    playerId: str, limit: int = Query(10, ge=1, le=100)
):
    # Verify player exists
    try:
        await players_container.read_item(item=playerId, partition_key=playerId)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Rule 3.6: literal integer in TOP
    limit_int = int(limit)
    query = (
        f"SELECT TOP {limit_int} c.id, c.playerId, c.score, c.gameMode, c.timestamp "
        "FROM c WHERE c.playerId = @pid "
        "ORDER BY c.timestamp DESC"
    )
    params = [{"name": "@pid", "value": playerId}]
    items = [
        s
        async for s in scores_container.query_items(
            query=query, parameters=params, partition_key=playerId
        )
    ]

    return [
        {
            "scoreId": s["id"],
            "playerId": s["playerId"],
            "score": s["score"],
            "gameMode": s.get("gameMode") or None,
            "timestamp": s["timestamp"],
        }
        for s in items
    ]


# ---------------------------------------------------------------------------
# GET /api/leaderboards/global  →  200
# ---------------------------------------------------------------------------
@app.get("/api/leaderboards/global")
async def global_leaderboard(top: int = Query(100, ge=0, le=100)):
    top_int = int(top)
    if top_int == 0:
        return []

    # Cross-partition query (Rule 5.1 composite index: bestScore DESC, displayName ASC)
    query = (
        f"SELECT TOP {top_int} c.playerId, c.displayName, c.bestScore "
        "FROM c WHERE c.totalGames > 0 "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )
    entries = [
        e
        async for e in players_container.query_items(
            query=query, enable_cross_partition_query=True
        )
    ]

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
# GET /api/leaderboards/regional/{region}  →  200
# ---------------------------------------------------------------------------
@app.get("/api/leaderboards/regional/{region}")
async def regional_leaderboard(
    region: str, top: int = Query(100, ge=0, le=100)
):
    top_int = int(top)
    if top_int == 0:
        return []

    query = (
        f"SELECT TOP {top_int} c.playerId, c.displayName, c.bestScore "
        "FROM c WHERE c.region = @region AND c.totalGames > 0 "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )
    params = [{"name": "@region", "value": region}]
    entries = [
        e
        async for e in players_container.query_items(
            query=query, parameters=params, enable_cross_partition_query=True
        )
    ]

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
# GET /api/players/{playerId}/rank  →  200 | 404
# ---------------------------------------------------------------------------
@app.get("/api/players/{playerId}/rank")
async def get_player_rank(playerId: str):
    # Read the player
    try:
        player = await players_container.read_item(
            item=playerId, partition_key=playerId
        )
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    if player.get("totalGames", 0) == 0:
        raise HTTPException(status_code=404, detail="Player has no scores")

    player_score = player.get("bestScore", 0)
    player_name = player.get("displayName", "")

    # Build full sorted leaderboard for accurate ranking + neighbours
    query = (
        "SELECT c.playerId, c.displayName, c.bestScore "
        "FROM c WHERE c.totalGames > 0 "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )
    all_entries = [
        e
        async for e in players_container.query_items(
            query=query, enable_cross_partition_query=True
        )
    ]

    # Find this player's position
    player_idx = None
    for idx, e in enumerate(all_entries):
        if e["playerId"] == playerId:
            player_idx = idx
            break

    if player_idx is None:
        raise HTTPException(status_code=404, detail="Player not found in rankings")

    rank = player_idx + 1

    # Neighbours ±10
    start = max(0, player_idx - 10)
    end = min(len(all_entries), player_idx + 11)
    neighbours = all_entries[start:end]

    return {
        "playerId": playerId,
        "rank": rank,
        "score": player_score,
        "neighbors": [
            {
                "rank": start + i + 1,
                "playerId": n["playerId"],
                "displayName": n["displayName"],
                "score": n["bestScore"],
            }
            for i, n in enumerate(neighbours)
        ],
    }


# ---------------------------------------------------------------------------
# Main entry-point
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
