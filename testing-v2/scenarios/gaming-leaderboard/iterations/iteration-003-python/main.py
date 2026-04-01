"""
Gaming Leaderboard API — FastAPI + Azure Cosmos DB (NoSQL, async SDK)
=====================================================================
Implements the gaming-leaderboard API contract with Cosmos DB best practices:
- 3 containers: players (/playerId), scores (/playerId), leaderboards (/leaderboardKey)
- Composite indexes for ORDER BY bestScore DESC, displayName ASC
- ETag-based optimistic concurrency for read-modify-write on player stats
- Type discriminator and schemaVersion on every document
- Synthetic partition keys on leaderboard container (e.g. "global_all-time", "US_all-time")
- Custom excluded indexing paths to reduce write RU cost
- Denormalized player info in leaderboard entries
"""

import os
import uuid
from datetime import datetime, timezone
from typing import Optional

from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.responses import JSONResponse
from azure.cosmos.aio import CosmosClient
from azure.cosmos import PartitionKey
from azure.cosmos.exceptions import CosmosResourceNotFoundError, CosmosHttpResponseError
from azure.core import MatchConditions

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
COSMOS_ENDPOINT = os.environ.get("COSMOS_ENDPOINT", "https://localhost:8081")
COSMOS_KEY = os.environ.get(
    "COSMOS_KEY",
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==",
)
DATABASE_NAME = "gaming-leaderboard"

app = FastAPI()

# Global references — populated on startup
_cosmos_client: Optional[CosmosClient] = None
_database = None
_players_container = None
_scores_container = None
_leaderboards_container = None


