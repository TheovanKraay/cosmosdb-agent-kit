"""
E-Commerce Order API - FastAPI Application

Azure Cosmos DB Best Practices Applied:
- Rule 4.1: Singleton CosmosClient
- Rule 4.10: Enum string serialization via OrderStatus(str, Enum)
- Rule 1.1: Embedded order items
- Rule 2.1: High-cardinality partition key (customerId)
"""
import ssl
from contextlib import asynccontextmanager
from fastapi import FastAPI

from .config import ensure_database_and_container, close_async_client
from .routers import orders_router


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Application lifespan handler.
    Initializes database/container on startup.
    """
    # Startup: ensure database and container exist
    print("Initializing Cosmos DB connection...")
    
    # Disable SSL verification for emulator (development only!)
    ssl._create_default_https_context = ssl._create_unverified_context
    
    ensure_database_and_container()
    print("Cosmos DB initialized successfully!")
    
    yield
    
    # Shutdown: close async client
    await close_async_client()


app = FastAPI(
    title="E-Commerce Order API",
    description="Order management API using Azure Cosmos DB with best practices",
    version="1.0.0",
    lifespan=lifespan,
)

# Register routers
app.include_router(orders_router)


@app.get("/health")
def health_check():
    """Health check endpoint."""
    return {"status": "healthy", "service": "ecommerce-order-api"}


@app.get("/")
def root():
    """Root endpoint with API info."""
    return {
        "service": "E-Commerce Order API",
        "version": "1.0.0",
        "docs": "/docs",
        "cosmos_db_rules_applied": [
            "4.1 - Singleton CosmosClient",
            "4.10 - Enum string serialization",
            "1.1 - Embedded order items",
            "2.1 - High-cardinality partition key (customerId)",
            "3.1 - Single-partition queries where possible",
            "3.5 - Parameterized queries",
        ],
    }
