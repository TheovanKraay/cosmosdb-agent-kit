"""
Gaming Leaderboard API — FastAPI + Azure Cosmos DB

Iteration 003 (Python) testing for cosmosdb-best-practices skill.

Endpoints:
  POST /scores           - Submit a score
  GET  /leaderboards/global?period=weekly|all-time  - Global top 100
  GET  /leaderboards/regional/{country}?period=weekly|all-time - Regional top 100
  GET  /players/{playerId}/rank?leaderboard=global|{country}&period=weekly|all-time
  GET  /players/{playerId}  - Player profile with stats
"""

import logging
import uuid
from contextlib import asynccontextmanager
from datetime import datetime, timezone

from fastapi import FastAPI, HTTPException, Query
from azure.cosmos.exceptions import CosmosHttpResponseError

from cosmos_config import create_cosmos_client, initialize_database
from repositories import (
    PlayerRepository,
    ScoreRepository,
    LeaderboardRepository,
    _get_week_key,
    _now_iso,
)
from models import (
    SubmitScoreRequest,
    ScoreSubmissionResponse,
    LeaderboardResponse,
    LeaderboardEntryResponse,
    PlayerResponse,
    PlayerRankResponse,
)

logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger(__name__)

# ── Global state ──────────────────────────────────────────────
player_repo: PlayerRepository = None  # type: ignore[assignment]
score_repo: ScoreRepository = None  # type: ignore[assignment]
lb_repo: LeaderboardRepository = None  # type: ignore[assignment]


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Startup: create Cosmos client and initialize containers.
    Shutdown: (SDK client is lightweight, no explicit close needed for sync client.)
    """
    global player_repo, score_repo, lb_repo

    client = create_cosmos_client()
    containers = initialize_database(client)

    player_repo = PlayerRepository(containers["players"])
    score_repo = ScoreRepository(containers["scores"])
    lb_repo = LeaderboardRepository(containers["leaderboards"])

    logger.info("Cosmos DB containers initialized ✓")
    yield
    logger.info("Shutting down")


app = FastAPI(
    title="Gaming Leaderboard API",
    version="0.3.0",
    lifespan=lifespan,
)


# ── POST /scores ──────────────────────────────────────────────
@app.post("/scores", response_model=ScoreSubmissionResponse, status_code=201)
def submit_score(req: SubmitScoreRequest):
    """
    Submit a new score.
    
    Flow:
      1. Create score document in scores container.
      2. Read-modify-write player document (ETag concurrency, Rule 4.7).
      3. Upsert leaderboard entries (materialized views, Rule 9.1):
         - global weekly, global all-time
         - regional weekly, regional all-time
    """
    now = _now_iso()
    week_key = _get_week_key()
    score_id = str(uuid.uuid4())

    # 1. Create score document ──────────────────────────────────
    score_doc = {
        "id": score_id,
        "type": "score",
        "playerId": req.player_id,
        "score": req.score,
        "gameMode": req.game_mode,
        "weekKey": week_key,
        "submittedAt": now,
        "schemaVersion": 1,
    }
    score_repo.create_score(score_doc)

    # 2. Read-modify-write player stats (ETag concurrency) ─────
    MAX_RETRIES = 3
    updated_player = None
    for attempt in range(MAX_RETRIES):
        existing = player_repo.get_player(req.player_id)
        if existing:
            etag = existing.get("_etag")
            existing["totalGames"] = existing.get("totalGames", 0) + 1
            existing["totalScore"] = existing.get("totalScore", 0) + req.score
            if req.score > existing.get("bestScore", 0):
                existing["bestScore"] = req.score
            existing["lastActive"] = now
            try:
                updated_player = player_repo.upsert_player_with_etag(existing, etag)
                break
            except CosmosHttpResponseError as e:
                if e.status_code == 412 and attempt < MAX_RETRIES - 1:
                    logger.warning(f"ETag conflict on player {req.player_id}, retry {attempt+1}")
                    continue
                raise
        else:
            # New player
            new_player = {
                "id": req.player_id,
                "type": "player",
                "displayName": req.display_name,
                "country": req.country,
                "bestScore": req.score,
                "totalScore": req.score,
                "totalGames": 1,
                "lastActive": now,
                "createdAt": now,
                "schemaVersion": 1,
            }
            updated_player = player_repo.upsert_player(new_player)
            break

    if updated_player is None:
        raise HTTPException(status_code=409, detail="Failed to update player after retries")

    # 3. Upsert leaderboard entries (materialized view) ────────
    best_score = updated_player.get("bestScore", req.score)
    total_games = updated_player.get("totalGames", 1)
    country = updated_player.get("country", req.country)
    display_name = updated_player.get("displayName", req.display_name)

    leaderboard_keys = [
        f"global_{week_key}",
        "global_all-time",
        f"{country}_{week_key}",
        f"{country}_all-time",
    ]

    for lb_key in leaderboard_keys:
        lb_entry = {
            "id": req.player_id,  # one entry per player per leaderboard
            "type": "leaderboardEntry",
            "leaderboardKey": lb_key,
            "playerId": req.player_id,
            "displayName": display_name,
            "country": country,
            "bestScore": best_score,
            "totalGames": total_games,
            "lastUpdatedAt": now,
            "schemaVersion": 1,
        }
        lb_repo.upsert_entry(lb_entry)

    return ScoreSubmissionResponse(
        score_id=score_id,
        player_id=req.player_id,
        score=req.score,
        best_score=best_score,
        total_games=total_games,
        leaderboard_keys_updated=leaderboard_keys,
    )


# ── GET /leaderboards/global ─────────────────────────────────
@app.get("/leaderboards/global", response_model=LeaderboardResponse)
def get_global_leaderboard(
    period: str = Query("all-time", regex="^(weekly|all-time)$"),
    limit: int = Query(100, ge=1, le=100),
):
    """
    Get global top players.
    Rule 3.1: Single-partition query on leaderboard partition key.
    """
    if period == "weekly":
        lb_key = f"global_{_get_week_key()}"
    else:
        lb_key = "global_all-time"

    entries = lb_repo.get_top_entries(lb_key, limit=limit)

    return LeaderboardResponse(
        leaderboard_key=lb_key,
        period=period,
        entries=[
            LeaderboardEntryResponse(
                player_id=e["playerId"],
                display_name=e["displayName"],
                country=e["country"],
                best_score=e["bestScore"],
                total_games=e["totalGames"],
                rank=idx + 1,
            )
            for idx, e in enumerate(entries)
        ],
        total_count=len(entries),
    )


# ── GET /leaderboards/regional/{country} ─────────────────────
@app.get("/leaderboards/regional/{country}", response_model=LeaderboardResponse)
def get_regional_leaderboard(
    country: str,
    period: str = Query("all-time", regex="^(weekly|all-time)$"),
    limit: int = Query(100, ge=1, le=100),
):
    """
    Get regional top players for a specific country.
    Rule 2.7: Synthetic partition key = "{country}_{period/weekKey}".
    """
    if period == "weekly":
        lb_key = f"{country}_{_get_week_key()}"
    else:
        lb_key = f"{country}_all-time"

    entries = lb_repo.get_top_entries(lb_key, limit=limit)

    return LeaderboardResponse(
        leaderboard_key=lb_key,
        period=period,
        entries=[
            LeaderboardEntryResponse(
                player_id=e["playerId"],
                display_name=e["displayName"],
                country=e["country"],
                best_score=e["bestScore"],
                total_games=e["totalGames"],
                rank=idx + 1,
            )
            for idx, e in enumerate(entries)
        ],
        total_count=len(entries),
    )


# ── GET /players/{playerId}/rank ──────────────────────────────
@app.get("/players/{player_id}/rank", response_model=PlayerRankResponse)
def get_player_rank(
    player_id: str,
    leaderboard: str = Query("global", description="'global' or a country code like 'US'"),
    period: str = Query("all-time", regex="^(weekly|all-time)$"),
):
    """
    Get player rank + nearby players (±10).
    Rule 9.2: COUNT-based ranking — counts players with higher score.
    """
    if period == "weekly":
        lb_key = f"{leaderboard}_{_get_week_key()}"
    else:
        lb_key = f"{leaderboard}_all-time"

    # Get the player's leaderboard entry
    player_entry = lb_repo.get_player_entry(lb_key, player_id)
    if not player_entry:
        raise HTTPException(status_code=404, detail=f"Player {player_id} not found in leaderboard {lb_key}")

    player_score = player_entry["bestScore"]

    # COUNT-based rank (Rule 9.2)
    rank = lb_repo.get_player_rank(lb_key, player_score)

    # Get nearby players
    nearby_entries = lb_repo.get_nearby_players(lb_key, player_score, range_size=10)

    # Calculate ranks for nearby players based on their position relative to this group
    # We need the rank of the top player in the group
    if nearby_entries:
        top_score = nearby_entries[0]["bestScore"]
        top_rank = lb_repo.get_player_rank(lb_key, top_score)
    else:
        top_rank = rank

    return PlayerRankResponse(
        player_id=player_id,
        leaderboard_key=lb_key,
        rank=rank,
        best_score=player_score,
        nearby_players=[
            LeaderboardEntryResponse(
                player_id=e["playerId"],
                display_name=e["displayName"],
                country=e["country"],
                best_score=e["bestScore"],
                total_games=e["totalGames"],
                rank=top_rank + idx,
            )
            for idx, e in enumerate(nearby_entries)
        ],
    )


# ── GET /players/{playerId} ──────────────────────────────────
@app.get("/players/{player_id}", response_model=PlayerResponse)
def get_player_profile(player_id: str):
    """
    Get player profile with aggregated stats.
    1 RU point read from players container.
    """
    player = player_repo.get_player(player_id)
    if not player:
        raise HTTPException(status_code=404, detail=f"Player {player_id} not found")

    return PlayerResponse(
        id=player["id"],
        display_name=player.get("displayName", ""),
        country=player.get("country", ""),
        best_score=player.get("bestScore", 0),
        total_score=player.get("totalScore", 0),
        total_games=player.get("totalGames", 0),
        last_active=player.get("lastActive", ""),
        created_at=player.get("createdAt", ""),
    )


# ── Health check ──────────────────────────────────────────────
@app.get("/health")
def health_check():
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
