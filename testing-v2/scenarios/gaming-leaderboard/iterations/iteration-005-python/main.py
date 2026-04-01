"""
Gaming Leaderboard API - Azure Cosmos DB + FastAPI
===================================================
Implements a mobile game leaderboard system with:
- Player management (CRUD)
- Score submission with player stat aggregation
- Global and regional leaderboards (materialized view pattern)
- Player ranking with neighbors

Cosmos DB best practices applied:
- Async SDK with aiohttp
- Singleton CosmosClient
- Multiple containers with purpose-specific partition keys
- Synthetic partition key for leaderboards
- Composite indexes for ORDER BY bestScore DESC, displayName ASC
- Excluded index paths for write optimization
- Type discriminators and schema versioning on all documents
- ETag-based optimistic concurrency for player stat updates
- Parameterized queries throughout
"""

import os
import uuid
from datetime import datetime, timezone
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.responses import JSONResponse
from azure.cosmos.aio import CosmosClient
from azure.cosmos import PartitionKey, exceptions

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
COSMOS_ENDPOINT = os.environ.get("COSMOS_ENDPOINT", "https://localhost:8081")
# Default key is the well-known Azure Cosmos DB Emulator key (not a secret)
COSMOS_KEY = os.environ.get(
    "COSMOS_KEY",
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==",
)
DATABASE_NAME = "gaming-leaderboard"
NEIGHBOR_RANGE = 10

MAX_ETAG_RETRIES = 25

# ---------------------------------------------------------------------------
# Cosmos DB singleton + container references
# ---------------------------------------------------------------------------
cosmos_client: Optional[CosmosClient] = None
database = None
players_container = None
scores_container = None
leaderboards_container = None


async def init_cosmos():
    """Initialize Cosmos DB client, database, and containers."""
    global cosmos_client, database
    global players_container, scores_container, leaderboards_container

    cosmos_client = CosmosClient(COSMOS_ENDPOINT, credential=COSMOS_KEY)
    database = await cosmos_client.create_database_if_not_exists(id=DATABASE_NAME)

    # --- Players container ---
    # Partition key: /playerId for efficient point reads
    players_container = await database.create_container_if_not_exists(
        id="players",
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy={
            "indexingMode": "consistent",
            "automatic": True,
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [
                {"path": "/displayName/?"},
                {"path": "/averageScore/?"},
                {"path": "/schemaVersion/?"},
                {"path": '/"_etag"/?'},
            ],
        },
        offer_throughput=400,
    )

    # --- Scores container ---
    # Partition key: /playerId for efficient per-player queries
    scores_container = await database.create_container_if_not_exists(
        id="scores",
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy={
            "indexingMode": "consistent",
            "automatic": True,
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [
                {"path": "/gameMode/?"},
                {"path": "/schemaVersion/?"},
                {"path": '/"_etag"/?'},
            ],
            "compositeIndexes": [
                [
                    {"path": "/playerId", "order": "ascending"},
                    {"path": "/timestamp", "order": "descending"},
                ]
            ],
        },
        offer_throughput=400,
    )

    # --- Leaderboards container ---
    # Synthetic partition key: /leaderboardKey (e.g., "global_all-time", "US_all-time")
    # Each partition holds all entries for one leaderboard scope
    leaderboards_container = await database.create_container_if_not_exists(
        id="leaderboards",
        partition_key=PartitionKey(path="/leaderboardKey"),
        indexing_policy={
            "indexingMode": "consistent",
            "automatic": True,
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [
                {"path": "/region/?"},
                {"path": "/schemaVersion/?"},
                {"path": '/"_etag"/?'},
            ],
            "compositeIndexes": [
                [
                    {"path": "/bestScore", "order": "descending"},
                    {"path": "/displayName", "order": "ascending"},
                ]
            ],
        },
        offer_throughput=400,
    )


async def close_cosmos():
    """Close the Cosmos DB client."""
    global cosmos_client
    if cosmos_client:
        await cosmos_client.close()
        cosmos_client = None


