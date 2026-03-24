"""
Gaming Leaderboard API — FastAPI + Azure Cosmos DB (NoSQL API)

Design:
  - Container "players"     (PK: /playerId)  — player profiles with running stats
  - Container "scores"      (PK: /playerId)  — individual score records
  - Container "leaderboard" (PK: /leaderboardKey) — materialized view for
        efficient top-N and regional queries (avoids cross-partition fan-out)

Best-practice highlights applied:
  • Async SDK (azure.cosmos.aio) with aiohttp transport
  • Singleton CosmosClient reused for the app lifetime
  • Gateway mode + SSL verification disabled for emulator compatibility
  • Parameterized queries with literal TOP integers
  • Composite index (score DESC, displayName ASC) on leaderboard container
  • ETag-based optimistic concurrency for player stat updates
  • Type discriminator and schema-version fields on every document
  • Denormalized leaderboard entries (materialized view pattern)
"""

import os
import uuid
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from typing import List, Optional

import urllib3
from azure.core import MatchConditions
from azure.cosmos import PartitionKey, exceptions
from azure.cosmos.aio import CosmosClient
from fastapi import FastAPI, HTTPException, Query, Response
from pydantic import BaseModel

# Suppress SSL warnings for emulator
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

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
# Singleton Cosmos DB references (initialized at startup)
# ---------------------------------------------------------------------------
cosmos_client: Optional[CosmosClient] = None
players_container = None
scores_container = None
leaderboard_container = None


async def _init_cosmos() -> None:
    global cosmos_client, players_container, scores_container, leaderboard_container

    cosmos_client = CosmosClient(
        url=COSMOS_ENDPOINT,
        credential=COSMOS_KEY,
        connection_verify=False,  # emulator self-signed cert
    )

    database = await cosmos_client.create_database_if_not_exists(DATABASE_NAME)

    # Players container — point reads by playerId
    players_container = await database.create_container_if_not_exists(
        id="players",
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy={
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [{"path": '/"_etag"/?'}],
        },
    )

    # Scores container — score history per player
    scores_container = await database.create_container_if_not_exists(
        id="scores",
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy={
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [{"path": '/"_etag"/?'}],
        },
    )

    # Leaderboard container — materialized view with composite index
    leaderboard_container = await database.create_container_if_not_exists(
        id="leaderboard",
        partition_key=PartitionKey(path="/leaderboardKey"),
        indexing_policy={
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [{"path": '/"_etag"/?'}],
            "compositeIndexes": [
                [
                    {"path": "/score", "order": "descending"},
                    {"path": "/displayName", "order": "ascending"},
                ]
            ],
        },
    )


async def _close_cosmos() -> None:
    global cosmos_client
    if cosmos_client:
        await cosmos_client.close()


@asynccontextmanager
async def lifespan(_app: FastAPI):
    await _init_cosmos()
    yield
    await _close_cosmos()


app = FastAPI(lifespan=lifespan)

# ---------------------------------------------------------------------------
# Request / response helpers
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


def _format_player(doc: dict) -> dict:
    """Return only the public fields expected by the API contract."""
    return {
        "playerId": doc["playerId"],
        "displayName": doc["displayName"],
        "region": doc["region"],
        "totalGames": doc["totalGames"],
        "bestScore": doc["bestScore"],
        "averageScore": doc["averageScore"],
    }


# ---------------------------------------------------------------------------
# Health
# ---------------------------------------------------------------------------


@app.get("/health")
async def health():
    return {"status": "ok"}


# ---------------------------------------------------------------------------
# Player CRUD
# ---------------------------------------------------------------------------


@app.post("/api/players", status_code=201)
async def create_player(req: CreatePlayerRequest):
    player = {
        "id": req.playerId,
        "playerId": req.playerId,
        "displayName": req.displayName,
        "region": req.region,
        "totalGames": 0,
        "bestScore": 0,
        "averageScore": 0,
        "totalScoreSum": 0,
        "type": "player",
        "schemaVersion": 1,
    }
    try:
        await players_container.create_item(body=player)
    except exceptions.CosmosResourceExistsError:
        raise HTTPException(status_code=409, detail="Player already exists")
    return _format_player(player)


@app.get("/api/players/{player_id}")
async def get_player(player_id: str):
    try:
        player = await players_container.read_item(
            item=player_id, partition_key=player_id
        )
        return _format_player(player)
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")


@app.patch("/api/players/{player_id}")
async def update_player(player_id: str, req: UpdatePlayerRequest):
    try:
        player = await players_container.read_item(
            item=player_id, partition_key=player_id
        )
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    old_region = player["region"]
    if req.displayName is not None:
        player["displayName"] = req.displayName
    if req.region is not None:
        player["region"] = req.region

    await players_container.replace_item(
        item=player_id, body=player, partition_key=player_id
    )

    # Keep leaderboard materialized view in sync
    if player["totalGames"] > 0:
        await _sync_leaderboard_after_update(player, old_region, req)

    return _format_player(player)


