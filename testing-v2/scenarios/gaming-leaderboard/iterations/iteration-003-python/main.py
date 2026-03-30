import os
import uuid
from datetime import datetime, timezone
from typing import Optional

from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from azure.cosmos.aio import CosmosClient
from azure.cosmos import PartitionKey
from azure.cosmos.exceptions import CosmosResourceNotFoundError, CosmosHttpResponseError

app = FastAPI()


@app.exception_handler(Exception)
async def general_exception_handler(request: Request, exc: Exception):
    if isinstance(exc, HTTPException):
        raise exc
    if isinstance(exc, CosmosHttpResponseError):
        if exc.status_code == 404:
            raise HTTPException(status_code=404, detail="Resource not found")
        if exc.status_code == 409:
            raise HTTPException(status_code=409, detail="Conflict")
    return JSONResponse(status_code=500, content={"detail": "Internal server error"})

COSMOS_ENDPOINT = os.environ.get("COSMOS_ENDPOINT", "https://localhost:8081")
COSMOS_KEY = os.environ.get(
    "COSMOS_KEY",
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==",
)
DATABASE_NAME = "gaming-leaderboard"

PLAYERS_CONTAINER = "players"
SCORES_CONTAINER = "scores"

MAX_RETRIES = 10

cosmos_client: Optional[CosmosClient] = None
database = None
players_container = None
scores_container = None


class CreatePlayerRequest(BaseModel):
    playerId: str
    displayName: str
    region: str


class UpdatePlayerRequest(BaseModel):
    displayName: Optional[str] = None
    region: Optional[str] = None


class SubmitScoreRequest(BaseModel):
    playerId: str
    score: int = Field(ge=0)
    gameMode: Optional[str] = None


async def get_cosmos_client() -> CosmosClient:
    global cosmos_client
    if cosmos_client is None:
        cosmos_client = CosmosClient(
            url=COSMOS_ENDPOINT,
            credential=COSMOS_KEY,
            connection_verify=False,
        )
    return cosmos_client


