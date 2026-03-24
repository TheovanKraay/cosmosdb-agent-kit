"""
Pydantic models for the Gaming Leaderboard API.

Data Model Design (applying Cosmos DB best practices):
- Rule 1.2: Denormalize for read-heavy workloads (leaderboard reads >> writes)
- Rule 1.3: Embed related data retrieved together (player stats in player doc)
- Rule 1.11: Use type discriminators for polymorphic data in same container
- Rule 1.10: Version document schemas
- Rule 1.4: Follow ID value constraints (alphanumeric, < 1023 bytes)
- Rule 2.6: Align partition key with query patterns
- Rule 2.7: Create synthetic partition keys when needed (leaderboardKey)
"""

from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime, timezone
from enum import Enum


# --- Enums (Rule 4.16: consistent enum serialization) ---

class EntityType(str, Enum):
    """Type discriminator for polymorphic documents in shared containers."""
    PLAYER = "player"
    SCORE = "score"
    LEADERBOARD_ENTRY = "leaderboardEntry"


# --- Request Models ---

class SubmitScoreRequest(BaseModel):
    """Request body for submitting a new score."""
    player_id: str = Field(..., min_length=1, max_length=100, description="Player identifier")
    display_name: str = Field(..., min_length=1, max_length=50, description="Display name")
    country: str = Field(..., min_length=2, max_length=2, description="ISO 3166-1 alpha-2 country code")
    score: int = Field(..., gt=0, description="Game score (positive integer)")
    game_mode: str = Field(default="standard", max_length=30, description="Game mode")


# --- Cosmos DB Document Models ---

class PlayerDocument(BaseModel):
    """
    Player profile with cumulative stats.
    Container: players
    Partition key: /id (player ID - point reads are primary access pattern)
    
    Rule 1.3: Embed stats (always accessed with player profile)
    Rule 1.10: Schema versioning
    Rule 1.11: Type discriminator
    """
    id: str
    type: str = EntityType.PLAYER.value
    schema_version: int = Field(default=1, alias="schemaVersion")
    display_name: str = Field(..., alias="displayName")
    country: str
    total_games: int = Field(default=0, alias="totalGames")
    best_score: int = Field(default=0, alias="bestScore")
    total_score: int = Field(default=0, alias="totalScore")
    average_score: float = Field(default=0.0, alias="averageScore")
    last_played_at: Optional[str] = Field(default=None, alias="lastPlayedAt")
    created_at: str = Field(default=None, alias="createdAt")
    _etag: Optional[str] = None

    model_config = {"populate_by_name": True}


class ScoreDocument(BaseModel):
    """
    Individual game score record.
    Container: scores
    Partition key: /playerId (scores retrieved per-player)
    
    Rule 2.6: Partition by playerId for player-specific score queries
    Rule 1.11: Type discriminator
    """
    id: str
    type: str = EntityType.SCORE.value
    player_id: str = Field(..., alias="playerId")
    display_name: str = Field(..., alias="displayName")
    country: str
    score: int
    game_mode: str = Field(default="standard", alias="gameMode")
    week_key: str = Field(..., alias="weekKey")  # e.g., "2026-W09"
    submitted_at: str = Field(..., alias="submittedAt")

    model_config = {"populate_by_name": True}


class LeaderboardEntryDocument(BaseModel):
    """
    Denormalized leaderboard entry (materialized view pattern).
    Container: leaderboards
    Partition key: /leaderboardKey (synthetic key for single-partition leaderboard queries)
    
    Rule 9.1: Change Feed / materialized views for cross-partition query optimization
    Rule 2.7: Synthetic partition key (leaderboardKey = "global_{weekKey}" or "{country}_{weekKey}")
    Rule 1.2: Denormalize player info for read efficiency
    """
    id: str  # playerId (unique per player per leaderboard partition)
    type: str = EntityType.LEADERBOARD_ENTRY.value
    leaderboard_key: str = Field(..., alias="leaderboardKey")  # Partition key
    player_id: str = Field(..., alias="playerId")
    display_name: str = Field(..., alias="displayName")
    country: str
    best_score: int = Field(default=0, alias="bestScore")
    total_games: int = Field(default=0, alias="totalGames")
    last_updated_at: str = Field(default=None, alias="lastUpdatedAt")

    model_config = {"populate_by_name": True}


# --- Response Models ---

class PlayerResponse(BaseModel):
    """Player profile response."""
    id: str
    display_name: str
    country: str
    total_games: int
    best_score: int
    total_score: int
    last_active: Optional[str] = None
    created_at: Optional[str] = None


class LeaderboardEntryResponse(BaseModel):
    """Single leaderboard entry in API responses."""
    rank: int
    player_id: str
    display_name: str
    country: str
    best_score: int
    total_games: int


class LeaderboardResponse(BaseModel):
    """Leaderboard API response."""
    leaderboard_key: str
    period: str
    entries: list[LeaderboardEntryResponse]
    total_count: int


class PlayerRankResponse(BaseModel):
    """Player's rank with surrounding players."""
    player_id: str
    leaderboard_key: str
    rank: int
    best_score: int
    nearby_players: list[LeaderboardEntryResponse]


class ScoreSubmissionResponse(BaseModel):
    """Response after submitting a score."""
    score_id: str
    player_id: str
    score: int
    best_score: int
    total_games: int
    leaderboard_keys_updated: list[str]
    message: str = "Score submitted successfully"