@asynccontextmanager
async def lifespan(_app: FastAPI):
    await init_cosmos()
    yield
    await close_cosmos()


# ---------------------------------------------------------------------------
# FastAPI application
# ---------------------------------------------------------------------------
app = FastAPI(title="Gaming Leaderboard API", lifespan=lifespan)


# ---------------------------------------------------------------------------
# Health
# ---------------------------------------------------------------------------
@app.get("/health")
async def health():
    return {"status": "ok"}


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _player_response(doc: dict) -> dict:
    """Extract API-facing fields from a player document."""
    return {
        "playerId": doc["playerId"],
        "displayName": doc["displayName"],
        "region": doc["region"],
        "totalGames": doc.get("totalGames", 0),
        "bestScore": doc.get("bestScore", 0),
        "averageScore": doc.get("averageScore", 0),
    }


def _make_lb_key(scope: str) -> str:
    """Create a synthetic leaderboard partition key."""
    return f"{scope}_all-time"


async def _upsert_leaderboard_entry(player_doc: dict):
    """Create or update leaderboard entries for a player (global + regional)."""
    best_score = player_doc.get("bestScore", 0)
    if best_score <= 0:
        return

    player_id = player_doc["playerId"]
    display_name = player_doc["displayName"]
    region = player_doc["region"]

    for scope in ["global", region]:
        lb_key = _make_lb_key(scope)
        entry = {
            "id": f"{lb_key}_{player_id}",
            "leaderboardKey": lb_key,
            "playerId": player_id,
            "displayName": display_name,
            "bestScore": best_score,
            "region": region,
            "type": "leaderboardEntry",
            "schemaVersion": 1,
        }
        await leaderboards_container.upsert_item(body=entry)


async def _remove_leaderboard_entries(player_id: str, region: str):
    """Remove a player from all leaderboards."""
    for scope in ["global", region]:
        lb_key = _make_lb_key(scope)
        doc_id = f"{lb_key}_{player_id}"
        try:
            await leaderboards_container.delete_item(
                item=doc_id, partition_key=lb_key
            )
        except exceptions.CosmosResourceNotFoundError:
            pass


async def _get_player_doc(player_id: str) -> Optional[dict]:
    """Point-read a player document. Returns None if not found."""
    try:
        return await players_container.read_item(
            item=player_id, partition_key=player_id
        )
    except exceptions.CosmosResourceNotFoundError:
        return None


async def _update_player_stats_with_etag(player_id: str, new_score: int):
    """
    Read-modify-write player stats using ETag optimistic concurrency.
    Retries on 412 Precondition Failed (ETag mismatch).
    """
    from azure.core import MatchConditions

    for _ in range(MAX_ETAG_RETRIES):
        player_doc = await _get_player_doc(player_id)
        if player_doc is None:
            return

        etag = player_doc.get("_etag")
        total_games = player_doc.get("totalGames", 0)
        best_score = player_doc.get("bestScore", 0)
        avg_score = player_doc.get("averageScore", 0.0)
        total_score_sum = avg_score * total_games

        total_games += 1
        total_score_sum += new_score
        new_avg = total_score_sum / total_games
        new_best = max(best_score, new_score)

        player_doc["totalGames"] = total_games
        player_doc["bestScore"] = new_best
        player_doc["averageScore"] = round(new_avg, 10)

        try:
            await players_container.replace_item(
                item=player_doc["id"],
                body=player_doc,
                etag=etag,
                match_condition=MatchConditions.IfNotModified,
            )
            await _upsert_leaderboard_entry(player_doc)
            return
        except exceptions.CosmosAccessConditionFailedError:
            continue

    raise HTTPException(
        status_code=503,
        detail="Could not update player stats after retries",
    )


