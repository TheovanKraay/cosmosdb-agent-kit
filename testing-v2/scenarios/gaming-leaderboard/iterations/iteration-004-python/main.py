"""
Gaming Leaderboard API — FastAPI + Azure Cosmos DB (NoSQL, async SDK)
=====================================================================
Implements the full gaming-leaderboard API contract with Cosmos DB
best practices: composite indexes, synthetic partition keys, ETag-based
optimistic concurrency, type discriminators, and schema versioning.
"""

import os
import uuid
from datetime import datetime, timezone

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse
from azure.cosmos.aio import CosmosClient
from azure.cosmos import PartitionKey, exceptions
from azure.core import MatchConditions

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

COSMOS_ENDPOINT = os.environ.get("COSMOS_ENDPOINT", "https://localhost:8081")
COSMOS_KEY = os.environ.get(
    "COSMOS_KEY",
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==",
)
DATABASE_NAME = "gaming-leaderboard-db"

PLAYERS_CONTAINER = "players"
SCORES_CONTAINER = "scores"
LEADERBOARDS_CONTAINER = "leaderboards"

# ---------------------------------------------------------------------------
# FastAPI app
# ---------------------------------------------------------------------------

app = FastAPI(title="Gaming Leaderboard API")

cosmos_client: CosmosClient = None
database = None
players_container = None
scores_container = None
leaderboards_container = None


# ---------------------------------------------------------------------------
# Startup / Shutdown
# ---------------------------------------------------------------------------

@app.on_event("startup")
async def startup():
    global cosmos_client, database
    global players_container, scores_container, leaderboards_container

    cosmos_client = CosmosClient(COSMOS_ENDPOINT, credential=COSMOS_KEY)
    database = await cosmos_client.create_database_if_not_exists(
        id=DATABASE_NAME,
        offer_throughput=400,
    )

    # --- Players container (partition key: /playerId) ---
    players_container = await database.create_container_if_not_exists(
        id=PLAYERS_CONTAINER,
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy={
            "indexingMode": "consistent",
            "automatic": True,
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [
                {"path": "/displayName/?"},
                {"path": '/"_etag"/?'},
            ],
        },
    )

    # --- Scores container (partition key: /playerId) ---
    scores_container = await database.create_container_if_not_exists(
        id=SCORES_CONTAINER,
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy={
            "indexingMode": "consistent",
            "automatic": True,
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [
                {"path": "/gameMode/?"},
                {"path": '/"_etag"/?'},
            ],
            "compositeIndexes": [
                [
                    {"path": "/playerId", "order": "ascending"},
                    {"path": "/timestamp", "order": "descending"},
                ]
            ],
        },
    )

    # --- Leaderboards container (partition key: /leaderboardKey, synthetic) ---
    leaderboards_container = await database.create_container_if_not_exists(
        id=LEADERBOARDS_CONTAINER,
        partition_key=PartitionKey(path="/leaderboardKey"),
        indexing_policy={
            "indexingMode": "consistent",
            "automatic": True,
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [
                {"path": "/playerId/?"},
                {"path": '/"_etag"/?'},
            ],
            "compositeIndexes": [
                [
                    {"path": "/bestScore", "order": "descending"},
                    {"path": "/displayName", "order": "ascending"},
                ]
            ],
        },
    )


@app.on_event("shutdown")
async def shutdown():
    global cosmos_client
    if cosmos_client:
        await cosmos_client.close()


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
    """Extract the API-facing player fields from a Cosmos document."""
    return {
        "playerId": doc["playerId"],
        "displayName": doc["displayName"],
        "region": doc["region"],
        "totalGames": doc.get("totalGames", 0),
        "bestScore": doc.get("bestScore", 0),
        "averageScore": doc.get("averageScore", 0),
    }


def _leaderboard_entry_response(doc: dict, rank: int) -> dict:
    return {
        "rank": rank,
        "playerId": doc["playerId"],
        "displayName": doc["displayName"],
        "score": doc["bestScore"],
    }


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
        raise HTTPException(status_code=400, detail="playerId, displayName, and region are required")

    doc = {
        "id": player_id,
        "playerId": player_id,
        "displayName": display_name,
        "region": region,
        "totalGames": 0,
        "bestScore": 0,
        "averageScore": 0,
        "totalScore": 0,
        "type": "player",
        "schemaVersion": 1,
    }

    try:
        created = await players_container.create_item(body=doc)
    except exceptions.CosmosResourceExistsError:
        raise HTTPException(status_code=409, detail="Player already exists")

    return _player_response(created)


@app.get("/api/players/{player_id}")
async def get_player(player_id: str):
    try:
        doc = await players_container.read_item(item=player_id, partition_key=player_id)
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")
    return _player_response(doc)