async def init_database():
    global database, players_container, scores_container
    client = await get_cosmos_client()

    database = await client.create_database_if_not_exists(id=DATABASE_NAME)

    players_container = await database.create_container_if_not_exists(
        id=PLAYERS_CONTAINER,
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy={
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

    scores_container = await database.create_container_if_not_exists(
        id=SCORES_CONTAINER,
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy={
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
    await init_database()


@app.on_event("shutdown")
async def shutdown():
    global cosmos_client
    if cosmos_client is not None:
        await cosmos_client.close()
        cosmos_client = None


def player_response(doc: dict) -> dict:
    return {
        "playerId": doc["playerId"],
        "displayName": doc["displayName"],
        "region": doc["region"],
        "totalGames": doc.get("totalGames", 0),
        "bestScore": doc.get("bestScore", 0),
        "averageScore": doc.get("averageScore", 0),
    }


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.post("/api/players", status_code=201)
async def create_player(req: CreatePlayerRequest):
    player = {
        "id": req.playerId,
        "playerId": req.playerId,
        "displayName": req.displayName,
        "region": req.region,
        "totalGames": 0,
        "bestScore": 0,
        "averageScore": 0.0,
    }
    try:
        await players_container.create_item(body=player)
    except CosmosHttpResponseError as e:
        if e.status_code == 409:
            raise HTTPException(status_code=409, detail="Player already exists")
        raise
    return player_response(player)


@app.get("/api/players/{player_id}")
async def get_player(player_id: str):
    try:
        doc = await players_container.read_item(item=player_id, partition_key=player_id)
        return player_response(doc)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")


@app.patch("/api/players/{player_id}")
async def update_player(player_id: str, req: UpdatePlayerRequest):
    try:
        doc = await players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    if req.displayName is not None:
        doc["displayName"] = req.displayName
    if req.region is not None:
        doc["region"] = req.region

    replaced = await players_container.replace_item(item=doc["id"], body=doc)
    return player_response(replaced)


@app.delete("/api/players/{player_id}", status_code=204)
async def delete_player(player_id: str):
    try:
        await players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Delete all scores for this player
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


@app.post("/api/scores", status_code=201)
async def submit_score(req: SubmitScoreRequest):
    # Verify player exists via point read
    try:
        player_doc = await players_container.read_item(
            item=req.playerId, partition_key=req.playerId
        )
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
    }
    if req.gameMode is not None:
        score_doc["gameMode"] = req.gameMode

    await scores_container.create_item(body=score_doc)

    # Update player stats with ETag-based optimistic concurrency
    for _ in range(MAX_RETRIES):
        total_games = player_doc.get("totalGames", 0) + 1
        best_score = max(player_doc.get("bestScore", 0), req.score)
        prev_avg = player_doc.get("averageScore", 0.0)
        prev_total = player_doc.get("totalGames", 0)
        new_avg = ((prev_avg * prev_total) + req.score) / total_games

        player_doc["totalGames"] = total_games
        player_doc["bestScore"] = best_score
        player_doc["averageScore"] = new_avg

        try:
            await players_container.replace_item(
                item=player_doc["id"],
                body=player_doc,
                if_match=player_doc.get("_etag"),
            )
            break
        except CosmosHttpResponseError as e:
            if e.status_code == 412:
                # ETag mismatch - re-read and retry
                player_doc = await players_container.read_item(
                    item=req.playerId, partition_key=req.playerId
                )
                continue
            raise

    return {
        "scoreId": score_id,
        "playerId": req.playerId,
        "score": req.score,
    }


@app.get("/api/players/{player_id}/scores")
async def get_player_scores(player_id: str, limit: int = Query(default=10, ge=1, le=100)):
    # Verify player exists
    try:
        await players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    query = (
        "SELECT c.scoreId, c.playerId, c.score, c.gameMode, c.timestamp "
        "FROM c WHERE c.playerId = @playerId "
        "ORDER BY c.timestamp DESC "
        f"OFFSET 0 LIMIT {int(limit)}"
    )
    params = [{"name": "@playerId", "value": player_id}]

    results = []
    async for item in scores_container.query_items(
        query=query, parameters=params, partition_key=player_id
    ):
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


@app.get("/api/leaderboards/global")
async def global_leaderboard(top: int = Query(default=100, ge=0, le=100)):
    if top == 0:
        return []
    query = (
        "SELECT c.playerId, c.displayName, c.bestScore "
        "FROM c WHERE c.bestScore > 0 "
        "ORDER BY c.bestScore DESC, c.displayName ASC "
        f"OFFSET 0 LIMIT {int(top)}"
    )

    results = []
    rank = 1
    async for item in players_container.query_items(
        query=query, enable_cross_partition_query=True
    ):
        results.append(
            {
                "rank": rank,
                "playerId": item["playerId"],
                "displayName": item["displayName"],
                "score": item["bestScore"],
            }
        )
        rank += 1

    return results


@app.get("/api/leaderboards/regional/{region}")
async def regional_leaderboard(region: str, top: int = Query(default=100, ge=0, le=100)):
    if top == 0:
        return []
    query = (
        "SELECT c.playerId, c.displayName, c.bestScore "
        "FROM c WHERE c.region = @region AND c.bestScore > 0 "
        "ORDER BY c.bestScore DESC, c.displayName ASC "
        f"OFFSET 0 LIMIT {int(top)}"
    )
    params = [{"name": "@region", "value": region}]

    results = []
    rank = 1
    async for item in players_container.query_items(
        query=query, parameters=params, enable_cross_partition_query=True
    ):
        results.append(
            {
                "rank": rank,
                "playerId": item["playerId"],
                "displayName": item["displayName"],
                "score": item["bestScore"],
            }
        )
        rank += 1

    return results


@app.get("/api/players/{player_id}/rank")
async def get_player_rank(player_id: str):
    # Get player info via point read
    try:
        player_doc = await players_container.read_item(
            item=player_id, partition_key=player_id
        )
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    player_best = player_doc.get("bestScore", 0)
    if player_best == 0:
        raise HTTPException(status_code=404, detail="Player has no scores")

    # Build full leaderboard to determine rank and neighbors
    all_query = (
        "SELECT c.playerId, c.displayName, c.bestScore "
        "FROM c WHERE c.bestScore > 0 "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )

    all_players = []
    async for item in players_container.query_items(
        query=all_query, enable_cross_partition_query=True
    ):
        all_players.append(item)

    # Find player position
    player_index = -1
    for i, p in enumerate(all_players):
        if p["playerId"] == player_id:
            player_index = i
            break

    if player_index == -1:
        raise HTTPException(status_code=404, detail="Player not found in leaderboard")

    player_rank = player_index + 1

    # Get neighbors: ±10 positions
    start = max(0, player_index - 10)
    end = min(len(all_players), player_index + 11)

    neighbors = []
    for i in range(start, end):
        if i == player_index:
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
        "rank": player_rank,
        "score": player_best,
        "neighbors": neighbors,
    }
