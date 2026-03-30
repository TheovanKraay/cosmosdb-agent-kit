"""
Gaming Leaderboard API — FastAPI + Azure Cosmos DB (NoSQL)
==========================================================
Implements the gaming-leaderboard API contract with Cosmos DB best practices:
- Async SDK with aiohttp (rule 4.1, 4.15)
- Singleton CosmosClient (rule 4.18)
- Gateway mode + SSL disabled for emulator (rule 4.6)
- Parameterized queries (rule 3.6)
- Point reads where possible (rule 3.7)
- Composite indexes matching ORDER BY (rule 5.1, 5.2)
- Partition key on playerId for players, playerId for scores (rule 2.7)
- Denormalized player stats for read-heavy leaderboard (rule 1.2)
- ETags for optimistic concurrency on player stats updates (rule 4.7)
"""

import os
import uuid
import logging
from datetime import datetime, timezone
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel

from azure.cosmos.aio import CosmosClient
from azure.cosmos import PartitionKey
from azure.cosmos.exceptions import CosmosResourceNotFoundError, CosmosHttpResponseError

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Configuration (from environment variables — never hardcoded)
# ---------------------------------------------------------------------------
COSMOS_ENDPOINT = os.environ.get(
    "COSMOS_ENDPOINT", "https://localhost:8081"
)
COSMOS_KEY = os.environ.get(
    "COSMOS_KEY",
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==",
)
DATABASE_NAME = os.environ.get("COSMOS_DATABASE", "gaming-leaderboard")

# Max retries for optimistic concurrency (ETag) conflicts
MAX_RETRIES = 10

# ---------------------------------------------------------------------------
# Pydantic Models
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
# Cosmos DB singleton client + database/container references
# ---------------------------------------------------------------------------
cosmos_client: Optional[CosmosClient] = None
database = None
players_container = None
scores_container = None


async def init_cosmos():
    """Initialize Cosmos DB client, database, and containers."""
    global cosmos_client, database, players_container, scores_container

    # Rule 4.18: Reuse CosmosClient as singleton
    # Rule 4.6: Gateway mode (default for Python) + disable SSL verification for emulator
    cosmos_client = CosmosClient(
        url=COSMOS_ENDPOINT,
        credential=COSMOS_KEY,
        connection_verify=False,  # Disable SSL for emulator
    )

    # Create database if not exists
    database = await cosmos_client.create_database_if_not_exists(id=DATABASE_NAME)

    # Players container — partition key on playerId
    # Rule 2.7: Align partition key with query patterns
    # Rule 2.4: High cardinality partition key
    # Rule 2.5: playerId is immutable
    players_container = await database.create_container_if_not_exists(
        id="players",
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy={
            "indexingMode": "consistent",
            "includedPaths": [
                {"path": "/*"}
            ],
            "excludedPaths": [
                {"path": "/\"_etag\"/?"}
            ],
            "compositeIndexes": [
                # For global/regional leaderboard: ORDER BY bestScore DESC, displayName ASC
                # Rule 5.1: Composite index directions must match ORDER BY
                [
                    {"path": "/bestScore", "order": "descending"},
                    {"path": "/displayName", "order": "ascending"}
                ],
                # For regional leaderboard filtering
                [
                    {"path": "/region", "order": "ascending"},
                    {"path": "/bestScore", "order": "descending"},
                    {"path": "/displayName", "order": "ascending"}
                ]
            ]
        },
    )

    # Scores container — partition key on playerId
    # Rule 2.7: Most queries filter by playerId
    scores_container = await database.create_container_if_not_exists(
        id="scores",
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy={
            "indexingMode": "consistent",
            "includedPaths": [
                {"path": "/*"}
            ],
            "excludedPaths": [
                {"path": "/\"_etag\"/?"}
            ],
        },
    )

    logger.info("Cosmos DB initialized: database=%s", DATABASE_NAME)


async def close_cosmos():
    """Close the Cosmos DB client."""
    global cosmos_client
    if cosmos_client:
        await cosmos_client.close()
        cosmos_client = None


# ---------------------------------------------------------------------------
# FastAPI lifespan
# ---------------------------------------------------------------------------
@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_cosmos()
    yield
    await close_cosmos()


app = FastAPI(title="Gaming Leaderboard API", lifespan=lifespan)


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
    """Create a new player profile with zero stats."""
    player_doc = {
        "id": req.playerId,  # Rule 1.4: Use playerId as document id
        "playerId": req.playerId,
        "displayName": req.displayName,
        "region": req.region,
        "totalGames": 0,
        "bestScore": 0,
        "averageScore": 0.0,
        "type": "player",  # Rule 1.11: Type discriminator
    }

    try:
        await players_container.create_item(body=player_doc)
    except CosmosHttpResponseError as e:
        if e.status_code == 409:
            raise HTTPException(status_code=409, detail="Player already exists")
        raise

    return _player_response(player_doc)


