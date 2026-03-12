"""
Gaming Leaderboard API - FastAPI + Azure Cosmos DB
Implements the gaming-leaderboard API contract.
"""
import os
import uuid
from contextlib import asynccontextmanager
from typing import Optional

import urllib3
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from azure.cosmos.aio import CosmosClient
from azure.cosmos import exceptions as cosmos_exceptions, PartitionKey

# Suppress SSL warnings for local development with emulator
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

COSMOS_ENDPOINT = os.environ.get("COSMOS_ENDPOINT", "https://localhost:8081")
COSMOS_KEY = os.environ.get(
    "COSMOS_KEY",
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==",
)
DATABASE_NAME = "gaming-leaderboard"

# Singleton Cosmos DB client and container references
cosmos_client: CosmosClient = None
players_container = None
leaderboard_container = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Initialize Cosmos DB resources on startup and close on shutdown."""
    global cosmos_client, players_container, leaderboard_container

    cosmos_client = CosmosClient(
        url=COSMOS_ENDPOINT,
        credential=COSMOS_KEY,
        connection_verify=False,  # Required for local emulator
    )

    db = await cosmos_client.create_database_if_not_exists(DATABASE_NAME)

    # Players container: partition key = /playerId
    players_container = await db.create_container_if_not_exists(
        id="players",
        partition_key=PartitionKey(path="/playerId"),
        offer_throughput=400,
    )

    # Leaderboard container: partition key = /leaderboardKey
    # Each player has one entry per leaderboard ("global" or region code).
    # Queries stay within a single partition, avoiding cross-partition scans.
    leaderboard_container = await db.create_container_if_not_exists(
        id="leaderboard",
        partition_key=PartitionKey(path="/leaderboardKey"),
        offer_throughput=400,
    )

    yield

    await cosmos_client.close()


app = FastAPI(lifespan=lifespan)


# ---------------------------------------------------------------------------
# Request / Response models
# ---------------------------------------------------------------------------

class CreatePlayerRequest(BaseModel):
    playerId: str
    displayName: str
    region: str


class SubmitScoreRequest(BaseModel):
    playerId: str
    score: int
    gameMode: Optional[str] = None


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def player_to_response(doc: dict) -> dict:
    return {
        "playerId": doc["playerId"],
        "displayName": doc["displayName"],
        "region": doc["region"],
        "totalGames": doc.get("totalGames", 0),
        "bestScore": doc.get("bestScore", 0),
        "averageScore": doc.get("averageScore", 0.0),
    }


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

@app.get("/health")
async def health():
    return {"status": "ok"}


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
    }
    try:
        await players_container.create_item(body=player_doc)
    except cosmos_exceptions.CosmosResourceExistsError:
        raise HTTPException(status_code=409, detail="Player already exists")
    return player_to_response(player_doc)


@app.get("/api/players/{player_id}")
async def get_player(player_id: str):
    try:
        doc = await players_container.read_item(
            item=player_id, partition_key=player_id
        )
        return player_to_response(doc)
    except cosmos_exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")


@app.post("/api/scores", status_code=201)
async def submit_score(request: SubmitScoreRequest):
    score_id = str(uuid.uuid4())

    # Read current player document
    try:
        player_doc = await players_container.read_item(
            item=request.playerId, partition_key=request.playerId
        )
    except cosmos_exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Update cumulative stats
    total_games = player_doc.get("totalGames", 0) + 1
    total_score = player_doc.get("totalScore", 0) + request.score
    best_score = max(player_doc.get("bestScore", 0), request.score)
    average_score = total_score / total_games

    player_doc["totalGames"] = total_games
    player_doc["totalScore"] = total_score
    player_doc["bestScore"] = best_score
    player_doc["averageScore"] = average_score

    await players_container.upsert_item(body=player_doc)

    # Upsert global leaderboard entry (one entry per player, stores best score)
    await leaderboard_container.upsert_item(
        body={
            "id": f"global#{request.playerId}",
            "leaderboardKey": "global",
            "playerId": request.playerId,
            "displayName": player_doc["displayName"],
            "score": best_score,
            "region": player_doc["region"],
        }
    )

    # Upsert regional leaderboard entry
    region = player_doc["region"]
    await leaderboard_container.upsert_item(
        body={
            "id": f"{region}#{request.playerId}",
            "leaderboardKey": region,
            "playerId": request.playerId,
            "displayName": player_doc["displayName"],
            "score": best_score,
            "region": region,
        }
    )

    return {
        "scoreId": score_id,
        "playerId": request.playerId,
        "score": request.score,
    }


@app.get("/api/leaderboards/global")
async def global_leaderboard(top: int = 100):
    # Clamp top between 1 and 100
    top = max(1, min(top, 100))
    # Use literal integer in OFFSET/LIMIT (per SDK requirement — parameters not
    # accepted for TOP/OFFSET/LIMIT in Cosmos DB SQL)
    query = (
        f"SELECT c.playerId, c.displayName, c.score FROM c "
        f"ORDER BY c.score DESC OFFSET 0 LIMIT {top}"
    )
    entries = []
    async for item in leaderboard_container.query_items(
        query=query,
        partition_key="global",
    ):
        entries.append(item)

    return [
        {
            "rank": i + 1,
            "playerId": e["playerId"],
            "displayName": e["displayName"],
            "score": e["score"],
        }
        for i, e in enumerate(entries)
    ]


@app.get("/api/leaderboards/regional/{region}")
async def regional_leaderboard(region: str, top: int = 100):
    top = max(1, min(top, 100))
    query = (
        f"SELECT c.playerId, c.displayName, c.score FROM c "
        f"ORDER BY c.score DESC OFFSET 0 LIMIT {top}"
    )
    entries = []
    async for item in leaderboard_container.query_items(
        query=query,
        partition_key=region,
    ):
        entries.append(item)

    return [
        {
            "rank": i + 1,
            "playerId": e["playerId"],
            "displayName": e["displayName"],
            "score": e["score"],
        }
        for i, e in enumerate(entries)
    ]


@app.get("/api/players/{player_id}/rank")
async def get_player_rank(player_id: str):
    # Verify player exists
    try:
        player_doc = await players_container.read_item(
            item=player_id, partition_key=player_id
        )
    except cosmos_exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    player_score = player_doc.get("bestScore", 0)
    if player_doc.get("totalGames", 0) == 0:
        raise HTTPException(
            status_code=404, detail="Player not found or has no scores"
        )

    # Count-based rank: players with a strictly higher score + 1
    # This avoids scanning all leaderboard entries (O(1) RU cost)
    count_query = "SELECT VALUE COUNT(1) FROM c WHERE c.score > @score"
    rank = 1
    async for count in leaderboard_container.query_items(
        query=count_query,
        parameters=[{"name": "@score", "value": player_score}],
        partition_key="global",
    ):
        rank = count + 1
        break

    # Fetch a window of entries around the player using OFFSET/LIMIT
    start_offset = max(0, rank - 11)  # up to 10 positions above
    window_limit = 21                  # player + 10 above + 10 below
    neighbors_query = (
        f"SELECT c.playerId, c.displayName, c.score FROM c "
        f"ORDER BY c.score DESC OFFSET {start_offset} LIMIT {window_limit}"
    )
    window = []
    async for item in leaderboard_container.query_items(
        query=neighbors_query,
        partition_key="global",
    ):
        window.append(item)

    neighbors = []
    for i, entry in enumerate(window):
        position = start_offset + i + 1  # 1-based rank
        if entry["playerId"] != player_id:
            neighbors.append(
                {
                    "rank": position,
                    "playerId": entry["playerId"],
                    "displayName": entry["displayName"],
                    "score": entry["score"],
                }
            )

    return {
        "playerId": player_id,
        "rank": rank,
        "score": player_score,
        "neighbors": neighbors,
    }