async def _build_leaderboard(lb_key: str, top: int) -> list:
    """Query the leaderboards container for a specific leaderboard."""
    query = (
        "SELECT c.playerId, c.displayName, c.bestScore "
        "FROM c WHERE c.leaderboardKey = @lbKey "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )
    items = leaderboards_container.query_items(
        query=query,
        parameters=[{"name": "@lbKey", "value": lb_key}],
        partition_key=lb_key,
    )
    results = []
    rank = 0
    async for item in items:
        rank += 1
        if rank > top:
            break
        results.append(
            {
                "rank": rank,
                "playerId": item["playerId"],
                "displayName": item["displayName"],
                "score": item["bestScore"],
            }
        )
    return results


async def _get_full_leaderboard(lb_key: str) -> list:
    """Get ALL entries from a leaderboard (used for rank lookups)."""
    query = (
        "SELECT c.playerId, c.displayName, c.bestScore "
        "FROM c WHERE c.leaderboardKey = @lbKey "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )
    items = leaderboards_container.query_items(
        query=query,
        parameters=[{"name": "@lbKey", "value": lb_key}],
        partition_key=lb_key,
    )
    results = []
    rank = 0
    async for item in items:
        rank += 1
        results.append(
            {
                "rank": rank,
                "playerId": item["playerId"],
                "displayName": item["displayName"],
                "score": item["bestScore"],
            }
        )
    return results


# ---------------------------------------------------------------------------
# Player Management
# ---------------------------------------------------------------------------


@app.post("/api/players", status_code=201)
async def create_player(request: Request):
    body = await request.json()

    player_id = body.get("playerId")
    display_name = body.get("displayName")
    region = body.get("region")

    if not player_id or not display_name or not region:
        raise HTTPException(status_code=400, detail="Missing required fields: playerId, displayName, region")

    doc = {
        "id": player_id,
        "playerId": player_id,
        "displayName": display_name,
        "region": region,
        "totalGames": 0,
        "bestScore": 0,
        "averageScore": 0,
        "type": "player",
        "schemaVersion": 1,
    }
    try:
        await players_container.create_item(body=doc)
    except exceptions.CosmosResourceExistsError:
        raise HTTPException(status_code=409, detail="Player already exists")
    return _player_response(doc)


@app.get("/api/players/{player_id}")
async def get_player(player_id: str):
    doc = await _get_player_doc(player_id)
    if doc is None:
        raise HTTPException(status_code=404, detail="Player not found")
    return _player_response(doc)


@app.patch("/api/players/{player_id}")
async def update_player(player_id: str, request: Request):
    doc = await _get_player_doc(player_id)
    if doc is None:
        raise HTTPException(status_code=404, detail="Player not found")

    body = await request.json()
    old_region = doc["region"]
    changed = False

    if "displayName" in body and body["displayName"] is not None:
        doc["displayName"] = body["displayName"]
        changed = True
    if "region" in body and body["region"] is not None:
        doc["region"] = body["region"]
        changed = True

    if changed:
        await players_container.replace_item(item=doc["id"], body=doc)

        # Update leaderboard entries if player has scores
        if doc.get("bestScore", 0) > 0:
            # Remove old leaderboard entries if region changed
            if doc["region"] != old_region:
                await _remove_leaderboard_entries(player_id, old_region)
            await _upsert_leaderboard_entry(doc)

    return _player_response(doc)


@app.delete("/api/players/{player_id}", status_code=204)
async def delete_player(player_id: str):
    doc = await _get_player_doc(player_id)
    if doc is None:
        raise HTTPException(status_code=404, detail="Player not found")

    region = doc["region"]

    # Delete player document
    try:
        await players_container.delete_item(item=player_id, partition_key=player_id)
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Delete all score documents for this player
    score_query = scores_container.query_items(
        query="SELECT c.id FROM c WHERE c.playerId = @pid",
        parameters=[{"name": "@pid", "value": player_id}],
        partition_key=player_id,
    )
    async for score_doc in score_query:
        try:
            await scores_container.delete_item(
                item=score_doc["id"], partition_key=player_id
            )
        except exceptions.CosmosResourceNotFoundError:
            pass

    # Remove from leaderboards
    await _remove_leaderboard_entries(player_id, region)

    return None


# ---------------------------------------------------------------------------
# Score Submission
# ---------------------------------------------------------------------------


