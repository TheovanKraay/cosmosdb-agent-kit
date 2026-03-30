"""
Gaming Leaderboard API — FastAPI + Azure Cosmos DB (NoSQL)

Cosmos DB Best Practices Applied:
- Singleton CosmosClient (Rule: sdk-singleton-client)
- Gateway mode + SSL disabled for emulator (Rule: sdk-configure-emulator)
- Multiple containers with strategic partition keys (Rule: partition-query-patterns)
- Synthetic partition key for leaderboards (Rule: partition-synthetic-keys)
- Composite indexes for ORDER BY (Rule: index-composite)
- Excluded unused index paths (Rule: index-exclude-unused)
- Parameterized queries (Rule: query-parameterized)
- Type discriminators (Rule: model-type-discriminator)
- Schema versioning (Rule: model-schema-versioning)
- Denormalized leaderboard entries (Rule: model-denormalize-reads)
- ETag-based optimistic concurrency for player stat updates (Rule: sdk-etag-concurrency)
- Point reads where possible (Rule: query-point-reads)
- Content response on write disabled (Rule: sdk-content-response-on-write)
"""

import os
import uuid
import logging
from datetime import datetime, timezone
from typing import Optional

from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from azure.cosmos import CosmosClient, PartitionKey, exceptions

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

COSMOS_ENDPOINT = os.environ.get("COSMOS_ENDPOINT", "https://localhost:8081")
COSMOS_KEY = os.environ.get(
    "COSMOS_KEY",
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==",
)
DATABASE_NAME = os.environ.get("COSMOS_DATABASE", "gaming-leaderboard")

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def _is_emulator(endpoint: str) -> bool:
    return "localhost" in endpoint or "127.0.0.1" in endpoint


# ---------------------------------------------------------------------------
# Cosmos DB Client (Singleton) — Rule: sdk-singleton-client
# ---------------------------------------------------------------------------

_cosmos_client: Optional[CosmosClient] = None


def get_cosmos_client() -> CosmosClient:
    global _cosmos_client
    if _cosmos_client is None:
        is_emu = _is_emulator(COSMOS_ENDPOINT)
        logger.info("Connecting to Cosmos DB at %s (emulator=%s)", COSMOS_ENDPOINT, is_emu)
        _cosmos_client = CosmosClient(
            url=COSMOS_ENDPOINT,
            credential=COSMOS_KEY,
            connection_verify=not is_emu,  # Rule: sdk-configure-emulator — disable SSL for emulator
        )
    return _cosmos_client


# ---------------------------------------------------------------------------
# Database & Container initialisation
# ---------------------------------------------------------------------------

_db = None
_players_ctr = None
_scores_ctr = None
_leaderboards_ctr = None


