"""
Cosmos DB client configuration and initialization.

Applies Cosmos DB best practices:
- Rule 4.17: Reuse CosmosClient as singleton
- Rule 4.6: Configure SSL and connection mode for emulator (Gateway mode, disable SSL verification)
- Rule 4.12: Configure local dev environment to avoid cloud connection conflicts
- Rule 5.1: Use composite indexes for ORDER BY (bestScore DESC)
- Rule 5.2: Exclude unused index paths to reduce write RU
"""

import os
import logging
from azure.cosmos import CosmosClient, PartitionKey
from azure.cosmos.exceptions import CosmosResourceExistsError
from dotenv import load_dotenv
import urllib3

logger = logging.getLogger(__name__)

# Rule 4.12: Force .env values to override system environment variables
load_dotenv(override=True)

COSMOS_ENDPOINT = os.getenv("COSMOS_ENDPOINT", "https://localhost:8081")
COSMOS_KEY = os.getenv(
    "COSMOS_KEY",
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw=="
)
COSMOS_DATABASE = os.getenv("COSMOS_DATABASE", "gaming-leaderboard")

# Rule 4.6: Suppress SSL warnings for local emulator development only
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)


def _is_emulator(endpoint: str) -> bool:
    """Check if endpoint is the local Cosmos DB Emulator."""
    return "localhost" in endpoint or "127.0.0.1" in endpoint


def create_cosmos_client() -> CosmosClient:
    """
    Create and return a singleton-like CosmosClient.
    
    Rule 4.17: Reuse CosmosClient as singleton
    Rule 4.6: Gateway mode + disable SSL verification for emulator
    """
    is_emulator = _is_emulator(COSMOS_ENDPOINT)
    
    # Log endpoint (never log the key!) - Rule 4.12
    logger.info(f"Connecting to Cosmos DB at: {COSMOS_ENDPOINT}")
    if is_emulator:
        logger.info("Using Cosmos DB Emulator (Gateway mode, SSL disabled)")
    
    # Rule 4.6: Python SDK uses Gateway mode by default - correct for emulator
    # For production, Direct mode would be preferred but Python SDK defaults to Gateway
    client = CosmosClient(
        url=COSMOS_ENDPOINT,
        credential=COSMOS_KEY,
        connection_verify=not is_emulator  # Rule 4.6: Disable SSL verification for emulator
    )
    
    return client


def initialize_database(client: CosmosClient):
    """
    Initialize database and containers with proper configuration.
    
    Container design:
    - players: Partition key /id (point reads by player ID)
    - scores: Partition key /playerId (query scores per player)
    - leaderboards: Partition key /leaderboardKey (single-partition leaderboard queries)
    
    Rule 2.4: High-cardinality partition keys
    Rule 2.6: Align partition key with query patterns
    Rule 5.1: Composite indexes for ORDER BY on leaderboards
    Rule 5.2: Exclude unused index paths
    """
    database = client.create_database_if_not_exists(id=COSMOS_DATABASE)
    logger.info(f"Database '{COSMOS_DATABASE}' ready")

    # --- Players container ---
    # Partition key: /id (player ID) for efficient point reads
    try:
        players_container = database.create_container(
            id="players",
            partition_key=PartitionKey(path="/id"),
            indexing_policy={
                "indexingMode": "consistent",
                "automatic": True,
                "includedPaths": [
                    {"path": "/country/?"},
                    {"path": "/bestScore/?"},
                    {"path": "/totalGames/?"},
                ],
                "excludedPaths": [
                    {"path": "/*"}
                ]
            }
        )
        logger.info("Created container: players")
    except CosmosResourceExistsError:
        players_container = database.get_container_client("players")
        logger.info("Container 'players' already exists")

    # --- Scores container ---
    # Partition key: /playerId for per-player score queries
    try:
        scores_container = database.create_container(
            id="scores",
            partition_key=PartitionKey(path="/playerId"),
            indexing_policy={
                "indexingMode": "consistent",
                "automatic": True,
                "includedPaths": [
                    {"path": "/weekKey/?"},
                    {"path": "/score/?"},
                    {"path": "/submittedAt/?"},
                ],
                "excludedPaths": [
                    {"path": "/*"}
                ]
            }
        )
        logger.info("Created container: scores")
    except CosmosResourceExistsError:
        scores_container = database.get_container_client("scores")
        logger.info("Container 'scores' already exists")

    # --- Leaderboards container ---
    # Partition key: /leaderboardKey (synthetic key: "global_2026-W09", "US_all-time")
    # Rule 5.1: Composite index for ORDER BY bestScore DESC
    try:
        leaderboards_container = database.create_container(
            id="leaderboards",
            partition_key=PartitionKey(path="/leaderboardKey"),
            indexing_policy={
                "indexingMode": "consistent",
                "automatic": True,
                "includedPaths": [
                    {"path": "/bestScore/?"},
                    {"path": "/playerId/?"},
                    {"path": "/type/?"},
                    {"path": "/lastUpdatedAt/?"},
                ],
                "excludedPaths": [
                    {"path": "/*"}
                ],
                "compositeIndexes": [
                    [
                        {"path": "/bestScore", "order": "descending"},
                        {"path": "/lastUpdatedAt", "order": "ascending"}
                    ]
                ]
            }
        )
        logger.info("Created container: leaderboards")
    except CosmosResourceExistsError:
        leaderboards_container = database.get_container_client("leaderboards")
        logger.info("Container 'leaderboards' already exists")

    return {
        "database": database,
        "players": players_container,
        "scores": scores_container,
        "leaderboards": leaderboards_container,
    }
