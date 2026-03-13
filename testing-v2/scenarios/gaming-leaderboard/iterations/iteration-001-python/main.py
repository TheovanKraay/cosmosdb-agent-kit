"""
Gaming Leaderboard API — FastAPI + Azure Cosmos DB (NoSQL)

Design decisions (following Cosmos DB best practices):
- Players container:      partition key = /playerId   (point reads by ID)
- Scores container:       partition key = /playerId   (write path per player)
- Leaderboard container:  partition key = /leaderboardKey  (materialized view)
  - leaderboardKey = "global" for global leaderboard
  - leaderboardKey = "<region>" for regional leaderboards
  - One document per player per leaderboard key (upserted on every score)
  - ORDER BY queries are single-partition → efficient, no cross-partition fan-out
  - Composite indexes on /score enable ORDER BY queries
- Count-based ranking: COUNT(players with score > X) avoids full-partition scans
"""

import os
import uuid
import logging
import urllib3
from contextlib import asynccontextmanager
from typing import List, Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from azure.cosmos.aio import CosmosClient
from azure.cosmos import PartitionKey, exceptions

# Suppress SSL warnings — emulator uses a self-signed certificate
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

COSMOS_ENDPOINT: str = os.getenv("COSMOS_ENDPOINT", "https://localhost:8081")
COSMOS_KEY: str = os.getenv(
    "COSMOS_KEY",
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==",
)
DATABASE_NAME = "gaming-leaderboard"

# ---------------------------------------------------------------------------
# Pydantic models
# ---------------------------------------------------------------------------


class CreatePlayerRequest(BaseModel):
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


class SubmitScoreRequest(BaseModel):
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
# App state (singleton CosmosClient — rule 4.18)
# ---------------------------------------------------------------------------

_cosmos_client: Optional[CosmosClient] = None
_players_container = None
_scores_container = None
_leaderboard_container = None


# ---------------------------------------------------------------------------
# Lifespan — create DB / containers once at startup
# ---------------------------------------------------------------------------


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _cosmos_client, _players_container, _scores_container, _leaderboard_container

    # Python async SDK requires aiohttp; connection_verify=False for emulator
    # (rule 4.6 — emulator uses self-signed SSL cert, Gateway mode by default)
    _cosmos_client = CosmosClient(
        url=COSMOS_ENDPOINT,
        credential=COSMOS_KEY,
        connection_verify=False,
    )

    db = await _cosmos_client.create_database_if_not_exists(id=DATABASE_NAME)

    # Players: partition key = /playerId — enables point reads by player ID
    _players_container = await db.create_container_if_not_exists(
        id="players",
        partition_key=PartitionKey(path="/playerId"),
    )

    # Scores: partition key = /playerId — writes stay within player's partition
    _scores_container = await db.create_container_if_not_exists(
        id="scores",
        partition_key=PartitionKey(path="/playerId"),
    )

    # Leaderboard: materialized view (rule 9.1)
    # Composite indexes support ORDER BY c.score DESC / ASC queries (rule 5.2)
    _leaderboard_container = await db.create_container_if_not_exists(
        id="leaderboard",
        partition_key=PartitionKey(path="/leaderboardKey"),
        indexing_policy={
            "indexingMode": "consistent",
            "automatic": True,
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [{"path": '/"_etag"/?'}],
            "compositeIndexes": [
                [
                    {"path": "/score", "order": "descending"},
                    {"path": "/playerId", "order": "ascending"},
                ],
                [
                    {"path": "/score", "order": "ascending"},
                    {"path": "/playerId", "order": "ascending"},
                ],
            ],
        },
    )

    yield

    await _cosmos_client.close()


app = FastAPI(title="Gaming Leaderboard API", lifespan=lifespan)


# ---------------------------------------------------------------------------
# Health
# ---------------------------------------------------------------------------


@app.get("/health")
async def health():
    return {"status": "ok"}


# ---------------------------------------------------------------------------
# Players
# ---------------------------------------------------------------------------