async def _sync_leaderboard_after_update(
    player: dict, old_region: str, req: UpdatePlayerRequest
) -> None:
    player_id = player["playerId"]

    # Update global leaderboard entry
    try:
        lb = await leaderboard_container.read_item(
            item=player_id, partition_key="global"
        )
        if req.displayName is not None:
            lb["displayName"] = req.displayName
        if req.region is not None:
            lb["region"] = req.region
        await leaderboard_container.replace_item(
            item=player_id, body=lb, partition_key="global"
        )
    except exceptions.CosmosResourceNotFoundError:
        pass

    if req.region is not None and req.region != old_region:
        # Remove old regional entry
        try:
            await leaderboard_container.delete_item(
                item=player_id, partition_key=old_region
            )
        except exceptions.CosmosResourceNotFoundError:
            pass
        # Create new regional entry
        await leaderboard_container.upsert_item(
            body={
                "id": player_id,
                "leaderboardKey": req.region,
                "playerId": player_id,
                "displayName": player["displayName"],
                "region": req.region,
                "score": player["bestScore"],
                "type": "leaderboardEntry",
                "schemaVersion": 1,
            }
        )
    elif req.displayName is not None:
        # Only display-name changed — update regional entry in place
        region = player["region"]
        try:
            lb = await leaderboard_container.read_item(
                item=player_id, partition_key=region
            )
            lb["displayName"] = req.displayName
            await leaderboard_container.replace_item(
                item=player_id, body=lb, partition_key=region
            )
        except exceptions.CosmosResourceNotFoundError:
            pass


@app.delete("/api/players/{player_id}")
async def delete_player(player_id: str):
    try:
        player = await players_container.read_item(
            item=player_id, partition_key=player_id
        )
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    region = player["region"]

    # Delete all score documents for this player
    score_ids: List[str] = []
    async for item in scores_container.query_items(
        query="SELECT c.id FROM c WHERE c.playerId = @pid",
        parameters=[{"name": "@pid", "value": player_id}],
        partition_key=player_id,
    ):
        score_ids.append(item["id"])

    for sid in score_ids:
        await scores_container.delete_item(item=sid, partition_key=player_id)

    # Delete leaderboard entries (global + regional)
    for pk in ("global", region):
        try:
            await leaderboard_container.delete_item(item=player_id, partition_key=pk)
        except exceptions.CosmosResourceNotFoundError:
            pass

    # Delete player document
    await players_container.delete_item(item=player_id, partition_key=player_id)
    return Response(status_code=204)


# ---------------------------------------------------------------------------
# Score submission
# ---------------------------------------------------------------------------


@app.post("/api/scores", status_code=201)
async def submit_score(req: SubmitScoreRequest):
    if req.score < 0:
        raise HTTPException(status_code=400, detail="Score must be non-negative")

    # Verify player exists
    try:
        await players_container.read_item(
            item=req.playerId, partition_key=req.playerId
        )
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Persist score record
    score_id = str(uuid.uuid4())
    timestamp = datetime.now(timezone.utc).isoformat()
    score_doc: dict = {
        "id": score_id,
        "scoreId": score_id,
        "playerId": req.playerId,
        "score": req.score,
        "timestamp": timestamp,
        "type": "score",
        "schemaVersion": 1,
    }
    if req.gameMode is not None:
        score_doc["gameMode"] = req.gameMode

    await scores_container.create_item(body=score_doc)

    # Update player stats with optimistic concurrency (ETag retry loop)
    is_new_best = False
    while True:
        player = await players_container.read_item(
            item=req.playerId, partition_key=req.playerId
        )
        etag = player.get("_etag")

        player["totalGames"] += 1
        running_sum = player.get("totalScoreSum", 0) + req.score
        player["totalScoreSum"] = running_sum
        player["averageScore"] = running_sum / player["totalGames"]

        is_new_best = req.score > player["bestScore"]
        if is_new_best:
            player["bestScore"] = req.score

        try:
            await players_container.replace_item(
                item=req.playerId,
                body=player,
                partition_key=req.playerId,
                etag=etag,
                match_condition=MatchConditions.IfNotModified,
            )
            break
        except exceptions.CosmosHttpResponseError as exc:
            if exc.status_code == 412:
                continue  # ETag mismatch — retry with fresh read
            raise

    # Upsert leaderboard entries when best score changes (or first score)
    if is_new_best or player["totalGames"] == 1:
        for lb_key in ("global", player["region"]):
            await leaderboard_container.upsert_item(
                body={
                    "id": req.playerId,
                    "leaderboardKey": lb_key,
                    "playerId": req.playerId,
                    "displayName": player["displayName"],
                    "region": player["region"],
                    "score": player["bestScore"],
                    "type": "leaderboardEntry",
                    "schemaVersion": 1,
                }
            )

    return {"scoreId": score_id, "playerId": req.playerId, "score": req.score}


# ---------------------------------------------------------------------------
# Score history
# ---------------------------------------------------------------------------