@app.post("/api/scores", status_code=201)
async def submit_score(request: Request):
    body = await request.json()

    player_id = body.get("playerId")
    score = body.get("score")
    game_mode = body.get("gameMode")

    if not player_id:
        raise HTTPException(status_code=400, detail="Missing required field: playerId")
    if score is None:
        raise HTTPException(status_code=400, detail="Missing required field: score")
    if not isinstance(score, (int, float)) or score < 0:
        raise HTTPException(status_code=400, detail="Score must be a non-negative integer")

    score = int(score)

    # Validate player exists
    player_doc = await _get_player_doc(player_id)
    if player_doc is None:
        raise HTTPException(status_code=404, detail="Player not found")

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
    if game_mode:
        score_doc["gameMode"] = game_mode

    await scores_container.create_item(body=score_doc)

    # Update player stats with ETag concurrency
    await _update_player_stats_with_etag(player_id, score)

    return {"scoreId": score_id, "playerId": player_id, "score": score}


# ---------------------------------------------------------------------------
# Score History
# ---------------------------------------------------------------------------


@app.get("/api/players/{player_id}/scores")
async def get_player_scores(
    player_id: str,
    limit: int = Query(default=10, ge=1, le=100),
):
    # Verify player exists
    player_doc = await _get_player_doc(player_id)
    if player_doc is None:
        raise HTTPException(status_code=404, detail="Player not found")

    query = (
        "SELECT c.scoreId, c.playerId, c.score, c.gameMode, c.timestamp "
        "FROM c WHERE c.playerId = @pid "
        "ORDER BY c.timestamp DESC"
    )
    items = scores_container.query_items(
        query=query,
        parameters=[{"name": "@pid", "value": player_id}],
        partition_key=player_id,
    )

    results = []
    async for item in items:
        if len(results) >= limit:
            break
        entry = {
            "scoreId": item["scoreId"],
            "playerId": item["playerId"],
            "score": item["score"],
            "timestamp": item["timestamp"],
        }
        if "gameMode" in item and item["gameMode"] is not None:
            entry["gameMode"] = item["gameMode"]
        results.append(entry)

    return results


# ---------------------------------------------------------------------------
# Leaderboards
# ---------------------------------------------------------------------------


@app.get("/api/leaderboards/global")
async def global_leaderboard(
    top: int = Query(default=100, ge=0, le=100),
):
    lb_key = _make_lb_key("global")
    return await _build_leaderboard(lb_key, top)


@app.get("/api/leaderboards/regional/{region}")
async def regional_leaderboard(
    region: str,
    top: int = Query(default=100, ge=0, le=100),
):
    lb_key = _make_lb_key(region)
    return await _build_leaderboard(lb_key, top)


# ---------------------------------------------------------------------------
# Player Ranking
# ---------------------------------------------------------------------------


@app.get("/api/players/{player_id}/rank")
async def player_rank(player_id: str):
    player_doc = await _get_player_doc(player_id)
    if player_doc is None:
        raise HTTPException(status_code=404, detail="Player not found")

    if player_doc.get("bestScore", 0) <= 0:
        raise HTTPException(
            status_code=404, detail="Player has no scores"
        )

    lb_key = _make_lb_key("global")
    all_entries = await _get_full_leaderboard(lb_key)

    player_entry = None
    player_index = -1
    for i, entry in enumerate(all_entries):
        if entry["playerId"] == player_id:
            player_entry = entry
            player_index = i
            break

    if player_entry is None:
        raise HTTPException(
            status_code=404, detail="Player not found in leaderboard"
        )

    # Get neighbors +/- NEIGHBOR_RANGE positions
    start = max(0, player_index - NEIGHBOR_RANGE)
    end = min(len(all_entries), player_index + NEIGHBOR_RANGE + 1)
    neighbors = []
    for i in range(start, end):
        if i == player_index:
            continue
        neighbors.append(all_entries[i])

    return {
        "playerId": player_id,
        "rank": player_entry["rank"],
        "score": player_entry["score"],
        "neighbors": neighbors,
    }