@app.post("/api/players", status_code=201)
async def create_player(req: CreatePlayerRequest) -> PlayerResponse:
    """Create a new player profile with zeroed stats."""
    player_doc = {
        "id": req.playerId,
        "playerId": req.playerId,
        "displayName": req.displayName,
        "region": req.region,
        "totalGames": 0,
        "bestScore": 0,
        "averageScore": 0.0,
        "totalScore": 0,  # internal field used to compute averageScore
        "type": "player",
    }
    try:
        await _players_container.create_item(body=player_doc)
    except exceptions.CosmosResourceExistsError:
        raise HTTPException(status_code=409, detail="Player already exists")

    return PlayerResponse(
        playerId=req.playerId,
        displayName=req.displayName,
        region=req.region,
        totalGames=0,
        bestScore=0,
        averageScore=0.0,
    )


@app.get("/api/players/{playerId}")
async def get_player(playerId: str) -> PlayerResponse:
    """Get player profile and cumulative stats."""
    try:
        item = await _players_container.read_item(
            item=playerId, partition_key=playerId
        )
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    return PlayerResponse(
        playerId=item["playerId"],
        displayName=item["displayName"],
        region=item["region"],
        totalGames=item["totalGames"],
        bestScore=item["bestScore"],
        averageScore=float(item["averageScore"]),
    )


# ---------------------------------------------------------------------------
# Scores
# ---------------------------------------------------------------------------


@app.post("/api/scores", status_code=201)
async def submit_score(req: SubmitScoreRequest) -> ScoreResponse:
    """Submit a game score; update player stats and materialized leaderboard."""
    # Verify player exists (point read — single-partition, cheap)
    try:
        player = await _players_container.read_item(
            item=req.playerId, partition_key=req.playerId
        )
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    # Persist the individual score record
    score_id = str(uuid.uuid4())
    score_doc = {
        "id": score_id,
        "scoreId": score_id,
        "playerId": req.playerId,
        "score": req.score,
        "gameMode": req.gameMode,
        "type": "score",
    }
    await _scores_container.create_item(body=score_doc)

    # Update cumulative player stats
    prev_total_score: int = player.get("totalScore", 0)
    new_total_games: int = player["totalGames"] + 1
    new_total_score: int = prev_total_score + req.score
    new_best_score: int = max(player["bestScore"], req.score)
    new_avg_score: float = new_total_score / new_total_games

    player["totalGames"] = new_total_games
    player["totalScore"] = new_total_score
    player["bestScore"] = new_best_score
    player["averageScore"] = new_avg_score

    await _players_container.replace_item(item=req.playerId, body=player)

    # Upsert leaderboard entries (materialized view — rule 9.1)
    # One doc per player per leaderboard key; always reflects the player's bestScore
    region: str = player["region"]
    display_name: str = player["displayName"]

    lb_global = {
        "id": req.playerId,
        "leaderboardKey": "global",
        "playerId": req.playerId,
        "displayName": display_name,
        "score": new_best_score,
        "type": "leaderboardEntry",
    }
    await _leaderboard_container.upsert_item(body=lb_global)

    lb_regional = {
        "id": req.playerId,
        "leaderboardKey": region,
        "playerId": req.playerId,
        "displayName": display_name,
        "score": new_best_score,
        "type": "leaderboardEntry",
    }
    await _leaderboard_container.upsert_item(body=lb_regional)

    return ScoreResponse(scoreId=score_id, playerId=req.playerId, score=req.score)


# ---------------------------------------------------------------------------
# Leaderboards
# ---------------------------------------------------------------------------


@app.get("/api/leaderboards/global")
async def global_leaderboard(top: int = 100) -> List[LeaderboardEntry]:
    """Return global top N players sorted by best score descending.

    Queries the "global" logical partition — single-partition, no fan-out.
    TOP uses a literal integer (rule 3.6: TOP does not support parameters).
    """
    top = min(max(1, top), 100)
    # Literal integer for TOP — parameterized TOP is not supported (rule 3.6)
    query = (
        f"SELECT TOP {top} c.playerId, c.displayName, c.score "
        f"FROM c "
        f"ORDER BY c.score DESC"
    )
    items: list = []
    async for item in _leaderboard_container.query_items(
        query=query,
        partition_key="global",
    ):
        items.append(item)

    return [
        LeaderboardEntry(
            rank=i + 1,
            playerId=item["playerId"],
            displayName=item["displayName"],
            score=item["score"],
        )
        for i, item in enumerate(items)
    ]


