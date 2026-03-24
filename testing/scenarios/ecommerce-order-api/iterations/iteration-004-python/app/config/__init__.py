"""Config package."""
from .cosmos_config import (
    get_cosmos_client,
    get_async_cosmos_client,
    close_async_client,
    ensure_database_and_container,
    COSMOS_DATABASE,
    COSMOS_CONTAINER,
)

__all__ = [
    "get_cosmos_client",
    "get_async_cosmos_client",
    "close_async_client",
    "ensure_database_and_container",
    "COSMOS_DATABASE",
    "COSMOS_CONTAINER",
]
