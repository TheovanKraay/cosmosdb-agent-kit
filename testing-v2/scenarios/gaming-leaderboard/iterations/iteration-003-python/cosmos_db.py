"""
Cosmos DB client management module.

Best practices applied:
- Singleton CosmosClient (reused across requests)
- Async SDK (azure.cosmos.aio) for better throughput
- Gateway mode for emulator compatibility
- SSL verification disabled for emulator
- Database and containers created on startup if missing
- Composite indexes for ORDER BY queries (bestScore DESC, displayName ASC)
- Partition key aligned with query patterns (playerId)
- Indexing policy with root path /* and custom excluded paths
- Excluded unused index paths to reduce write RU cost
"""

import os
import urllib3
from azure.cosmos.aio import CosmosClient
from azure.cosmos import PartitionKey

# Suppress SSL warnings for emulator
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# Configuration from environment
COSMOS_ENDPOINT = os.environ.get("COSMOS_ENDPOINT", "https://localhost:8081")
COSMOS_KEY = os.environ.get(
    "COSMOS_KEY",
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==",
)
DATABASE_NAME = os.environ.get("COSMOS_DATABASE", "gaming-leaderboard")


class CosmosManager:
    """Singleton manager for Cosmos DB client and containers."""

    def __init__(self):
        # Gateway mode is default for Python SDK; disable SSL for emulator
        self.client = CosmosClient(
            url=COSMOS_ENDPOINT,
            credential=COSMOS_KEY,
            connection_verify=False,
        )
        self.database = None
        self.players_container = None
        self.scores_container = None

    async def initialize(self):
        """Create database and containers if they don't exist."""
        self.database = await self.client.create_database_if_not_exists(id=DATABASE_NAME)

        # Players container - partition key on playerId for point reads
        # Composite index for leaderboard ordering (bestScore DESC, displayName ASC)
        # Exclude paths not used in queries to reduce write RU
        players_indexing_policy = {
            "indexingMode": "consistent",
            "automatic": True,
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [
                {"path": "/\"_etag\"/?"},
                {"path": "/schemaVersion/?"},
                {"path": "/averageScore/?"},
            ],
            "compositeIndexes": [
                [
                    {"path": "/bestScore", "order": "descending"},
                    {"path": "/displayName", "order": "ascending"},
                ]
            ],
        }

        self.players_container = await self.database.create_container_if_not_exists(
            id="players",
            partition_key=PartitionKey(path="/playerId"),
            indexing_policy=players_indexing_policy,
        )

        # Scores container - partition key on playerId for efficient per-player queries
        # Exclude paths not used in queries
        scores_indexing_policy = {
            "indexingMode": "consistent",
            "automatic": True,
            "includedPaths": [{"path": "/*"}],
            "excludedPaths": [
                {"path": "/\"_etag\"/?"},
                {"path": "/schemaVersion/?"},
                {"path": "/gameMode/?"},
            ],
            "compositeIndexes": [
                [
                    {"path": "/playerId", "order": "ascending"},
                    {"path": "/timestamp", "order": "descending"},
                ]
            ],
        }

        self.scores_container = await self.database.create_container_if_not_exists(
            id="scores",
            partition_key=PartitionKey(path="/playerId"),
            indexing_policy=scores_indexing_policy,
        )

    async def close(self):
        """Close the Cosmos DB client."""
        await self.client.close()


# Singleton instance
_manager = None


def get_cosmos_manager() -> CosmosManager:
    """Return the singleton CosmosManager instance."""
    global _manager
    if _manager is None:
        _manager = CosmosManager()
    return _manager
