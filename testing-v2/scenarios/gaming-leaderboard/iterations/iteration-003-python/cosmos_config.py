"""
Cosmos DB async client configuration and container initialization.

Best practices applied:
- Rule 4.1:  Use async APIs for better throughput
- Rule 4.18: Reuse CosmosClient as singleton
- Rule 4.6:  Gateway mode + disable SSL for emulator
- Rule 4.15: Include aiohttp for async SDK
- Rule 2.4:  High-cardinality partition keys (/playerId)
- Rule 2.6:  Align partition key with query patterns
- Rule 5.1:  Composite index directions match ORDER BY
- Rule 5.2:  Composite indexes for ORDER BY on leaderboards
"""

import os
import logging

from azure.cosmos.aio import CosmosClient
from azure.cosmos import PartitionKey
import urllib3

logger = logging.getLogger(__name__)

# Rule 4.6: Suppress SSL warnings for local emulator
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

COSMOS_ENDPOINT = os.environ.get("COSMOS_ENDPOINT", "https://localhost:8081")
COSMOS_KEY = os.environ.get(
    "COSMOS_KEY",
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==",
)
DATABASE_NAME = os.environ.get("COSMOS_DATABASE", "gaming-leaderboard")

_client: CosmosClient | None = None


def _is_emulator(endpoint: str) -> bool:
    return "localhost" in endpoint or "127.0.0.1" in endpoint


async def get_cosmos_client() -> CosmosClient:
    """Return a singleton async CosmosClient (Rule 4.18)."""
    global _client
    if _client is None:
        is_emulator = _is_emulator(COSMOS_ENDPOINT)
        logger.info("Connecting to Cosmos DB at %s (emulator=%s)", COSMOS_ENDPOINT, is_emulator)
        _client = CosmosClient(
            url=COSMOS_ENDPOINT,
            credential=COSMOS_KEY,
            connection_verify=not is_emulator,  # Rule 4.6
        )
    return _client


async def initialize_containers():
    """
    Create database and containers with proper partition keys and indexes.

    Container design:
      players  – partition key /playerId  (point reads + cross-partition leaderboard)
      scores   – partition key /playerId  (per-player score history)
    """
    client = await get_cosmos_client()
    database = await client.create_database_if_not_exists(id=DATABASE_NAME)
    logger.info("Database '%s' ready", DATABASE_NAME)

    # --- Players container ---
    # Partition key: /playerId (NOT /id – Rule 2.4 high cardinality)
    # Composite index: bestScore DESC, displayName ASC for leaderboard queries (Rule 5.1, 5.2)
    players = await database.create_container_if_not_exists(
        id="players",
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy={
            "indexingMode": "consistent",
            "automatic": True,
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [{"path": "/\"_etag\"/?"}],
            "compositeIndexes": [
                [
                    {"path": "/bestScore", "order": "descending"},
                    {"path": "/displayName", "order": "ascending"},
                ]
            ],
        },
    )
    logger.info("Container 'players' ready")

    # --- Scores container ---
    # Partition key: /playerId (per-player score queries – Rule 2.6)
    scores = await database.create_container_if_not_exists(
        id="scores",
        partition_key=PartitionKey(path="/playerId"),
        indexing_policy={
            "indexingMode": "consistent",
            "automatic": True,
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [{"path": "/\"_etag\"/?"}],
        },
    )
    logger.info("Container 'scores' ready")

    return {"database": database, "players": players, "scores": scores}


async def close_cosmos_client():
    global _client
    if _client is not None:
        await _client.close()
        _client = None