# ---------------------------------------------------------------------------
# Startup / Shutdown
# ---------------------------------------------------------------------------
@app.on_event("startup")
async def startup():
    global _cosmos_client, _database
    global _players_container, _scores_container, _leaderboards_container

    _cosmos_client = CosmosClient(COSMOS_ENDPOINT, credential=COSMOS_KEY)
    _database = await _cosmos_client.create_database_if_not_exists(DATABASE_NAME)

    # --- Players container (PK: /playerId) ---
    _players_container = await _database.create_container_if_not_exists(
        id="players",
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy={
            "indexingMode": "consistent",
            "automatic": True,
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [
                {"path": "/_etag/?"},
                {"path": "/averageScore/?"},
                {"path": "/totalScoreSum/?"},
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

    # --- Scores container (PK: /playerId) ---
    _scores_container = await _database.create_container_if_not_exists(
        id="scores",
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy={
            "indexingMode": "consistent",
            "automatic": True,
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [
                {"path": "/_etag/?"},
                {"path": "/gameMode/?"},
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

    # --- Leaderboards container (PK: /leaderboardKey — synthetic) ---
    _leaderboards_container = await _database.create_container_if_not_exists(
        id="leaderboards",
        partition_key=PartitionKey(path="/leaderboardKey"),
        indexing_policy={
            "indexingMode": "consistent",
            "automatic": True,
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [
                {"path": "/_etag/?"},
                {"path": "/schemaVersion/?"},
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


@app.on_event("shutdown")
async def shutdown():
    if _cosmos_client:
        await _cosmos_client.close()


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
    """Return the API-facing player representation."""
    return {
        "playerId": doc["playerId"],
        "displayName": doc["displayName"],
        "region": doc["region"],
        "totalGames": doc.get("totalGames", 0),
        "bestScore": doc.get("bestScore", 0),
        "averageScore": doc.get("averageScore", 0),
    }


def _leaderboard_key_global() -> str:
    return "global_all-time"


def _leaderboard_key_regional(region: str) -> str:
    return f"{region}_all-time"


# ---------------------------------------------------------------------------
# POST /api/players — Create player
# ---------------------------------------------------------------------------
@app.post("/api/players", status_code=201)
async def create_player(request: Request):
    body = await request.json()
    player_id = body.get("playerId")
    display_name = body.get("displayName")
    region = body.get("region")

    if not player_id or not display_name or not region:
        raise HTTPException(status_code=400, detail="playerId, displayName, and region are required")

    doc = {
        "id": player_id,
        "playerId": player_id,
        "displayName": display_name,
        "region": region,
        "totalGames": 0,
        "bestScore": 0,
        "averageScore": 0,
        "totalScoreSum": 0,
        "type": "player",
        "schemaVersion": 1,
    }

    try:
        created = await _players_container.create_item(body=doc)
    except CosmosHttpResponseError as e:
        if e.status_code == 409:
            raise HTTPException(status_code=409, detail="Player already exists")
        raise

    return _player_response(created)


# ---------------------------------------------------------------------------
# GET /api/players/{playerId}
# ---------------------------------------------------------------------------
@app.get("/api/players/{player_id}")
async def get_player(player_id: str):
    try:
        doc = await _players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")
    return _player_response(doc)


# ---------------------------------------------------------------------------
# PATCH /api/players/{playerId}
# ---------------------------------------------------------------------------
@app.patch("/api/players/{player_id}")
async def update_player(player_id: str, request: Request):
    body = await request.json()

    try:
        doc = await _players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    old_region = doc["region"]

    if "displayName" in body:
        doc["displayName"] = body["displayName"]
    if "region" in body:
        doc["region"] = body["region"]

    replaced = await _players_container.replace_item(item=doc["id"], body=doc)

    # If region or displayName changed, update leaderboard entries
    new_region = replaced["region"]
    new_display_name = replaced["displayName"]

    if old_region != new_region or body.get("displayName"):
        await _refresh_leaderboard_entries_for_player(
            player_id, new_display_name, new_region, replaced.get("bestScore", 0), old_region
        )

    return _player_response(replaced)


# ---------------------------------------------------------------------------
# DELETE /api/players/{playerId}
# ---------------------------------------------------------------------------
@app.delete("/api/players/{player_id}", status_code=204)
async def delete_player(player_id: str):
    # Verify player exists
    try:
        await _players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Delete player document
    await _players_container.delete_item(item=player_id, partition_key=player_id)

    # Delete all score documents for this player
    scores = _scores_container.query_items(
        query="SELECT c.id FROM c WHERE c.playerId = @pid",
        parameters=[{"name": "@pid", "value": player_id}],
        partition_key=player_id,
    )
    async for score_doc in scores:
        await _scores_container.delete_item(item=score_doc["id"], partition_key=player_id)

    # Delete leaderboard entries for this player
    lb_entries = _leaderboards_container.query_items(
        query="SELECT c.id, c.leaderboardKey FROM c WHERE c.playerId = @pid",
        parameters=[{"name": "@pid", "value": player_id}],
        enable_cross_partition_query=True,
    )
    async for lb_doc in lb_entries:
        await _leaderboards_container.delete_item(
            item=lb_doc["id"], partition_key=lb_doc["leaderboardKey"]
        )

    return None


# ---------------------------------------------------------------------------
# POST /api/scores — Submit a score
# ---------------------------------------------------------------------------
@app.post("/api/scores", status_code=201)
async def submit_score(request: Request):
    body = await request.json()
    player_id = body.get("playerId")
    score = body.get("score")
    game_mode = body.get("gameMode")

    if not player_id:
        raise HTTPException(status_code=400, detail="playerId is required")
    if score is None:
        raise HTTPException(status_code=400, detail="score is required")
    if not isinstance(score, int) or score < 0:
        raise HTTPException(status_code=400, detail="score must be a non-negative integer")

    score = int(score)

    # Verify player exists
    try:
        player_doc = await _players_container.read_item(item=player_id, partition_key=player_id)
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
        "schemaVersion": 1,
    }
    if game_mode:
        score_doc["gameMode"] = game_mode

    await _scores_container.create_item(body=score_doc)

    # Update player stats with ETag-based optimistic concurrency (retry loop)
    await _update_player_stats(player_id, score)

    return {"scoreId": score_id, "playerId": player_id, "score": score}


async def _update_player_stats(player_id: str, new_score: int, max_retries: int = 20):
    """Read-modify-write player stats with ETag optimistic concurrency."""
    for attempt in range(max_retries):
        try:
            player_doc = await _players_container.read_item(
                item=player_id, partition_key=player_id
            )
        except CosmosResourceNotFoundError:
            return

        old_total = player_doc.get("totalGames", 0)
        old_best = player_doc.get("bestScore", 0)
        old_sum = player_doc.get("totalScoreSum", 0)

        new_total = old_total + 1
        new_sum = old_sum + new_score
        new_best = max(old_best, new_score)
        new_avg = new_sum / new_total if new_total > 0 else 0

        player_doc["totalGames"] = new_total
        player_doc["bestScore"] = new_best
        player_doc["averageScore"] = round(new_avg, 2)
        player_doc["totalScoreSum"] = new_sum

        etag = player_doc.get("_etag")
        try:
            await _players_container.replace_item(
                item=player_doc["id"],
                body=player_doc,
                etag=etag,
                match_condition=MatchConditions.IfNotModified,
            )
            # Successfully wrote — now update leaderboard entries
            await _upsert_leaderboard_entry(
                player_id,
                player_doc["displayName"],
                player_doc["region"],
                new_best,
            )
            return
        except CosmosHttpResponseError as e:
            if e.status_code == 412:
                continue  # ETag mismatch — retry
            raise

    raise HTTPException(status_code=503, detail="Could not update player stats (concurrency)")


async def _upsert_leaderboard_entry(
    player_id: str, display_name: str, region: str, best_score: int
):
    """Upsert the player's entry in both global and regional leaderboards."""
    if best_score <= 0:
        return

    for lb_key in [_leaderboard_key_global(), _leaderboard_key_regional(region)]:
        doc_id = f"{lb_key}_{player_id}"
        lb_doc = {
            "id": doc_id,
            "leaderboardKey": lb_key,
            "playerId": player_id,
            "displayName": display_name,
            "region": region,
            "bestScore": best_score,
            "type": "leaderboardEntry",
            "schemaVersion": 1,
        }
        await _leaderboards_container.upsert_item(body=lb_doc)


async def _refresh_leaderboard_entries_for_player(
    player_id: str, display_name: str, new_region: str, best_score: int, old_region: str
):
    """After player profile update, refresh leaderboard entries."""
    # Delete all existing leaderboard entries for this player
    lb_entries = _leaderboards_container.query_items(
        query="SELECT c.id, c.leaderboardKey FROM c WHERE c.playerId = @pid",
        parameters=[{"name": "@pid", "value": player_id}],
        enable_cross_partition_query=True,
    )
    async for lb_doc in lb_entries:
        await _leaderboards_container.delete_item(
            item=lb_doc["id"], partition_key=lb_doc["leaderboardKey"]
        )

    # Re-create entries with updated info
    if best_score > 0:
        await _upsert_leaderboard_entry(player_id, display_name, new_region, best_score)


# ---------------------------------------------------------------------------
# GET /api/players/{playerId}/scores — Score history
# ---------------------------------------------------------------------------
@app.get("/api/players/{player_id}/scores")
async def get_player_scores(player_id: str, limit: int = Query(default=10, ge=1, le=100)):
    # Verify player exists
    try:
        await _players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    query = (
        "SELECT * FROM c WHERE c.playerId = @pid "
        "ORDER BY c.timestamp DESC"
    )
    items = _scores_container.query_items(
        query=query,
        parameters=[{"name": "@pid", "value": player_id}],
        partition_key=player_id,
    )

    results = []
    async for doc in items:
        results.append({
            "scoreId": doc["scoreId"],
            "playerId": doc["playerId"],
            "score": doc["score"],
            "gameMode": doc.get("gameMode"),
            "timestamp": doc["timestamp"],
        })
        if len(results) >= limit:
            break

    return results


# ---------------------------------------------------------------------------
# GET /api/leaderboards/global
# ---------------------------------------------------------------------------
@app.get("/api/leaderboards/global")
async def global_leaderboard(top: int = Query(default=100, ge=0, le=100)):
    if top == 0:
        return []

    lb_key = _leaderboard_key_global()
    query = (
        "SELECT * FROM c WHERE c.leaderboardKey = @key "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )
    items = _leaderboards_container.query_items(
        query=query,
        parameters=[{"name": "@key", "value": lb_key}],
        partition_key=lb_key,
    )

    results = []
    rank = 1
    async for doc in items:
        results.append({
            "rank": rank,
            "playerId": doc["playerId"],
            "displayName": doc["displayName"],
            "score": doc["bestScore"],
        })
        if len(results) >= top:
            break
        rank += 1

    return results


# ---------------------------------------------------------------------------
# GET /api/leaderboards/regional/{region}
# ---------------------------------------------------------------------------
@app.get("/api/leaderboards/regional/{region}")
async def regional_leaderboard(region: str, top: int = Query(default=100, ge=0, le=100)):
    if top == 0:
        return []

    lb_key = _leaderboard_key_regional(region)
    query = (
        "SELECT * FROM c WHERE c.leaderboardKey = @key "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )
    items = _leaderboards_container.query_items(
        query=query,
        parameters=[{"name": "@key", "value": lb_key}],
        partition_key=lb_key,
    )

    results = []
    rank = 1
    async for doc in items:
        results.append({
            "rank": rank,
            "playerId": doc["playerId"],
            "displayName": doc["displayName"],
            "score": doc["bestScore"],
        })
        if len(results) >= top:
            break
        rank += 1

    return results


# ---------------------------------------------------------------------------
# GET /api/players/{playerId}/rank
# ---------------------------------------------------------------------------
@app.get("/api/players/{player_id}/rank")
async def player_rank(player_id: str):
    # Verify player exists and has scores
    try:
        player_doc = await _players_container.read_item(
            item=player_id, partition_key=player_id
        )
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    if player_doc.get("bestScore", 0) == 0 and player_doc.get("totalGames", 0) == 0:
        raise HTTPException(status_code=404, detail="Player has no scores")

    # Get full global leaderboard to compute rank
    lb_key = _leaderboard_key_global()
    query = (
        "SELECT * FROM c WHERE c.leaderboardKey = @key "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )
    items = _leaderboards_container.query_items(
        query=query,
        parameters=[{"name": "@key", "value": lb_key}],
        partition_key=lb_key,
    )

    all_entries = []
    async for doc in items:
        all_entries.append(doc)

    # Find the player's rank
    player_rank_pos = None
    player_score = None
    for i, entry in enumerate(all_entries):
        if entry["playerId"] == player_id:
            player_rank_pos = i + 1  # 1-based
            player_score = entry["bestScore"]
            break

    if player_rank_pos is None:
        raise HTTPException(status_code=404, detail="Player not found in leaderboard")

    # Get neighbors (±10 positions)
    start = max(0, player_rank_pos - 1 - 10)
    end = min(len(all_entries), player_rank_pos + 10)
    neighbors = []
    for i in range(start, end):
        if i == player_rank_pos - 1:
            continue  # Skip the player themselves
        neighbors.append({
            "rank": i + 1,
            "playerId": all_entries[i]["playerId"],
            "displayName": all_entries[i]["displayName"],
            "score": all_entries[i]["bestScore"],
        })

    return {
        "playerId": player_id,
        "rank": player_rank_pos,
        "score": player_score,
        "neighbors": neighbors,
    }


# ---------------------------------------------------------------------------
# Global exception handler — catch unexpected errors
# ---------------------------------------------------------------------------
@app.exception_handler(Exception)
async def generic_exception_handler(request: Request, exc: Exception):
    if isinstance(exc, HTTPException):
        raise exc
    return JSONResponse(status_code=500, content={"detail": str(exc)})