@app.get("/api/leaderboards/regional/{region}")
async def regional_leaderboard(region: str, top: int = 100) -> List[LeaderboardEntry]:
    """Return top N players for a specific region, sorted by best score descending.

    Queries the region's logical partition — single-partition, no fan-out.
    """
    top = min(max(1, top), 100)
    query = (
        f"SELECT TOP {top} c.playerId, c.displayName, c.score "
        f"FROM c "
        f"ORDER BY c.score DESC"
    )
    items: list = []
    async for item in _leaderboard_container.query_items(
        query=query,
        partition_key=region,
    ):
        items.append(item)

    return [
        LeaderboardEntry(
            rank=i + 1,
            playerId=item["playerId"],
            displayName=item["displayName"],
            score=item["score"],
        )
        for i, item in enumerate(items)
    ]


# ---------------------------------------------------------------------------
# Player Rank
# ---------------------------------------------------------------------------


@app.get("/api/players/{playerId}/rank")
async def player_rank(playerId: str) -> PlayerRankResponse:
    """Return a player's global rank and neighboring players (±10 positions).

    Uses count-based ranking (rule 9.2): count players with a higher score
    to determine rank without scanning the entire leaderboard.
    All queries target the "global" partition — efficient single-partition reads.
    """
    try:
        player = await _players_container.read_item(
            item=playerId, partition_key=playerId
        )
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Player not found")

    if player["totalGames"] == 0:
        raise HTTPException(status_code=404, detail="Player has no scores")

    best_score: int = player["bestScore"]

    # Count-based rank: COUNT(players with score strictly higher) + 1 (rule 9.2)
    count_query = "SELECT VALUE COUNT(1) FROM c WHERE c.score > @playerScore"
    count_result: list = []
    async for val in _leaderboard_container.query_items(
        query=count_query,
        parameters=[{"name": "@playerScore", "value": best_score}],
        partition_key="global",
    ):
        count_result.append(val)

    rank: int = (count_result[0] if count_result else 0) + 1

    # Neighbors above: up to 10 players with score > player, ordered ASC
    # (ASC gives the closest rivals first after reversing)
    above_query = (
        "SELECT TOP 10 c.playerId, c.displayName, c.score "
        "FROM c "
        "WHERE c.score > @playerScore "
        "ORDER BY c.score ASC"
    )
    above_items: list = []
    async for item in _leaderboard_container.query_items(
        query=above_query,
        parameters=[{"name": "@playerScore", "value": best_score}],
        partition_key="global",
    ):
        above_items.append(item)

    # Neighbors below: up to 10 players with score < player, ordered DESC
    below_query = (
        "SELECT TOP 10 c.playerId, c.displayName, c.score "
        "FROM c "
        "WHERE c.score < @playerScore "
        "ORDER BY c.score DESC"
    )
    below_items: list = []
    async for item in _leaderboard_container.query_items(
        query=below_query,
        parameters=[{"name": "@playerScore", "value": best_score}],
        partition_key="global",
    ):
        below_items.append(item)

    # Build neighbors list: higher-ranked first, then lower-ranked
    neighbors: List[LeaderboardEntry] = []

    # above_items is ASC (lowest first among those above); reverse to get highest first
    for i, item in enumerate(reversed(above_items)):
        neighbor_rank = rank - len(above_items) + i
        if neighbor_rank >= 1:
            neighbors.append(
                LeaderboardEntry(
                    rank=neighbor_rank,
                    playerId=item["playerId"],
                    displayName=item["displayName"],
                    score=item["score"],
                )
            )

    for i, item in enumerate(below_items):
        neighbors.append(
            LeaderboardEntry(
                rank=rank + i + 1,
                playerId=item["playerId"],
                displayName=item["displayName"],
                score=item["score"],
            )
        )

    return PlayerRankResponse(
        playerId=playerId,
        rank=rank,
        score=best_score,
        neighbors=neighbors,
    )