@app.get("/api/players/{player_id}")
async def get_player(player_id: str):
    """Get player profile with stats."""
    try:
        # Rule 3.7: Point read with known id + partition key
        doc = await players_container.read_item(item=player_id, partition_key=player_id)
        return _player_response(doc)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")


@app.patch("/api/players/{player_id}")
async def update_player(player_id: str, req: UpdatePlayerRequest):
    """Update player display name and/or region."""
    try:
        # Rule 3.7: Point read
        doc = await players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    if req.displayName is not None:
        doc["displayName"] = req.displayName
    if req.region is not None:
        doc["region"] = req.region

    replaced = await players_container.replace_item(item=doc["id"], body=doc)
    return _player_response(replaced)


@app.delete("/api/players/{player_id}", status_code=204)
async def delete_player(player_id: str):
    """Delete a player and all associated scores."""
    # Verify player exists
    try:
        await players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Delete all scores for this player
    # Rule 3.6: Parameterized query
    query = "SELECT c.id FROM c WHERE c.playerId = @playerId"
    params = [{"name": "@playerId", "value": player_id}]
    score_ids = []
    async for item in scores_container.query_items(
        query=query,
        parameters=params,
        partition_key=player_id,
    ):
        score_ids.append(item["id"])

    for score_id in score_ids:
        await scores_container.delete_item(item=score_id, partition_key=player_id)

    # Delete the player document
    await players_container.delete_item(item=player_id, partition_key=player_id)

    return JSONResponse(status_code=204, content=None)


# ---------------------------------------------------------------------------
# Score Submission
# ---------------------------------------------------------------------------


@app.post("/api/scores", status_code=201)
async def submit_score(req: SubmitScoreRequest):
    """Submit a game score and update player stats."""
    # Validate score is non-negative
    if req.score < 0:
        raise HTTPException(status_code=400, detail="Score must be a non-negative integer")

    # Verify player exists
    try:
        await players_container.read_item(
            item=req.playerId, partition_key=req.playerId
        )
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Create score document
    score_id = str(uuid.uuid4())
    now = datetime.now(timezone.utc).isoformat()
    score_doc = {
        "id": score_id,
        "scoreId": score_id,
        "playerId": req.playerId,
        "score": req.score,
        "timestamp": now,
        "type": "score",
    }
    if req.gameMode is not None:
        score_doc["gameMode"] = req.gameMode

    await scores_container.create_item(body=score_doc)

    # Update player stats with optimistic concurrency (ETag) — rule 4.7
    # Retry loop handles concurrent read-modify-write conflicts
    for attempt in range(MAX_RETRIES):
        try:
            player_doc = await players_container.read_item(
                item=req.playerId, partition_key=req.playerId
            )
            etag = player_doc.get("_etag")

            total_games = player_doc.get("totalGames", 0) + 1
            best_score = max(player_doc.get("bestScore", 0), req.score)

            # Recalculate average: running average
            prev_avg = player_doc.get("averageScore", 0.0)
            prev_total = player_doc.get("totalGames", 0)
            new_avg = ((prev_avg * prev_total) + req.score) / total_games

            player_doc["totalGames"] = total_games
            player_doc["bestScore"] = best_score
            player_doc["averageScore"] = new_avg

            await players_container.replace_item(
                item=player_doc["id"],
                body=player_doc,
                if_match=etag,
            )
            break  # Success
        except CosmosHttpResponseError as e:
            if e.status_code == 412 and attempt < MAX_RETRIES - 1:
                # Precondition failed (ETag mismatch) — retry
                continue
            raise

    return {
        "scoreId": score_id,
        "playerId": req.playerId,
        "score": req.score,
    }


# ---------------------------------------------------------------------------
# Leaderboards
# ---------------------------------------------------------------------------


@app.get("/api/leaderboards/global")
async def global_leaderboard(top: int = Query(default=100, le=100, ge=0)):
    """
    Global top N leaderboard sorted by bestScore DESC, displayName ASC.
    Rule 5.1/5.2: Uses composite index for ORDER BY.
    Rule 3.6: Parameterized queries (TOP uses literal integer per rule 3.8).
    """
    if top == 0:
        return []

    # Rule 3.8: Use literal integer for TOP, not a parameter
    query = (
        f"SELECT c.playerId, c.displayName, c.bestScore "
        f"FROM c WHERE c.type = 'player' AND c.bestScore > 0 "
        f"ORDER BY c.bestScore DESC, c.displayName ASC "
        f"OFFSET 0 LIMIT {int(top)}"
    )

    entries = []
    rank = 1
    async for item in players_container.query_items(
        query=query,
        enable_cross_partition_query=True,
    ):
        entries.append({
            "rank": rank,
            "playerId": item["playerId"],
            "displayName": item["displayName"],
            "score": item["bestScore"],
        })
        rank += 1

    return entries


