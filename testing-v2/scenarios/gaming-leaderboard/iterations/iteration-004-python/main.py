import os
import uuid
from datetime import datetime, timezone
from typing import Optional

from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.responses import JSONResponse
from azure.cosmos.aio import CosmosClient
from azure.cosmos import PartitionKey
from azure.cosmos.exceptions import CosmosResourceNotFoundError, CosmosHttpResponseError
import urllib3

# Suppress SSL warnings for emulator (development only)
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

app = FastAPI(title="Gaming Leaderboard API")

MAX_ETAG_RETRIES = 10

# ── Configuration ──────────────────────────────────────────────────────────────
COSMOS_ENDPOINT = os.environ.get("COSMOS_ENDPOINT", "https://localhost:8081")
COSMOS_KEY = os.environ.get(
    "COSMOS_KEY",
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==",
)
DATABASE_NAME = "gaming-leaderboard"

# ── Singleton Cosmos client ────────────────────────────────────────────────────
# Reuse one CosmosClient for the lifetime of the process (SDK best practice 4.18)
cosmos_client: Optional[CosmosClient] = None
database = None
players_container = None
scores_container = None


async def get_cosmos_client() -> CosmosClient:
    global cosmos_client
    if cosmos_client is None:
        cosmos_client = CosmosClient(
            url=COSMOS_ENDPOINT,
            credential=COSMOS_KEY,
            connection_verify=False,  # Emulator uses self-signed cert (SDK rule 4.6)
        )
    return cosmos_client


