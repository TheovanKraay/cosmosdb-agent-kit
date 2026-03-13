import os
import uuid
from contextlib import asynccontextmanager
from typing import List, Optional

from azure.cosmos import CosmosClient, PartitionKey, exceptions
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

COSMOS_ENDPOINT = os.environ.get("COSMOS_ENDPOINT", "https://localhost:8081")
COSMOS_KEY = os.environ.get(
    "COSMOS_KEY",
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMcZcLU/D1YZP4OQ==",
)
DATABASE_NAME = "gaming-leaderboard-db"

players_container = None
scores_container = None


# ---------------------------------------------------------------------------
# Application lifespan (startup / shutdown)
# ---------------------------------------------------------------------------


@asynccontextmanager
async def lifespan(app: FastAPI):
    global players_container, scores_container

    client = CosmosClient(COSMOS_ENDPOINT, credential=COSMOS_KEY)
    database = client.create_database_if_not_exists(id=DATABASE_NAME)

    players_container = database.create_container_if_not_exists(
        id="players",
        partition_key=PartitionKey(path="/playerId"),
        offer_throughput=400,
    )
    scores_container = database.create_container_if_not_exists(
        id="scores",
        partition_key=PartitionKey(path="/playerId"),
        offer_throughput=400,
    )

    yield


app = FastAPI(lifespan=lifespan)


# ---------------------------------------------------------------------------
# Pydantic models
# ---------------------------------------------------------------------------


class PlayerCreate(BaseModel):
    playerId: str
    displayName: str
    region: str


class PlayerResponse(BaseModel):
    playerId: str
    displayName: str
    region: str
    totalGames: int
    bestScore: int
    averageScore: float


class ScoreSubmit(BaseModel):
    playerId: str
    score: int
    gameMode: Optional[str] = None


class ScoreResponse(BaseModel):
    scoreId: str
    playerId: str
    score: int


class LeaderboardEntry(BaseModel):
    rank: int
    playerId: str
    displayName: str
    score: int


class PlayerRankResponse(BaseModel):
    playerId: str
    rank: int
    score: int
    neighbors: List[LeaderboardEntry]


# ---------------------------------------------------------------------------
# Helper
# ---------------------------------------------------------------------------


def _player_response(doc: dict) -> PlayerResponse:
    return PlayerResponse(
        playerId=doc["playerId"],
        displayName=doc["displayName"],
        region=doc["region"],
        totalGames=doc["totalGames"],
        bestScore=doc["bestScore"],
        averageScore=doc["averageScore"],
    )


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/api/players", status_code=201, response_model=PlayerResponse)
def create_player(player: PlayerCreate):
    doc = {
        "id": player.playerId,
        "playerId": player.playerId,
        "displayName": player.displayName,
        "region": player.region,
        "totalGames": 0,
        "bestScore": 0,
        "averageScore": 0.0,
    }
    try:
        players_container.create_item(body=doc)
    except exceptions.CosmosResourceExistsError:
        raise HTTPException(status_code=409, detail="Player already exists")
    return _player_response(doc)


@app.get("/api/players/{playerId}", response_model=PlayerResponse)
def get_player(playerId: str):
    try:
        doc = players_container.read_item(item=playerId, partition_key=playerId)
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")
    return _player_response(doc)


@app.post("/api/scores", status_code=201, response_model=ScoreResponse)
def submit_score(data: ScoreSubmit):
    # Verify the player exists
    try:
        player = players_container.read_item(
            item=data.playerId, partition_key=data.playerId
        )
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Persist the score record
    score_id = str(uuid.uuid4())
    score_doc = {
        "id": score_id,
        "scoreId": score_id,
        "playerId": data.playerId,
        "score": data.score,
        "gameMode": data.gameMode,
    }
    scores_container.create_item(body=score_doc)

    # Update player cumulative stats
    total_games = player["totalGames"] + 1
    best_score = max(player["bestScore"], data.score)
    old_sum = player["averageScore"] * player["totalGames"]
    average_score = (old_sum + data.score) / total_games

    player["totalGames"] = total_games
    player["bestScore"] = best_score
    player["averageScore"] = average_score
    players_container.replace_item(item=data.playerId, body=player)

    return ScoreResponse(scoreId=score_id, playerId=data.playerId, score=data.score)


@app.get("/api/leaderboards/global", response_model=List[LeaderboardEntry])
def global_leaderboard(top: int = 100):
    top = min(max(top, 1), 100)
    query = (
        f"SELECT c.playerId, c.displayName, c.bestScore "
        f"FROM c ORDER BY c.bestScore DESC OFFSET 0 LIMIT {top}"
    )
    items = list(
        players_container.query_items(
            query=query, enable_cross_partition_query=True
        )
    )
    return [
        LeaderboardEntry(
            rank=i + 1,
            playerId=item["playerId"],
            displayName=item["displayName"],
            score=item["bestScore"],
        )
        for i, item in enumerate(items)
    ]


@app.get(
    "/api/leaderboards/regional/{region}", response_model=List[LeaderboardEntry]
)
def regional_leaderboard(region: str, top: int = 100):
    top = min(max(top, 1), 100)
    query = (
        f"SELECT c.playerId, c.displayName, c.bestScore "
        f"FROM c WHERE c.region = @region ORDER BY c.bestScore DESC OFFSET 0 LIMIT {top}"
    )
    items = list(
        players_container.query_items(
            query=query,
            parameters=[{"name": "@region", "value": region}],
            enable_cross_partition_query=True,
        )
    )
    return [
        LeaderboardEntry(
            rank=i + 1,
            playerId=item["playerId"],
            displayName=item["displayName"],
            score=item["bestScore"],
        )
        for i, item in enumerate(items)
    ]


@app.get("/api/players/{playerId}/rank", response_model=PlayerRankResponse)
def player_rank(playerId: str):
    try:
        player = players_container.read_item(
            item=playerId, partition_key=playerId
        )
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    player_score = player["bestScore"]

    # Rank = number of players with a strictly higher bestScore + 1
    count_result = list(
        players_container.query_items(
            query="SELECT VALUE COUNT(1) FROM c WHERE c.bestScore > @score",
            parameters=[{"name": "@score", "value": player_score}],
            enable_cross_partition_query=True,
        )
    )
    rank = (count_result[0] if count_result else 0) + 1

    # Fetch the ±10 window around this player
    offset = max(0, rank - 11)
    limit = 21
    nearby_query = (
        f"SELECT c.playerId, c.displayName, c.bestScore "
        f"FROM c ORDER BY c.bestScore DESC OFFSET {offset} LIMIT {limit}"
    )
    nearby = list(
        players_container.query_items(
            query=nearby_query, enable_cross_partition_query=True
        )
    )

    neighbors = [
        LeaderboardEntry(
            rank=offset + i + 1,
            playerId=item["playerId"],
            displayName=item["displayName"],
            score=item["bestScore"],
        )
        for i, item in enumerate(nearby)
        if item["playerId"] != playerId
    ]

    return PlayerRankResponse(
        playerId=playerId,
        rank=rank,
        score=player_score,
        neighbors=neighbors,
    )
