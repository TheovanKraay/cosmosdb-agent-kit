"""
Gaming Leaderboard API - FastAPI application using Azure Cosmos DB (NoSQL API).

Best practices applied:
- Async SDK with aiohttp (Rule 4.1, 4.15)
- Singleton CosmosClient (Rule 4.18)
- Partition key aligned with query patterns (Rule 2.7)
- Point reads for known ID + partition key (Rule 3.7)
- Parameterized queries (Rule 3.6)
- Denormalized player stats for read-heavy workload (Rule 1.2)
- Composite indexes for ORDER BY (Rule 5.1, 5.2)
- Included root path /* in indexing policy
- Composite indexes have at least 2 paths
"""

import os
import uuid
from datetime import datetime, timezone
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from azure.cosmos.aio import CosmosClient
from azure.cosmos import PartitionKey
from azure.cosmos.exceptions import CosmosResourceNotFoundError, CosmosHttpResponseError
from azure.core import MatchConditions


COSMOS_ENDPOINT = os.environ.get("COSMOS_ENDPOINT", "https://localhost:8081")
COSMOS_KEY = os.environ.get(
    "COSMOS_KEY",
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==",
)
DATABASE_NAME = "gaming-leaderboard-db"
PLAYERS_CONTAINER = "players"
SCORES_CONTAINER = "scores"

cosmos_client: Optional[CosmosClient] = None
database = None
players_container = None
scores_container = None


async def init_cosmos():
    """Initialize Cosmos DB client (singleton) and create database/containers."""
    global cosmos_client, database, players_container, scores_container

    cosmos_client = CosmosClient(COSMOS_ENDPOINT, credential=COSMOS_KEY)

    database = await cosmos_client.create_database_if_not_exists(id=DATABASE_NAME)

    players_container = await database.create_container_if_not_exists(
        id=PLAYERS_CONTAINER,
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy={
            "indexingMode": "consistent",
            "automatic": True,
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [
                {"path": '/"_etag"/?'},
                {"path": "/totalScore/?"},
            ],
            "compositeIndexes": [
                [
                    {"path": "/bestScore", "order": "descending"},
                    {"path": "/displayName", "order": "ascending"},
                ],
                [
                    {"path": "/bestScore", "order": "ascending"},
                    {"path": "/displayName", "order": "descending"},
                ],
                [
                    {"path": "/region", "order": "ascending"},
                    {"path": "/bestScore", "order": "descending"},
                ],
                [
                    {"path": "/region", "order": "ascending"},
                    {"path": "/bestScore", "order": "ascending"},
                ],
            ],
        },
    )

    scores_container = await database.create_container_if_not_exists(
        id=SCORES_CONTAINER,
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy={
            "indexingMode": "consistent",
            "automatic": True,
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [
                {"path": '/"_etag"/?'},
                {"path": "/gameMode/?"},
            ],
            "compositeIndexes": [
                [
                    {"path": "/playerId", "order": "ascending"},
                    {"path": "/timestamp", "order": "descending"},
                ],
                [
                    {"path": "/playerId", "order": "ascending"},
                    {"path": "/timestamp", "order": "ascending"},
                ],
            ],
        },
    )


async def close_cosmos():
    """Close the Cosmos DB client."""
    global cosmos_client
    if cosmos_client:
        await cosmos_client.close()
        cosmos_client = None


@asynccontextmanager
async def lifespan(application: FastAPI):
    await init_cosmos()
    yield
    await close_cosmos()


app = FastAPI(title="Gaming Leaderboard API", lifespan=lifespan)


# ---------- Pydantic Models ----------

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


# ---------- Helper Functions ----------

def player_response(doc: dict) -> dict:
    """Extract player response fields from a Cosmos document."""
    return {
        "playerId": doc["playerId"],
        "displayName": doc["displayName"],
        "region": doc["region"],
        "totalGames": doc.get("totalGames", 0),
        "bestScore": doc.get("bestScore", 0),
        "averageScore": doc.get("averageScore", 0),
    }