async def ensure_containers():
    """Create database and containers if they don't exist."""
    global database, players_container, scores_container
    client = await get_cosmos_client()
    database = await client.create_database_if_not_exists(id=DATABASE_NAME)

    # Players container – partition on playerId (high cardinality, point-read friendly)
    players_container = await database.create_container_if_not_exists(
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
                ]
            ],
        },
    )

    # Scores container – partition on playerId (aligns with query patterns)
    scores_container = await database.create_container_if_not_exists(
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


@app.on_event("startup")
async def startup():
    await ensure_containers()


@app.on_event("shutdown")
async def shutdown():
    global cosmos_client
    if cosmos_client:
        await cosmos_client.close()
        cosmos_client = None


# ── Health ─────────────────────────────────────────────────────────────────────
@app.get("/health")
async def health():
    return {"status": "healthy"}


# ── Helper: format player response ────────────────────────────────────────────
def _player_response(doc: dict) -> dict:
    return {
        "playerId": doc["playerId"],
        "displayName": doc["displayName"],
        "region": doc["region"],
        "totalGames": doc.get("totalGames", 0),
        "bestScore": doc.get("bestScore", 0),
        "averageScore": doc.get("averageScore", 0),
    }


# ── Player Management ─────────────────────────────────────────────────────────
@app.post("/api/players", status_code=201)
async def create_player(body: dict):
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
        "averageScore": 0,
        "totalScore": 0,
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
    try:
        doc = await players_container.read_item(item=player_id, partition_key=player_id)
        return _player_response(doc)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")


@app.patch("/api/players/{player_id}")
async def update_player(player_id: str, body: dict):
    try:
        doc = await players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    if "displayName" in body:
        doc["displayName"] = body["displayName"]
    if "region" in body:
        doc["region"] = body["region"]

    replaced = await players_container.replace_item(item=doc["id"], body=doc)
    return _player_response(replaced)


@app.delete("/api/players/{player_id}", status_code=204)
async def delete_player(player_id: str):
    try:
        await players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Delete all scores for this player (single-partition query, rule 3.1)
    query = "SELECT c.id FROM c WHERE c.playerId = @playerId"
    params = [{"name": "@playerId", "value": player_id}]
    score_ids = []
    async for item in scores_container.query_items(
        query=query, parameters=params, partition_key=player_id
    ):
        score_ids.append(item["id"])

    for sid in score_ids:
        await scores_container.delete_item(item=sid, partition_key=player_id)

    await players_container.delete_item(item=player_id, partition_key=player_id)
    return JSONResponse(status_code=204, content=None)


# ── Score Submission ───────────────────────────────────────────────────────────
@app.post("/api/scores", status_code=201)
async def submit_score(body: dict):
    player_id = body.get("playerId")
    score = body.get("score")

    if not player_id or score is None:
        raise HTTPException(status_code=400, detail="playerId and score are required")

    if not isinstance(score, (int, float)) or score < 0:
        raise HTTPException(status_code=400, detail="score must be a non-negative integer")

    score_id = str(uuid.uuid4())
    game_mode = body.get("gameMode")
    timestamp = datetime.now(timezone.utc).isoformat()

    score_doc = {
        "id": score_id,
        "scoreId": score_id,
        "playerId": player_id,
        "score": score,
        "timestamp": timestamp,
    }
    if game_mode is not None:
        score_doc["gameMode"] = game_mode

    # Optimistic concurrency loop for player stats update (SDK rule 4.7)
    for attempt in range(MAX_ETAG_RETRIES):
        try:
            player_doc = await players_container.read_item(
                item=player_id, partition_key=player_id
            )
        except CosmosResourceNotFoundError:
            raise HTTPException(status_code=404, detail="Player not found")

        etag = player_doc.get("_etag")

        total_games = player_doc.get("totalGames", 0) + 1
        total_score = player_doc.get("totalScore", 0) + score
        best_score = max(player_doc.get("bestScore", 0), score)
        average_score = total_score / total_games

        player_doc["totalGames"] = total_games
        player_doc["totalScore"] = total_score
        player_doc["bestScore"] = best_score
        player_doc["averageScore"] = average_score

        try:
            await players_container.replace_item(
                item=player_doc["id"],
                body=player_doc,
                if_match=etag,
            )
            break
        except CosmosHttpResponseError as e:
            if e.status_code == 412 and attempt < MAX_ETAG_RETRIES - 1:
                continue
            raise
    else:
        raise HTTPException(status_code=409, detail="Concurrent update conflict")

    # Create score doc after player update succeeds
    await scores_container.create_item(body=score_doc)

    return {
        "scoreId": score_id,
        "playerId": player_id,
        "score": score,
    }


# ── Score History ──────────────────────────────────────────────────────────────
@app.get("/api/players/{player_id}/scores")
async def get_player_scores(player_id: str, limit: int = Query(default=10, ge=1, le=100)):
    try:
        await players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Use literal integer for TOP (rule 3.6), parameterized for other values (rule 3.5)
    top = int(limit)
    query = f"SELECT TOP {top} c.scoreId, c.playerId, c.score, c.gameMode, c.timestamp FROM c WHERE c.playerId = @playerId ORDER BY c.timestamp DESC"
    params = [{"name": "@playerId", "value": player_id}]

    results = []
    async for item in scores_container.query_items(
        query=query, parameters=params, partition_key=player_id
    ):
        results.append(item)

    return results


# ── Leaderboards ───────────────────────────────────────────────────────────────
@app.get("/api/leaderboards/global")
async def global_leaderboard(top: int = Query(default=100, ge=0, le=100)):
    if top == 0:
        return []

    # Use literal integer for TOP (rule 3.6)
    # Composite index on bestScore DESC, displayName ASC for tiebreaking
    top_val = int(top)
    query = f"SELECT TOP {top_val} c.playerId, c.displayName, c.bestScore FROM c ORDER BY c.bestScore DESC, c.displayName ASC"

    results = []
    async for item in players_container.query_items(
        query=query, enable_cross_partition_query=True
    ):
        results.append(item)

    leaderboard = []
    for i, item in enumerate(results, start=1):
        leaderboard.append(
            {
                "rank": i,
                "playerId": item["playerId"],
                "displayName": item["displayName"],
                "score": item["bestScore"],
            }
        )

    return leaderboard


@app.get("/api/leaderboards/regional/{region}")
async def regional_leaderboard(region: str, top: int = Query(default=100, ge=0, le=100)):
    if top == 0:
        return []

    top_val = int(top)
    # Parameterized region filter (rule 3.5), literal TOP (rule 3.6)
    query = f"SELECT TOP {top_val} c.playerId, c.displayName, c.bestScore FROM c WHERE c.region = @region ORDER BY c.bestScore DESC, c.displayName ASC"
    params = [{"name": "@region", "value": region}]

    results = []
    async for item in players_container.query_items(
        query=query, parameters=params, enable_cross_partition_query=True
    ):
        results.append(item)

    leaderboard = []
    for i, item in enumerate(results, start=1):
        leaderboard.append(
            {
                "rank": i,
                "playerId": item["playerId"],
                "displayName": item["displayName"],
                "score": item["bestScore"],
            }
        )

    return leaderboard


# ── Player Rank ────────────────────────────────────────────────────────────────
@app.get("/api/players/{player_id}/rank")
async def player_rank(player_id: str):
    try:
        player_doc = await players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    if player_doc.get("bestScore", 0) == 0 and player_doc.get("totalGames", 0) == 0:
        raise HTTPException(status_code=404, detail="Player has no scores")

    # Get all players sorted by bestScore DESC, displayName ASC for ranking
    query = "SELECT c.playerId, c.displayName, c.bestScore FROM c ORDER BY c.bestScore DESC, c.displayName ASC"
    all_players = []
    async for item in players_container.query_items(
        query=query, enable_cross_partition_query=True
    ):
        all_players.append(item)

    player_rank_idx = None
    for i, p in enumerate(all_players):
        if p["playerId"] == player_id:
            player_rank_idx = i
            break

    if player_rank_idx is None:
        raise HTTPException(status_code=404, detail="Player not found in rankings")

    rank = player_rank_idx + 1  # 1-based
    player_score = all_players[player_rank_idx]["bestScore"]

    # Get neighbors (±10 positions)
    start = max(0, player_rank_idx - 10)
    end = min(len(all_players), player_rank_idx + 11)

    neighbors = []
    for i in range(start, end):
        if i == player_rank_idx:
            continue
        neighbors.append(
            {
                "rank": i + 1,
                "playerId": all_players[i]["playerId"],
                "displayName": all_players[i]["displayName"],
                "score": all_players[i]["bestScore"],
            }
        )

    return {
        "playerId": player_id,
        "rank": rank,
        "score": player_score,
        "neighbors": neighbors,
    }
