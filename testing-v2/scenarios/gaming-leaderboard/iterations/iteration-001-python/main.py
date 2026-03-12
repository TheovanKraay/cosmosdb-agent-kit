"""
Gaming Leaderboard API — FastAPI + Azure Cosmos DB (NoSQL, async SDK)

Implements the contract defined in api-contract.yaml:
  GET  /health
  POST /api/players
  GET  /api/players/{playerId}
  POST /api/scores
  GET  /api/leaderboards/global?top=N
  GET  /api/leaderboards/regional/{region}?top=N
  GET  /api/players/{playerId}/rank
"""

import logging
import os
import uuid
from contextlib import asynccontextmanager
from typing import Any, Dict, List, Optional

from azure.core import MatchConditions
from azure.cosmos import PartitionKey
from azure.cosmos import exceptions as cosmos_exceptions
from azure.cosmos.aio import CosmosClient
from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
DB_NAME = "gaming-leaderboard"
PLAYERS_CONTAINER = "players"
SCORES_CONTAINER = "scores"
MAX_OCC_RETRIES = 3

# ---------------------------------------------------------------------------
# Request / response models
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
# Global singletons (initialised in lifespan)
# ---------------------------------------------------------------------------
_cosmos_client: Optional[CosmosClient] = None
_players_container = None
_scores_container = None


# ---------------------------------------------------------------------------
# Application lifespan — create client and containers once
# ---------------------------------------------------------------------------


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _cosmos_client, _players_container, _scores_container

    endpoint = os.environ.get("COSMOS_ENDPOINT", "https://localhost:8081")
    key = os.environ.get(
        "COSMOS_KEY",
        # Standard Cosmos DB Emulator key
        "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==",
    )

    # Reuse a single CosmosClient for the lifetime of the application
    _cosmos_client = CosmosClient(url=endpoint, credential=key)

    db = await _cosmos_client.create_database_if_not_exists(id=DB_NAME)

    # Composite indexes let Cosmos DB satisfy ORDER BY on bestScore across
    # all partitions without a full container scan on every leaderboard read.
    # Directions in the index MUST match ORDER BY directions (rule 5.1).
    players_indexing_policy: Dict[str, Any] = {
        "indexingMode": "consistent",
        "automatic": True,
        "includedPaths": [{"path": "/*"}],
        "excludedPaths": [{"path": '/"_etag"/?'}],
        "compositeIndexes": [
            # Global leaderboard: ORDER BY bestScore DESC, playerId ASC
            [
                {"path": "/bestScore", "order": "descending"},
                {"path": "/playerId", "order": "ascending"},
            ],
            # Regional leaderboard: WHERE region = X ORDER BY bestScore DESC
            [
                {"path": "/region", "order": "ascending"},
                {"path": "/bestScore", "order": "descending"},
                {"path": "/playerId", "order": "ascending"},
            ],
        ],
    }

    _players_container = await db.create_container_if_not_exists(
        id=PLAYERS_CONTAINER,
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy=players_indexing_policy,
    )

    _scores_container = await db.create_container_if_not_exists(
        id=SCORES_CONTAINER,
        partition_key=PartitionKey(path="/playerId"),
    )

    yield

    await _cosmos_client.close()


app = FastAPI(lifespan=lifespan)


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
async def create_player(body: CreatePlayerRequest):
    doc = {
        "id": body.playerId,
        "playerId": body.playerId,
        "displayName": body.displayName,
        "region": body.region,
        "totalGames": 0,
        "bestScore": 0,
        "averageScore": 0.0,
        "totalScore": 0,
    }
    await _players_container.create_item(body=doc)
    return _player_response(doc)


@app.get("/api/players/{player_id}")
async def get_player(player_id: str):
    try:
        doc = await _players_container.read_item(
            item=player_id, partition_key=player_id
        )
    except cosmos_exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail=f"Player {player_id} not found")
    return _player_response(doc)


# ---------------------------------------------------------------------------
# Scores
# ---------------------------------------------------------------------------


@app.post("/api/scores", status_code=201)
async def submit_score(body: SubmitScoreRequest):
    # Verify the player exists before recording a score
    try:
        await _players_container.read_item(
            item=body.playerId, partition_key=body.playerId
        )
    except cosmos_exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail=f"Player {body.playerId} not found")

    # Persist the individual score record
    score_id = str(uuid.uuid4())
    score_doc: Dict[str, Any] = {
        "id": score_id,
        "scoreId": score_id,
        "playerId": body.playerId,
        "score": body.score,
    }
    if body.gameMode:
        score_doc["gameMode"] = body.gameMode
    await _scores_container.create_item(body=score_doc)

    # Update cumulative player stats using optimistic concurrency (ETags)
    # so that concurrent score submissions don't silently clobber each other.
    for attempt in range(MAX_OCC_RETRIES):
        try:
            player = await _players_container.read_item(
                item=body.playerId, partition_key=body.playerId
            )
            etag = player["_etag"]
            new_total_games = player["totalGames"] + 1
            new_total_score = player.get("totalScore", 0) + body.score
            new_best_score = max(player["bestScore"], body.score)
            new_average_score = new_total_score / new_total_games

            player["totalGames"] = new_total_games
            player["totalScore"] = new_total_score
            player["bestScore"] = new_best_score
            player["averageScore"] = new_average_score

            await _players_container.replace_item(
                item=body.playerId,
                body=player,
                etag=etag,
                match_condition=MatchConditions.IfNotModified,
            )
            break  # success
        except cosmos_exceptions.CosmosAccessConditionFailedError:
            if attempt == MAX_OCC_RETRIES - 1:
                logger.warning(
                    "Could not update stats for player %s after %d retries",
                    body.playerId,
                    MAX_OCC_RETRIES,
                )
            # retry with freshly read document

    return {"scoreId": score_id, "playerId": body.playerId, "score": body.score}