def score_response(doc: dict) -> dict:
    """Extract score response fields from a Cosmos document."""
    result = {
        "scoreId": doc["scoreId"],
        "playerId": doc["playerId"],
        "score": doc["score"],
        "timestamp": doc["timestamp"],
    }
    if "gameMode" in doc and doc["gameMode"] is not None:
        result["gameMode"] = doc["gameMode"]
    return result


# ---------- Health ----------

@app.get("/health")
async def health():
    return JSONResponse(content={"status": "healthy"}, status_code=200)


# ---------- Player Management ----------

@app.post("/api/players", status_code=201)
async def create_player(request: CreatePlayerRequest):
    player_doc = {
        "id": request.playerId,
        "playerId": request.playerId,
        "displayName": request.displayName,
        "region": request.region,
        "totalGames": 0,
        "bestScore": 0,
        "averageScore": 0.0,
        "totalScore": 0,
        "type": "player",
        "schemaVersion": 1,
    }
    try:
        await players_container.create_item(body=player_doc)
    except CosmosHttpResponseError as e:
        if e.status_code == 409:
            raise HTTPException(status_code=409, detail="Player already exists")
        raise
    return player_response(player_doc)


@app.get("/api/players/{player_id}")
async def get_player(player_id: str):
    try:
        doc = await players_container.read_item(item=player_id, partition_key=player_id)
        return player_response(doc)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")


@app.patch("/api/players/{player_id}")
async def update_player(player_id: str, request: UpdatePlayerRequest):
    try:
        doc = await players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    if request.displayName is not None:
        doc["displayName"] = request.displayName
    if request.region is not None:
        doc["region"] = request.region

    updated = await players_container.replace_item(item=doc["id"], body=doc)
    return player_response(updated)


@app.delete("/api/players/{player_id}", status_code=204)
async def delete_player(player_id: str):
    try:
        await players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    await players_container.delete_item(item=player_id, partition_key=player_id)

    query = "SELECT c.id FROM c WHERE c.playerId = @playerId"
    params = [{"name": "@playerId", "value": player_id}]
    score_ids = []
    async for item in scores_container.query_items(
        query=query, parameters=params, partition_key=player_id
    ):
        score_ids.append(item["id"])

    for score_id in score_ids:
        await scores_container.delete_item(item=score_id, partition_key=player_id)

    return None


# ---------- Score Submission ----------

@app.post("/api/scores", status_code=201)
async def submit_score(request: SubmitScoreRequest):
    if request.score < 0:
        raise HTTPException(status_code=400, detail="Score must be a non-negative integer")

    try:
        player_doc = await players_container.read_item(
            item=request.playerId, partition_key=request.playerId
        )
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    score_id = str(uuid.uuid4())
    timestamp = datetime.now(timezone.utc).isoformat()

    score_doc = {
        "id": score_id,
        "scoreId": score_id,
        "playerId": request.playerId,
        "score": request.score,
        "gameMode": request.gameMode,
        "timestamp": timestamp,
        "type": "score",
        "schemaVersion": 1,
    }
    await scores_container.create_item(body=score_doc)

    max_retries = 10
    for _ in range(max_retries):
        total_games = player_doc.get("totalGames", 0) + 1
        total_score = player_doc.get("totalScore", 0) + request.score
        best_score = max(player_doc.get("bestScore", 0), request.score)
        average_score = total_score / total_games if total_games > 0 else 0.0

        player_doc["totalGames"] = total_games
        player_doc["totalScore"] = total_score
        player_doc["bestScore"] = best_score
        player_doc["averageScore"] = average_score

        try:
            await players_container.replace_item(
                item=player_doc["id"],
                body=player_doc,
                etag=player_doc.get("_etag"),
                match_condition=MatchConditions.IfNotModified,
            )
            break
        except CosmosHttpResponseError as e:
            if e.status_code == 412:
                player_doc = await players_container.read_item(
                    item=request.playerId, partition_key=request.playerId
                )
                continue
            raise

    return {
        "scoreId": score_id,
        "playerId": request.playerId,
        "score": request.score,
    }


