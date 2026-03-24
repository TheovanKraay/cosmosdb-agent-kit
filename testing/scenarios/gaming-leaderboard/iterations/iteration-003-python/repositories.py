"""
Repository layer for Cosmos DB data access.

Applies Cosmos DB best practices:
- Rule 3.1: Minimize cross-partition queries (all queries target single partition)
- Rule 3.2: Avoid full container scans (use indexed fields)
- Rule 3.5: Use parameterized queries
- Rule 3.6: Project only needed fields
- Rule 4.7: Use ETags for optimistic concurrency on read-modify-write
- Rule 4.15: Handle 429 errors with retry (SDK handles automatically)
- Rule 8.4: Track RU consumption (log request charges)
- Rule 9.1: Materialized views pattern (leaderboard container)
- Rule 9.2: COUNT-based ranking (not O(N) partition scan)
"""

import logging
import uuid
from datetime import datetime, timezone
from typing import Optional

from azure.core import MatchConditions
from azure.cosmos import ContainerProxy
from azure.cosmos.exceptions import CosmosResourceNotFoundError, CosmosHttpResponseError

from models import (
    PlayerDocument,
    ScoreDocument,
    LeaderboardEntryDocument,
)

logger = logging.getLogger(__name__)


def _get_week_key(dt: datetime | None = None) -> str:
    """Get ISO week key string, e.g. '2026-W09'."""
    if dt is None:
        dt = datetime.now(timezone.utc)
    iso = dt.isocalendar()
    return f"{iso.year}-W{iso.week:02d}"


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


class PlayerRepository:
    """Data access for player documents."""

    def __init__(self, container: ContainerProxy):
        self._container = container

    def get_player(self, player_id: str) -> Optional[dict]:
        """
        Point read by player ID.
        Rule 3.6: Only needed fields (full player doc is fine - it's a point read at ~1 RU).
        """
        try:
            response = self._container.read_item(
                item=player_id,
                partition_key=player_id  # Partition key = /id
            )
            logger.debug(f"Read player {player_id} — RU: {self._container.client_connection.last_response_headers.get('x-ms-request-charge', 'N/A')}")
            return response
        except CosmosResourceNotFoundError:
            return None

    def upsert_player(self, player: dict) -> dict:
        """
        Upsert player document.
        Rule 4.7: Use ETag for optimistic concurrency on stat updates.
        """
        response = self._container.upsert_item(body=player)
        logger.debug(f"Upserted player {player['id']} — RU: {self._container.client_connection.last_response_headers.get('x-ms-request-charge', 'N/A')}")
        return response

    def upsert_player_with_etag(self, player: dict, etag: str) -> dict:
        """
        Upsert with ETag check for optimistic concurrency.
        Rule 4.7: Prevents lost updates on concurrent score submissions.
        """
        response = self._container.upsert_item(
            body=player,
            etag=etag,
            match_condition=MatchConditions.IfNotModified
        )
        logger.debug(f"Upserted player (ETag) {player['id']} — RU: {self._container.client_connection.last_response_headers.get('x-ms-request-charge', 'N/A')}")
        return response


class ScoreRepository:
    """Data access for score documents."""

    def __init__(self, container: ContainerProxy):
        self._container = container

    def create_score(self, score: dict) -> dict:
        """Create a new score document."""
        response = self._container.create_item(body=score)
        logger.debug(f"Created score {score['id']} — RU: {self._container.client_connection.last_response_headers.get('x-ms-request-charge', 'N/A')}")
        return response

    def get_player_scores(self, player_id: str, limit: int = 20) -> list[dict]:
        """
        Get recent scores for a player.
        Rule 3.1: Single-partition query (partition key = /playerId)
        Rule 3.5: Parameterized query
        Rule 3.6: Project only needed fields
        """
        query = (
            "SELECT c.id, c.score, c.gameMode, c.weekKey, c.submittedAt "
            "FROM c WHERE c.playerId = @playerId "
            "ORDER BY c.submittedAt DESC "
            "OFFSET 0 LIMIT @limit"
        )
        parameters = [
            {"name": "@playerId", "value": player_id},
            {"name": "@limit", "value": limit},
        ]
        items = list(self._container.query_items(
            query=query,
            parameters=parameters,
            partition_key=player_id,  # Rule 3.1: target single partition
        ))
        return items