@app.patch("/api/players/{player_id}")
async def update_player(player_id: str, request: Request):
    body = await request.json()

    try:
        doc = await players_container.read_item(item=player_id, partition_key=player_id)
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    old_region = doc["region"]
    region_changed = False
    name_changed = False

    if "displayName" in body:
        doc["displayName"] = body["displayName"]
        name_changed = True
    if "region" in body:
        doc["region"] = body["region"]
        if body["region"] != old_region:
            region_changed = True

    updated = await players_container.replace_item(item=doc["id"], body=doc)

    # If region or displayName changed and player has scores, update leaderboard entries
    if (region_changed or name_changed) and doc.get("totalGames", 0) > 0:
        await _rebuild_leaderboard_for_player(doc)

    return _player_response(updated)


@app.delete("/api/players/{player_id}", status_code=204)
async def delete_player(player_id: str):
    # Check player exists
    try:
        await players_container.read_item(item=player_id, partition_key=player_id)
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Delete all score documents for this player
    scores = scores_container.query_items(
        query="SELECT * FROM c WHERE c.playerId = @pid",
        parameters=[{"name": "@pid", "value": player_id}],
        partition_key=player_id,
    )
    async for score_doc in scores:
        await scores_container.delete_item(item=score_doc["id"], partition_key=player_id)

    # Delete leaderboard entries for this player
    lb_entries = leaderboards_container.query_items(
        query="SELECT * FROM c WHERE c.playerId = @pid",
        parameters=[{"name": "@pid", "value": player_id}],
        enable_cross_partition_query=True,
    )
    async for lb_doc in lb_entries:
        await leaderboards_container.delete_item(
            item=lb_doc["id"], partition_key=lb_doc["leaderboardKey"]
        )

    # Delete the player document
    await players_container.delete_item(item=player_id, partition_key=player_id)

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
        raise HTTPException(status_code=400, detail="playerId is required")
    if score is None:
        raise HTTPException(status_code=400, detail="score is required")
    if not isinstance(score, (int, float)) or score < 0:
        raise HTTPException(status_code=400, detail="score must be a non-negative integer")

    score = int(score)

    # Verify player exists
    try:
        player_doc = await players_container.read_item(item=player_id, partition_key=player_id)
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    score_id = str(uuid.uuid4())
    now = datetime.now(timezone.utc).isoformat()

    # Store the score document
    score_doc = {
        "id": score_id,
        "scoreId": score_id,
        "playerId": player_id,
        "score": score,
        "gameMode": game_mode or "",
        "timestamp": now,
        "type": "score",
        "schemaVersion": 1,
    }
    await scores_container.create_item(body=score_doc)

    # Update player stats with ETag-based optimistic concurrency
    await _update_player_stats(player_id, score)

    return {"scoreId": score_id, "playerId": player_id, "score": score}


async def _update_player_stats(player_id: str, new_score: int, max_retries: int = 20):
    """
    Read-modify-write player stats with ETag concurrency control.
    Retries on 412 Precondition Failed (ETag mismatch).
    """
    for attempt in range(max_retries):
        try:
            player_doc = await players_container.read_item(
                item=player_id, partition_key=player_id
            )
        except exceptions.CosmosResourceNotFoundError:
            return

        etag = player_doc.get("_etag")
        total_games = player_doc.get("totalGames", 0)
        total_score = player_doc.get("totalScore", 0)
        best_score = player_doc.get("bestScore", 0)

        total_games += 1
        total_score += new_score
        if new_score > best_score:
            best_score = new_score
        average_score = total_score / total_games

        player_doc["totalGames"] = total_games
        player_doc["totalScore"] = total_score
        player_doc["bestScore"] = best_score
        player_doc["averageScore"] = round(average_score, 2)

        try:
            await players_container.replace_item(
                item=player_doc["id"],
                body=player_doc,
                etag=etag,
                match_condition=MatchConditions.IfNotModified,
            )
            # Success — update leaderboard
            await _upsert_leaderboard_entry(player_doc)
            return
        except exceptions.CosmosAccessConditionFailedError:
            # ETag mismatch — retry
            continue

    raise HTTPException(status_code=503, detail="Could not update player stats after retries")


async def _upsert_leaderboard_entry(player_doc: dict):
    """Upsert denormalized leaderboard entries for global + regional boards."""
    player_id = player_doc["playerId"]
    display_name = player_doc["displayName"]
    region = player_doc["region"]
    best_score = player_doc["bestScore"]

    # Global leaderboard entry
    global_entry = {
        "id": f"global_{player_id}",
        "leaderboardKey": "global",
        "playerId": player_id,
        "displayName": display_name,
        "region": region,
        "bestScore": best_score,
        "type": "leaderboardEntry",
        "schemaVersion": 1,
    }
    await leaderboards_container.upsert_item(body=global_entry)

    # Regional leaderboard entry
    regional_key = f"region_{region}"
    regional_entry = {
        "id": f"{regional_key}_{player_id}",
        "leaderboardKey": regional_key,
        "playerId": player_id,
        "displayName": display_name,
        "region": region,
        "bestScore": best_score,
        "type": "leaderboardEntry",
        "schemaVersion": 1,
    }
    await leaderboards_container.upsert_item(body=regional_entry)