# ---------- Score History ----------

@app.get("/api/players/{player_id}/scores")
async def get_player_scores(player_id: str, limit: int = Query(default=10, ge=1, le=100)):
    try:
        await players_container.read_item(item=player_id, partition_key=player_id)
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    query = (
        f"SELECT TOP {limit} c.scoreId, c.playerId, c.score, c.gameMode, c.timestamp "
        "FROM c WHERE c.playerId = @playerId ORDER BY c.timestamp DESC"
    )
    params = [{"name": "@playerId", "value": player_id}]

    results = []
    async for item in scores_container.query_items(
        query=query, parameters=params, partition_key=player_id
    ):
        results.append(score_response(item))
    return results


# ---------- Leaderboards ----------

@app.get("/api/leaderboards/global")
async def global_leaderboard(top: int = Query(default=100, ge=1, le=100)):
    query = (
        f"SELECT TOP {top} c.playerId, c.displayName, c.bestScore, c.region "
        "FROM c WHERE c.type = 'player' "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
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
async def regional_leaderboard(
    region: str, top: int = Query(default=100, ge=1, le=100)
):
    query = (
        f"SELECT TOP {top} c.playerId, c.displayName, c.bestScore "
        "FROM c WHERE c.type = 'player' AND c.region = @region "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
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


# ---------- Player Ranking ----------

@app.get("/api/players/{player_id}/rank")
async def get_player_rank(player_id: str):
    try:
        player_doc = await players_container.read_item(
            item=player_id, partition_key=player_id
        )
    except CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    player_best = player_doc.get("bestScore", 0)
    player_name = player_doc.get("displayName", "")

    if player_best == 0:
        total_query = "SELECT VALUE COUNT(1) FROM c WHERE c.type = 'player' AND c.bestScore > 0"
        count_result = 0
        async for item in players_container.query_items(
            query=total_query, enable_cross_partition_query=True
        ):
            count_result = item

        total_zero_query = "SELECT VALUE COUNT(1) FROM c WHERE c.type = 'player' AND c.bestScore = 0"
        zero_count = 0
        async for item in players_container.query_items(
            query=total_zero_query, enable_cross_partition_query=True
        ):
            zero_count = item

        if zero_count == 0:
            raise HTTPException(status_code=404, detail="Player not found or has no scores")

    count_query = (
        "SELECT VALUE COUNT(1) FROM c WHERE c.type = 'player' "
        "AND (c.bestScore > @score OR (c.bestScore = @score AND c.displayName < @name))"
    )
    count_params = [
        {"name": "@score", "value": player_best},
        {"name": "@name", "value": player_name},
    ]
    player_rank = 1
    async for item in players_container.query_items(
        query=count_query, parameters=count_params, enable_cross_partition_query=True
    ):
        player_rank = item + 1

    all_query = (
        "SELECT c.playerId, c.displayName, c.bestScore "
        "FROM c WHERE c.type = 'player' "
        "ORDER BY c.bestScore DESC, c.displayName ASC"
    )

    all_players = []
    async for item in players_container.query_items(
        query=all_query, enable_cross_partition_query=True
    ):
        all_players.append(item)

    player_index = -1
    for i, p in enumerate(all_players):
        if p["playerId"] == player_id:
            player_index = i
            break

    if player_index == -1:
        raise HTTPException(status_code=404, detail="Player not found or has no scores")

    start = max(0, player_index - 10)
    end = min(len(all_players), player_index + 11)

    neighbors = []
    for i in range(start, end):
        if i == player_index:
            continue
        p = all_players[i]
        neighbors.append(
            {
                "rank": i + 1,
                "playerId": p["playerId"],
                "displayName": p["displayName"],
                "score": p["bestScore"],
            }
        )

    return {
        "playerId": player_id,
        "rank": player_rank,
        "score": player_best,
        "neighbors": neighbors,
    }