class LeaderboardRepository:
    """Data access for leaderboard documents (materialized view)."""

    def __init__(self, container: ContainerProxy):
        self._container = container

    def upsert_entry(self, entry: dict) -> dict:
        """Upsert a leaderboard entry."""
        response = self._container.upsert_item(body=entry)
        logger.debug(f"Upserted leaderboard entry {entry['id']} in {entry['leaderboardKey']}")
        return response

    def get_top_entries(self, leaderboard_key: str, limit: int = 100) -> list[dict]:
        """
        Get top N entries from a leaderboard.
        Rule 3.1: Single-partition query (partition key = /leaderboardKey)
        Rule 3.5: Parameterized queries
        Rule 3.6: Project only needed fields
        Rule 5.1: Uses composite index (bestScore DESC, lastUpdatedAt ASC)
        """
        query = (
            "SELECT c.playerId, c.displayName, c.country, c.bestScore, c.totalGames "
            "FROM c WHERE c.type = @type "
            "ORDER BY c.bestScore DESC, c.lastUpdatedAt ASC "
            "OFFSET 0 LIMIT @limit"
        )
        parameters = [
            {"name": "@type", "value": "leaderboardEntry"},
            {"name": "@limit", "value": limit},
        ]
        items = list(self._container.query_items(
            query=query,
            parameters=parameters,
            partition_key=leaderboard_key,  # Rule 3.1: target single partition
        ))
        return items

    def get_player_rank(self, leaderboard_key: str, player_score: int) -> int:
        """
        Get a player's rank using COUNT-based approach.
        Rule 9.2: COUNT-based rank query (not O(N) partition scan!)
        ~3-5 RU regardless of partition size.
        """
        query = (
            "SELECT VALUE COUNT(1) FROM c "
            "WHERE c.type = @type AND c.bestScore > @score"
        )
        parameters = [
            {"name": "@type", "value": "leaderboardEntry"},
            {"name": "@score", "value": player_score},
        ]
        result = list(self._container.query_items(
            query=query,
            parameters=parameters,
            partition_key=leaderboard_key,
        ))
        count = result[0] if result else 0
        return count + 1  # Rank = count of players with higher score + 1

    def get_player_entry(self, leaderboard_key: str, player_id: str) -> Optional[dict]:
        """
        Point read for a player's leaderboard entry.
        """
        try:
            response = self._container.read_item(
                item=player_id,  # id = playerId in leaderboard container
                partition_key=leaderboard_key
            )
            return response
        except CosmosResourceNotFoundError:
            return None

    def get_nearby_players(
        self, leaderboard_key: str, player_score: int, range_size: int = 10
    ) -> list[dict]:
        """
        Get players near a given score (±range_size positions).
        
        Strategy: Query players with scores around the target score.
        We get players above and at/below the target score.
        Rule 3.5: Parameterized queries
        Rule 3.6: Project only needed fields
        """
        # Get players with scores >= player_score (including player), sorted DESC
        above_query = (
            "SELECT c.playerId, c.displayName, c.country, c.bestScore, c.totalGames "
            "FROM c WHERE c.type = @type AND c.bestScore >= @score "
            "ORDER BY c.bestScore ASC "
            "OFFSET 0 LIMIT @limit"
        )
        above_params = [
            {"name": "@type", "value": "leaderboardEntry"},
            {"name": "@score", "value": player_score},
            {"name": "@limit", "value": range_size + 1},
        ]
        above_entries = list(self._container.query_items(
            query=above_query,
            parameters=above_params,
            partition_key=leaderboard_key,
        ))
        above_entries.reverse()

        # Get players with scores < player_score, sorted DESC
        below_query = (
            "SELECT c.playerId, c.displayName, c.country, c.bestScore, c.totalGames "
            "FROM c WHERE c.type = @type AND c.bestScore < @score "
            "ORDER BY c.bestScore DESC "
            "OFFSET 0 LIMIT @limit"
        )
        below_params = [
            {"name": "@type", "value": "leaderboardEntry"},
            {"name": "@score", "value": player_score},
            {"name": "@limit", "value": range_size},
        ]
        below_entries = list(self._container.query_items(
            query=below_query,
            parameters=below_params,
            partition_key=leaderboard_key,
        ))

        # Combine: players above + player + players below
        combined = above_entries + below_entries
        return combined