@app.get("/api/leaderboards/regional/{region}")
async def regional_leaderboard(region: str, top: int = Query(default=100, le=100, ge=0)):
    """
    Regional top N leaderboard filtered by region.
    Rule 3.6: Parameterized query for region filter.
    Rule 3.8: Literal integer for TOP.
    """
    if top == 0:
        return []

    query = (
        f"SELECT c.playerId, c.displayName, c.bestScore "
        f"FROM c WHERE c.type = 'player' AND c.region = @region AND c.bestScore > 0 "
        f"ORDER BY c.bestScore DESC, c.displayName ASC "
        f"OFFSET 0 LIMIT {int(top)}"
    )
    params = [{"name": "@region", "value": region}]

    entries = []
    rank = 1
    async for item in players_container.query_items(
        query=query,
        parameters=params,
        enable_cross_partition_query=True,
    ):
        entries.append({
            "rank": rank,
            "playerId": item["playerId"],
            "displayName": item["displayName"],
            "score": item["bestScore"],
        })
        rank += 1

    return entries


# ---------------------------------------------------------------------------
# Player Ranking
# ---------------------------------------------------------------------------


@app.get("/api/players/{player_id}/rank")
async def player_rank(player_id: str):
    """
    Get player's global rank and ±10 neighbors.
    Rule 9.2: Count-based rank approach.
    """
    # Verify player exists and has scores
    try:
        player_doc = await players_container.read_item(
            item=player_id, partition_key=player_id
        )
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    if player_doc.get("bestScore", 0) == 0:
        raise HTTPException(status_code=404, detail="Player has no scores")

    player_best = player_doc["bestScore"]
    player_name = player_doc["displayName"]

    # Get all players with scores > 0, sorted for ranking
    query = (
        "SELECT c.playerId, c.displayName, c.bestScore "
        "FROM c WHERE c.type = 'player' AND c.bestScore > 0 "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )

    all_ranked = []
    async for item in players_container.query_items(
        query=query,
        enable_cross_partition_query=True,
    ):
        all_ranked.append(item)

    # Find the player's position
    player_index = None
    for i, entry in enumerate(all_ranked):
        if entry["playerId"] == player_id:
            player_index = i
            break

    if player_index is None:
        raise HTTPException(status_code=404, detail="Player not found in rankings")

    player_rank_val = player_index + 1  # 1-based

    # Get neighbors: ±10 positions
    start = max(0, player_index - 10)
    end = min(len(all_ranked), player_index + 11)
    neighbors = []
    for i in range(start, end):
        entry = all_ranked[i]
        if entry["playerId"] == player_id:
            continue
        neighbors.append({
            "rank": i + 1,
            "playerId": entry["playerId"],
            "displayName": entry["displayName"],
            "score": entry["bestScore"],
        })

    return {
        "playerId": player_id,
        "rank": player_rank_val,
        "score": player_best,
        "neighbors": neighbors,
    }


# ---------------------------------------------------------------------------
# Score History
# ---------------------------------------------------------------------------


@app.get("/api/players/{player_id}/scores")
async def get_player_scores(player_id: str, limit: int = Query(default=10, ge=1, le=100)):
    """
    Get a player's score history, most recent first.
    Rule 3.6: Parameterized query.
    Rule 3.8: Literal integer for LIMIT.
    """
    # Verify player exists
    try:
        await players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Rule 3.6: Parameterized query for playerId
    query = (
        f"SELECT c.scoreId, c.playerId, c.score, c.gameMode, c.timestamp "
        f"FROM c WHERE c.playerId = @playerId "
        f"ORDER BY c.timestamp DESC "
        f"OFFSET 0 LIMIT {int(limit)}"
    )
    params = [{"name": "@playerId", "value": player_id}]

    scores = []
    async for item in scores_container.query_items(
        query=query,
        parameters=params,
        partition_key=player_id,
    ):
        score_entry = {
            "scoreId": item["scoreId"],
            "playerId": item["playerId"],
            "score": item["score"],
            "timestamp": item["timestamp"],
        }
        if "gameMode" in item and item["gameMode"] is not None:
            score_entry["gameMode"] = item["gameMode"]
        scores.append(score_entry)

    return scores


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _player_response(doc: dict) -> dict:
    """Format player document for API response with required fields."""
    return {
        "playerId": doc["playerId"],
        "displayName": doc["displayName"],
        "region": doc["region"],
        "totalGames": doc.get("totalGames", 0),
        "bestScore": doc.get("bestScore", 0),
        "averageScore": doc.get("averageScore", 0.0),
    }


# ---------------------------------------------------------------------------
# Main (for direct execution)
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    import uvicorn
    port = int(os.environ.get("PORT", 8000))
    uvicorn.run("main:app", host="0.0.0.0", port=port)