@app.get("/api/players/{player_id}/scores")
async def get_player_scores(
    player_id: str, limit: int = Query(default=10, ge=1, le=100)
):
    try:
        await players_container.read_item(
            item=player_id, partition_key=player_id
        )
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    safe_limit = max(1, min(int(limit), 100))
    query = (
        f"SELECT TOP {safe_limit} c.scoreId, c.playerId, c.score, "
        f"c.gameMode, c.timestamp "
        f"FROM c WHERE c.playerId = @pid "
        f"ORDER BY c.timestamp DESC"
    )

    results: List[dict] = []
    async for item in scores_container.query_items(
        query=query,
        parameters=[{"name": "@pid", "value": player_id}],
        partition_key=player_id,
    ):
        entry: dict = {
            "scoreId": item["scoreId"],
            "playerId": item["playerId"],
            "score": item["score"],
            "timestamp": item["timestamp"],
        }
        if item.get("gameMode") is not None:
            entry["gameMode"] = item["gameMode"]
        results.append(entry)

    return results


# ---------------------------------------------------------------------------
# Leaderboards
# ---------------------------------------------------------------------------


@app.get("/api/leaderboards/global")
async def global_leaderboard(top: int = Query(default=100, ge=0, le=100)):
    if top == 0:
        return []

    safe_top = max(1, min(int(top), 100))
    query = (
        f"SELECT TOP {safe_top} c.playerId, c.displayName, c.score "
        f"FROM c WHERE c.leaderboardKey = 'global' "
        f"ORDER BY c.score DESC, c.displayName ASC"
    )

    entries: List[dict] = []
    rank = 1
    async for item in leaderboard_container.query_items(
        query=query, partition_key="global"
    ):
        entries.append(
            {
                "rank": rank,
                "playerId": item["playerId"],
                "displayName": item["displayName"],
                "score": item["score"],
            }
        )
        rank += 1
    return entries


@app.get("/api/leaderboards/regional/{region}")
async def regional_leaderboard(
    region: str, top: int = Query(default=100, ge=0, le=100)
):
    if top == 0:
        return []

    safe_top = max(1, min(int(top), 100))
    query = (
        f"SELECT TOP {safe_top} c.playerId, c.displayName, c.score "
        f"FROM c WHERE c.leaderboardKey = @region "
        f"ORDER BY c.score DESC, c.displayName ASC"
    )

    entries: List[dict] = []
    rank = 1
    async for item in leaderboard_container.query_items(
        query=query,
        parameters=[{"name": "@region", "value": region}],
        partition_key=region,
    ):
        entries.append(
            {
                "rank": rank,
                "playerId": item["playerId"],
                "displayName": item["displayName"],
                "score": item["score"],
            }
        )
        rank += 1
    return entries


# ---------------------------------------------------------------------------
# Player rank
# ---------------------------------------------------------------------------


@app.get("/api/players/{player_id}/rank")
async def player_rank(player_id: str):
    # Verify player exists and has scores
    try:
        player = await players_container.read_item(
            item=player_id, partition_key=player_id
        )
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    if player["totalGames"] == 0:
        raise HTTPException(status_code=404, detail="Player has no scores")

    best_score = player["bestScore"]
    display_name = player["displayName"]

    # COUNT-based rank: count players with a strictly better leaderboard position
    count_query = (
        "SELECT VALUE COUNT(1) FROM c "
        "WHERE c.leaderboardKey = 'global' "
        "AND (c.score > @score "
        "     OR (c.score = @score AND c.displayName < @name))"
    )
    rank_value = 1
    async for val in leaderboard_container.query_items(
        query=count_query,
        parameters=[
            {"name": "@score", "value": best_score},
            {"name": "@name", "value": display_name},
        ],
        partition_key="global",
    ):
        rank_value = val + 1

    # Fetch full sorted leaderboard for neighbor window (±10)
    all_entries: List[dict] = []
    async for item in leaderboard_container.query_items(
        query=(
            "SELECT c.playerId, c.displayName, c.score "
            "FROM c WHERE c.leaderboardKey = 'global' "
            "ORDER BY c.score DESC, c.displayName ASC"
        ),
        partition_key="global",
    ):
        all_entries.append(item)

    # Locate player index
    player_index = -1
    for i, e in enumerate(all_entries):
        if e["playerId"] == player_id:
            player_index = i
            break

    if player_index == -1:
        raise HTTPException(status_code=404, detail="Player not in leaderboard")

    # Build neighbors list (±10, excluding the player themselves)
    start = max(0, player_index - 10)
    end = min(len(all_entries), player_index + 11)
    neighbors: List[dict] = []
    for i in range(start, end):
        if i == player_index:
            continue
        neighbors.append(
            {
                "rank": i + 1,
                "playerId": all_entries[i]["playerId"],
                "displayName": all_entries[i]["displayName"],
                "score": all_entries[i]["score"],
            }
        )

    return {
        "playerId": player_id,
        "rank": rank_value,
        "score": best_score,
        "neighbors": neighbors,
    }