# ---------------------------------------------------------------------------
# Leaderboards
# ---------------------------------------------------------------------------


@app.get("/api/leaderboards/global")
async def global_leaderboard(top: int = Query(default=100, ge=1, le=100)):
    """Return the global top-N players sorted by bestScore descending."""
    # Cross-partition ORDER BY query backed by a composite index.
    # We consume the iterator in Python and stop at `top` entries to avoid
    # fetching more data than needed (avoids using TOP with a parameter,
    # which is unsupported in Cosmos DB SQL — rule 3.6).
    query = (
        "SELECT c.playerId, c.displayName, c.bestScore AS score "
        "FROM c "
        "WHERE c.bestScore > 0 "
        "ORDER BY c.bestScore DESC, c.playerId ASC"
    )
    entries = await _fetch_top_n(query, params=None, limit=top)
    return [{"rank": i + 1, **e} for i, e in enumerate(entries)]


@app.get("/api/leaderboards/regional/{region}")
async def regional_leaderboard(
    region: str, top: int = Query(default=100, ge=1, le=100)
):
    """Return top-N players for the given region, sorted by bestScore descending."""
    query = (
        "SELECT c.playerId, c.displayName, c.bestScore AS score "
        "FROM c "
        "WHERE c.region = @region AND c.bestScore > 0 "
        "ORDER BY c.bestScore DESC, c.playerId ASC"
    )
    params = [{"name": "@region", "value": region}]
    entries = await _fetch_top_n(query, params=params, limit=top)
    return [{"rank": i + 1, **e} for i, e in enumerate(entries)]


# ---------------------------------------------------------------------------
# Player rank
# ---------------------------------------------------------------------------


@app.get("/api/players/{player_id}/rank")
async def player_rank(player_id: str):
    """Return a player's global rank and the ±10 neighbouring players."""
    try:
        player = await _players_container.read_item(
            item=player_id, partition_key=player_id
        )
    except cosmos_exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail=f"Player {player_id} not found")

    player_score = player["bestScore"]
    if player_score == 0:
        raise HTTPException(
            status_code=404, detail=f"Player {player_id} has no scores yet"
        )

    # Count-based rank: rank = (number of players with a strictly higher
    # bestScore) + 1  (rule 9.2 — avoid full partition scans for ranking).
    count_query = (
        "SELECT VALUE COUNT(1) FROM c WHERE c.bestScore > @score"
    )
    count_params = [{"name": "@score", "value": player_score}]
    rank = await _scalar_count(count_query, count_params) + 1

    # Fetch only the ±10 window around this player using OFFSET/LIMIT so
    # that we don't retrieve all leading entries for high-ranked players.
    window_size = 21  # 10 above + player + 10 below
    window_start = max(0, rank - 11)  # 0-indexed offset into the sorted list

    neighbor_query = (
        "SELECT c.playerId, c.displayName, c.bestScore AS score "
        "FROM c "
        "WHERE c.bestScore > 0 "
        "ORDER BY c.bestScore DESC, c.playerId ASC "
        "OFFSET @skip LIMIT @take"
    )
    neighbor_params = [
        {"name": "@skip", "value": window_start},
        {"name": "@take", "value": window_size},
    ]
    window = await _run_query(neighbor_query, neighbor_params)

    neighbors: List[Dict[str, Any]] = []
    for i, entry in enumerate(window, start=window_start + 1):
        if entry["playerId"] != player_id:
            neighbors.append(
                {
                    "rank": i,
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


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _player_response(doc: Dict[str, Any]) -> Dict[str, Any]:
    return {
        "playerId": doc["playerId"],
        "displayName": doc["displayName"],
        "region": doc["region"],
        "totalGames": doc["totalGames"],
        "bestScore": doc["bestScore"],
        "averageScore": doc["averageScore"],
    }


async def _run_query(
    query: str,
    params: Optional[List[Dict[str, Any]]],
) -> List[Dict[str, Any]]:
    """Run a cross-partition query and return all results."""
    items: List[Dict[str, Any]] = []
    async for item in _players_container.query_items(
        query=query,
        parameters=params,
        enable_cross_partition_query=True,
    ):
        items.append(item)
    return items


async def _fetch_top_n(
    query: str,
    params: Optional[List[Dict[str, Any]]],
    limit: int,
) -> List[Dict[str, Any]]:
    """Run a cross-partition query and return at most `limit` items."""
    items: List[Dict[str, Any]] = []
    async for item in _players_container.query_items(
        query=query,
        parameters=params,
        enable_cross_partition_query=True,
    ):
        items.append(item)
        if len(items) >= limit:
            break
    return items


async def _scalar_count(
    query: str, params: List[Dict[str, Any]]
) -> int:
    """Execute a COUNT query and return the integer result."""
    results: List[Any] = []
    async for item in _players_container.query_items(
        query=query,
        parameters=params,
        enable_cross_partition_query=True,
    ):
        results.append(item)
    return int(results[0]) if results else 0