async def _rebuild_leaderboard_for_player(player_doc: dict):
    """
    After a region change, remove old regional entry and create new one.
    Also update the global entry with the new display name / region.
    """
    player_id = player_doc["playerId"]

    # Delete any old leaderboard entries for this player
    old_entries = leaderboards_container.query_items(
        query="SELECT * FROM c WHERE c.playerId = @pid",
        parameters=[{"name": "@pid", "value": player_id}],
        enable_cross_partition_query=True,
    )
    async for entry in old_entries:
        await leaderboards_container.delete_item(
            item=entry["id"], partition_key=entry["leaderboardKey"]
        )

    # Re-create entries with updated info
    await _upsert_leaderboard_entry(player_doc)


# ---------------------------------------------------------------------------
# Leaderboards
# ---------------------------------------------------------------------------

@app.get("/api/leaderboards/global")
async def global_leaderboard(top: int = 100):
    if top <= 0:
        return JSONResponse(content=[], status_code=200)

    query = (
        "SELECT * FROM c WHERE c.leaderboardKey = 'global' "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )
    items = leaderboards_container.query_items(
        query=query,
        partition_key="global",
    )
    results = []
    rank = 1
    async for doc in items:
        if rank > top:
            break
        results.append(_leaderboard_entry_response(doc, rank))
        rank += 1

    return JSONResponse(content=results, status_code=200)


@app.get("/api/leaderboards/regional/{region}")
async def regional_leaderboard(region: str, top: int = 100):
    if top <= 0:
        return JSONResponse(content=[], status_code=200)

    lb_key = f"region_{region}"
    query = (
        "SELECT * FROM c WHERE c.leaderboardKey = @key "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )
    items = leaderboards_container.query_items(
        query=query,
        parameters=[{"name": "@key", "value": lb_key}],
        partition_key=lb_key,
    )
    results = []
    rank = 1
    async for doc in items:
        if rank > top:
            break
        results.append(_leaderboard_entry_response(doc, rank))
        rank += 1

    return JSONResponse(content=results, status_code=200)


# ---------------------------------------------------------------------------
# Player Rank
# ---------------------------------------------------------------------------

@app.get("/api/players/{player_id}/rank")
async def player_rank(player_id: str):
    # Verify player exists
    try:
        await players_container.read_item(item=player_id, partition_key=player_id)
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Get the full global leaderboard sorted
    query = (
        "SELECT * FROM c WHERE c.leaderboardKey = 'global' "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )
    items = leaderboards_container.query_items(
        query=query,
        partition_key="global",
    )

    all_entries = []
    async for doc in items:
        all_entries.append(doc)

    # Find the player's position
    player_index = None
    for i, entry in enumerate(all_entries):
        if entry["playerId"] == player_id:
            player_index = i
            break

    if player_index is None:
        raise HTTPException(status_code=404, detail="Player has no scores")

    player_entry = all_entries[player_index]
    player_rank_val = player_index + 1

    # Get neighbors ±10
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
        "score": player_entry["bestScore"],
        "neighbors": neighbors,
    }


# ---------------------------------------------------------------------------
# Score History
# ---------------------------------------------------------------------------

@app.get("/api/players/{player_id}/scores")
async def get_player_scores(player_id: str, limit: int = 10):
    # Verify player exists
    try:
        await players_container.read_item(item=player_id, partition_key=player_id)
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    query = (
        "SELECT * FROM c WHERE c.playerId = @pid "
        "ORDER BY c.timestamp DESC"
    )
    items = scores_container.query_items(
        query=query,
        parameters=[{"name": "@pid", "value": player_id}],
        partition_key=player_id,
    )

    results = []
    count = 0
    async for doc in items:
        if count >= limit:
            break
        results.append({
            "scoreId": doc["scoreId"],
            "playerId": doc["playerId"],
            "score": doc["score"],
            "gameMode": doc.get("gameMode", ""),
            "timestamp": doc["timestamp"],
        })
        count += 1

    return JSONResponse(content=results, status_code=200)


# ---------------------------------------------------------------------------
# Main entry point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    import uvicorn

    port = int(os.environ.get("PORT", 8000))
    uvicorn.run(app, host="0.0.0.0", port=port)