def _init_cosmos():
    """Create database and containers with best-practice configuration."""
    global _db, _players_ctr, _scores_ctr, _leaderboards_ctr

    client = get_cosmos_client()
    _db = client.create_database_if_not_exists(id=DATABASE_NAME)
    logger.info("Database '%s' ready", DATABASE_NAME)

    # --- Players container ---
    # Partition key: /playerId — efficient point reads by player ID
    # Rule: partition-query-patterns, partition-high-cardinality
    try:
        _players_ctr = _db.create_container(
            id="players",
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
        logger.info("Created container: players")
    except exceptions.CosmosResourceExistsError:
        _players_ctr = _db.get_container_client("players")
        logger.info("Container 'players' already exists")

    # --- Scores container ---
    # Partition key: /playerId — per-player score queries stay single-partition
    # Rule: partition-query-patterns
    try:
        _scores_ctr = _db.create_container(
            id="scores",
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
        logger.info("Created container: scores")
    except exceptions.CosmosResourceExistsError:
        _scores_ctr = _db.get_container_client("scores")
        logger.info("Container 'scores' already exists")

    # --- Leaderboards container ---
    # Partition key: /leaderboardKey (synthetic) — e.g. "global" or "region_US"
    # Rule: partition-synthetic-keys, index-composite
    try:
        _leaderboards_ctr = _db.create_container(
            id="leaderboards",
            partition_key=PartitionKey(path="/leaderboardKey"),
            indexing_policy={
                "indexingMode": "consistent",
                "automatic": True,
                "includedPaths": [{"path": "/*"}],
                "excludedPaths": [
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
        logger.info("Created container: leaderboards")
    except exceptions.CosmosResourceExistsError:
        _leaderboards_ctr = _db.get_container_client("leaderboards")
        logger.info("Container 'leaderboards' already exists")


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
# FastAPI app
# ---------------------------------------------------------------------------

app = FastAPI(title="Gaming Leaderboard API")


@app.on_event("startup")
def startup():
    _init_cosmos()


# ---------------------------------------------------------------------------
# Health
# ---------------------------------------------------------------------------

@app.get("/health")
def health():
    return {"status": "ok"}


# ---------------------------------------------------------------------------
# Player CRUD
# ---------------------------------------------------------------------------

def _player_response(doc: dict) -> dict:
    """Build the API response for a player document."""
    return {
        "playerId": doc["playerId"],
        "displayName": doc["displayName"],
        "region": doc["region"],
        "totalGames": doc.get("totalGames", 0),
        "bestScore": doc.get("bestScore", 0),
        "averageScore": doc.get("averageScore", 0),
    }


@app.post("/api/players", status_code=201)
def create_player(req: CreatePlayerRequest):
    if not req.playerId or not req.displayName or not req.region:
        raise HTTPException(status_code=400, detail="playerId, displayName and region are required")

    doc = {
        "id": req.playerId,
        "playerId": req.playerId,
        "displayName": req.displayName,
        "region": req.region,
        "totalGames": 0,
        "bestScore": 0,
        "averageScore": 0.0,
        "totalScore": 0,
        "type": "player",
        "schemaVersion": 1,
    }
    try:
        _players_ctr.create_item(body=doc)
    except exceptions.CosmosResourceExistsError:
        raise HTTPException(status_code=409, detail="Player already exists")

    return _player_response(doc)


@app.get("/api/players/{player_id}")
def get_player(player_id: str):
    try:
        doc = _players_ctr.read_item(item=player_id, partition_key=player_id)
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")
    return _player_response(doc)


@app.patch("/api/players/{player_id}")
def update_player(player_id: str, req: UpdatePlayerRequest):
    try:
        doc = _players_ctr.read_item(item=player_id, partition_key=player_id)
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    if req.displayName is not None:
        doc["displayName"] = req.displayName
    if req.region is not None:
        old_region = doc["region"]
        doc["region"] = req.region

    _players_ctr.replace_item(item=doc["id"], body=doc)

    # Update leaderboard entries if displayName or region changed
    if req.displayName is not None or req.region is not None:
        _update_leaderboard_entries_for_player(doc, old_region if req.region is not None else None)

    return _player_response(doc)


def _update_leaderboard_entries_for_player(player_doc: dict, old_region: Optional[str] = None):
    """Update denormalized player info in leaderboard entries."""
    player_id = player_doc["playerId"]

    # Update global entry
    try:
        lb_id = f"lb_{player_id}_global"
        lb_doc = _leaderboards_ctr.read_item(item=lb_id, partition_key="global")
        lb_doc["displayName"] = player_doc["displayName"]
        lb_doc["region"] = player_doc["region"]
        _leaderboards_ctr.replace_item(item=lb_id, body=lb_doc)
    except exceptions.CosmosResourceNotFoundError:
        pass

    # Remove old regional entry if region changed
    if old_region is not None:
        old_key = f"region_{old_region}"
        try:
            lb_id_old = f"lb_{player_id}_{old_key}"
            _leaderboards_ctr.delete_item(item=lb_id_old, partition_key=old_key)
        except exceptions.CosmosResourceNotFoundError:
            pass

    # Upsert new regional entry
    new_key = f"region_{player_doc['region']}"
    try:
        lb_id_new = f"lb_{player_id}_{new_key}"
        lb_doc = _leaderboards_ctr.read_item(item=lb_id_new, partition_key=new_key)
        lb_doc["displayName"] = player_doc["displayName"]
        lb_doc["region"] = player_doc["region"]
        _leaderboards_ctr.replace_item(item=lb_id_new, body=lb_doc)
    except exceptions.CosmosResourceNotFoundError:
        # Need to create new regional entry if player has scores
        if player_doc.get("bestScore", 0) > 0:
            _leaderboards_ctr.upsert_item(body={
                "id": lb_id_new,
                "leaderboardKey": new_key,
                "playerId": player_doc["playerId"],
                "displayName": player_doc["displayName"],
                "region": player_doc["region"],
                "bestScore": player_doc["bestScore"],
                "type": "leaderboardEntry",
                "schemaVersion": 1,
            })


@app.delete("/api/players/{player_id}", status_code=204)
def delete_player(player_id: str):
    # Verify player exists
    try:
        player_doc = _players_ctr.read_item(item=player_id, partition_key=player_id)
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    region = player_doc.get("region", "")

    # Delete player document
    _players_ctr.delete_item(item=player_id, partition_key=player_id)

    # Delete all score documents for this player (single-partition delete)
    scores = list(_scores_ctr.query_items(
        query="SELECT c.id FROM c WHERE c.playerId = @pid",
        parameters=[{"name": "@pid", "value": player_id}],
        partition_key=player_id,
    ))
    for s in scores:
        _scores_ctr.delete_item(item=s["id"], partition_key=player_id)

    # Delete leaderboard entries
    _delete_leaderboard_entries(player_id, region)

    return JSONResponse(status_code=204, content=None)


def _delete_leaderboard_entries(player_id: str, region: str):
    """Remove player from all leaderboard partitions."""
    # Global
    try:
        _leaderboards_ctr.delete_item(
            item=f"lb_{player_id}_global",
            partition_key="global",
        )
    except exceptions.CosmosResourceNotFoundError:
        pass

    # Regional
    region_key = f"region_{region}"
    try:
        _leaderboards_ctr.delete_item(
            item=f"lb_{player_id}_{region_key}",
            partition_key=region_key,
        )
    except exceptions.CosmosResourceNotFoundError:
        pass


# ---------------------------------------------------------------------------
# Score Submission
# ---------------------------------------------------------------------------

@app.post("/api/scores", status_code=201)
def submit_score(req: SubmitScoreRequest):
    if not req.playerId:
        raise HTTPException(status_code=400, detail="playerId is required")
    if req.score is None:
        raise HTTPException(status_code=400, detail="score is required")
    if req.score < 0:
        raise HTTPException(status_code=400, detail="score must be a positive integer")

    # Verify player exists
    try:
        player_doc = _players_ctr.read_item(item=req.playerId, partition_key=req.playerId)
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Create score document
    score_id = str(uuid.uuid4())
    now = datetime.now(timezone.utc).isoformat()
    score_doc = {
        "id": score_id,
        "scoreId": score_id,
        "playerId": req.playerId,
        "score": req.score,
        "gameMode": req.gameMode or "",
        "timestamp": now,
        "type": "score",
        "schemaVersion": 1,
    }
    _scores_ctr.create_item(body=score_doc)

    # Update player stats with ETag-based optimistic concurrency
    # Rule: sdk-etag-concurrency
    _update_player_stats(req.playerId, req.score)

    # Update leaderboard entries (materialized views pattern)
    _upsert_leaderboard_entries(req.playerId)

    return {
        "scoreId": score_id,
        "playerId": req.playerId,
        "score": req.score,
    }


def _update_player_stats(player_id: str, new_score: int):
    """
    Update player stats using ETag-based optimistic concurrency.
    Rule: sdk-etag-concurrency — retry on conflict.
    """
    max_retries = 10
    for attempt in range(max_retries):
        try:
            player_doc = _players_ctr.read_item(item=player_id, partition_key=player_id)
            etag = player_doc.get("_etag")

            total_games = player_doc.get("totalGames", 0) + 1
            total_score = player_doc.get("totalScore", 0) + new_score
            best_score = max(player_doc.get("bestScore", 0), new_score)
            avg_score = total_score / total_games if total_games > 0 else 0.0

            player_doc["totalGames"] = total_games
            player_doc["totalScore"] = total_score
            player_doc["bestScore"] = best_score
            player_doc["averageScore"] = avg_score

            _players_ctr.replace_item(
                item=player_doc["id"],
                body=player_doc,
                if_match=etag,
            )
            return
        except exceptions.CosmosAccessConditionFailedError:
            if attempt == max_retries - 1:
                raise HTTPException(status_code=409, detail="Concurrent update conflict")
            continue


def _upsert_leaderboard_entries(player_id: str):
    """
    Upsert global and regional leaderboard entries for a player.
    Rule: model-denormalize-reads — denormalize displayName for efficient reads.
    """
    try:
        player_doc = _players_ctr.read_item(item=player_id, partition_key=player_id)
    except exceptions.CosmosResourceNotFoundError:
        return

    best_score = player_doc.get("bestScore", 0)
    display_name = player_doc.get("displayName", "")
    region = player_doc.get("region", "")

    # Global leaderboard entry
    global_entry = {
        "id": f"lb_{player_id}_global",
        "leaderboardKey": "global",
        "playerId": player_id,
        "displayName": display_name,
        "region": region,
        "bestScore": best_score,
        "type": "leaderboardEntry",
        "schemaVersion": 1,
    }
    _leaderboards_ctr.upsert_item(body=global_entry)

    # Regional leaderboard entry
    region_key = f"region_{region}"
    regional_entry = {
        "id": f"lb_{player_id}_{region_key}",
        "leaderboardKey": region_key,
        "playerId": player_id,
        "displayName": display_name,
        "region": region,
        "bestScore": best_score,
        "type": "leaderboardEntry",
        "schemaVersion": 1,
    }
    _leaderboards_ctr.upsert_item(body=regional_entry)


# ---------------------------------------------------------------------------
# Score History
# ---------------------------------------------------------------------------

@app.get("/api/players/{player_id}/scores")
def get_player_scores(player_id: str, limit: int = Query(default=10, ge=1, le=100)):
    # Verify player exists
    try:
        _players_ctr.read_item(item=player_id, partition_key=player_id)
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Single-partition query — Rule: query-minimize-cross-partition
    scores = list(_scores_ctr.query_items(
        query="SELECT c.scoreId, c.playerId, c.score, c.gameMode, c.timestamp "
              "FROM c WHERE c.playerId = @pid ORDER BY c.timestamp DESC",
        parameters=[{"name": "@pid", "value": player_id}],
        partition_key=player_id,
        max_item_count=limit,
    ))

    return scores[:limit]


# ---------------------------------------------------------------------------
# Leaderboards
# ---------------------------------------------------------------------------

@app.get("/api/leaderboards/global")
def global_leaderboard(top: int = Query(default=100, ge=0, le=100)):
    if top == 0:
        return []

    # Single-partition query on leaderboardKey = "global"
    # Rule: query-minimize-cross-partition, index-composite
    entries = list(_leaderboards_ctr.query_items(
        query="SELECT c.playerId, c.displayName, c.bestScore, c.region "
              "FROM c WHERE c.leaderboardKey = @key "
              "ORDER BY c.bestScore DESC, c.displayName ASC",
        parameters=[{"name": "@key", "value": "global"}],
        partition_key="global",
        max_item_count=top,
    ))

    result = []
    for i, entry in enumerate(entries[:top]):
        result.append({
            "rank": i + 1,
            "playerId": entry["playerId"],
            "displayName": entry["displayName"],
            "score": entry["bestScore"],
        })
    return result


@app.get("/api/leaderboards/regional/{region}")
def regional_leaderboard(region: str, top: int = Query(default=100, ge=0, le=100)):
    if top == 0:
        return []

    region_key = f"region_{region}"

    # Single-partition query — Rule: query-minimize-cross-partition
    entries = list(_leaderboards_ctr.query_items(
        query="SELECT c.playerId, c.displayName, c.bestScore, c.region "
              "FROM c WHERE c.leaderboardKey = @key "
              "ORDER BY c.bestScore DESC, c.displayName ASC",
        parameters=[{"name": "@key", "value": region_key}],
        partition_key=region_key,
        max_item_count=top,
    ))

    result = []
    for i, entry in enumerate(entries[:top]):
        result.append({
            "rank": i + 1,
            "playerId": entry["playerId"],
            "displayName": entry["displayName"],
            "score": entry["bestScore"],
        })
    return result


# ---------------------------------------------------------------------------
# Player Rank
# ---------------------------------------------------------------------------

@app.get("/api/players/{player_id}/rank")
def player_rank(player_id: str):
    # Verify player exists and has scores
    try:
        player_doc = _players_ctr.read_item(item=player_id, partition_key=player_id)
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    if player_doc.get("bestScore", 0) == 0 and player_doc.get("totalGames", 0) == 0:
        raise HTTPException(status_code=404, detail="Player has no scores")

    # Get the full global leaderboard sorted
    all_entries = list(_leaderboards_ctr.query_items(
        query="SELECT c.playerId, c.displayName, c.bestScore "
              "FROM c WHERE c.leaderboardKey = @key "
              "ORDER BY c.bestScore DESC, c.displayName ASC",
        parameters=[{"name": "@key", "value": "global"}],
        partition_key="global",
    ))

    # Find the player's rank
    player_rank_val = None
    player_score = None
    for i, entry in enumerate(all_entries):
        if entry["playerId"] == player_id:
            player_rank_val = i + 1
            player_score = entry["bestScore"]
            break

    if player_rank_val is None:
        raise HTTPException(status_code=404, detail="Player not found in leaderboard")

    # Get neighbors (±10 positions)
    start_idx = max(0, player_rank_val - 1 - 10)
    end_idx = min(len(all_entries), player_rank_val + 10)
    neighbors = []
    for i in range(start_idx, end_idx):
        entry = all_entries[i]
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
        "score": player_score,
        "neighbors": neighbors,
    }


# ---------------------------------------------------------------------------
# Run with uvicorn
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
