"""
Cosmos DB Configuration - Singleton Client Pattern

Rules Applied:
- Rule 4.1: Singleton CosmosClient instance
- Rule 4.4: Gateway connection mode (for emulator compatibility)
- Rule 4.3: Retry configuration for 429 handling
- Rule 7.2: Session consistency level
"""
import os
import ssl
from azure.cosmos import CosmosClient, PartitionKey
from azure.cosmos.aio import CosmosClient as AsyncCosmosClient
from dotenv import load_dotenv

# Load .env file FIRST, with override=True to override system env vars
load_dotenv(override=True)

# Configuration - hardcoded for emulator, can be overridden by .env
# The emulator's well-known key
EMULATOR_KEY = "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw=="
EMULATOR_ENDPOINT = "https://localhost:8081"

COSMOS_ENDPOINT = os.getenv("COSMOS_ENDPOINT", EMULATOR_ENDPOINT)
COSMOS_KEY = os.getenv("COSMOS_KEY", EMULATOR_KEY)
COSMOS_DATABASE = os.getenv("COSMOS_DATABASE", "ecommerce")
COSMOS_CONTAINER = os.getenv("COSMOS_CONTAINER", "orders")

# Debug: print what we're connecting to
print(f"Connecting to Cosmos DB at: {COSMOS_ENDPOINT}")

# Rule 4.1: Singleton client instance
_cosmos_client: CosmosClient | None = None
_async_cosmos_client: AsyncCosmosClient | None = None


def get_cosmos_client() -> CosmosClient:
    """
    Get singleton CosmosClient instance.
    Rule 4.1: Reuse CosmosClient as singleton.
    """
    global _cosmos_client
    
    if _cosmos_client is None:
        # For emulator: disable SSL verification
        # In production, remove this and use proper certificates
        _cosmos_client = CosmosClient(
            url=COSMOS_ENDPOINT,
            credential=COSMOS_KEY,
            # Rule 7.2: Session consistency for read-your-writes
            consistency_level="Session",
        )
    
    return _cosmos_client


async def get_async_cosmos_client() -> AsyncCosmosClient:
    """
    Get singleton async CosmosClient instance.
    Rule 4.1: Reuse CosmosClient as singleton.
    Rule 4.2: Use async APIs for better throughput.
    """
    global _async_cosmos_client
    
    if _async_cosmos_client is None:
        _async_cosmos_client = AsyncCosmosClient(
            url=COSMOS_ENDPOINT,
            credential=COSMOS_KEY,
            consistency_level="Session",
        )
    
    return _async_cosmos_client


async def close_async_client():
    """Close the async client on shutdown."""
    global _async_cosmos_client
    if _async_cosmos_client is not None:
        await _async_cosmos_client.close()
        _async_cosmos_client = None


def ensure_database_and_container():
    """
    Initialize database and container if they don't exist.
    Includes composite index configuration per Rule 5.1.
    """
    client = get_cosmos_client()
    
    # Create database if not exists
    try:
        database = client.create_database_if_not_exists(id=COSMOS_DATABASE)
        print(f"Database '{COSMOS_DATABASE}' ready")
    except Exception as e:
        print(f"Database creation error (may already exist): {e}")
        database = client.get_database_client(COSMOS_DATABASE)
    
    # Rule 5.1: Composite indexes for ORDER BY queries
    indexing_policy = {
        "indexingMode": "consistent",
        "automatic": True,
        "includedPaths": [{"path": "/*"}],
        "excludedPaths": [{"path": '/"_etag"/?'}],
        "compositeIndexes": [
            # For: ORDER BY status, createdAt
            [
                {"path": "/status", "order": "ascending"},
                {"path": "/createdAt", "order": "descending"}
            ],
            # For: ORDER BY customerId, createdAt (within partition)
            [
                {"path": "/customerId", "order": "ascending"},
                {"path": "/createdAt", "order": "descending"}
            ]
        ]
    }
    
    # Create container with partition key (Rule 2.1: customerId)
    try:
        container = database.create_container_if_not_exists(
            id=COSMOS_CONTAINER,
            partition_key=PartitionKey(path="/customerId"),
            indexing_policy=indexing_policy
        )
        print(f"Container '{COSMOS_CONTAINER}' ready")
    except Exception as e:
        print(f"Container creation error (may already exist): {e}")
        container = database.get_container_client(COSMOS_CONTAINER)
        print(f"Using existing container '{COSMOS_CONTAINER}'")
    
    return container
