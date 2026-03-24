"""
Order Status Enum - Rule 4.10: Enum String Serialization

Uses `class OrderStatus(str, Enum)` pattern to ensure enums serialize
as strings in both API responses and Cosmos DB storage.
"""
from enum import Enum


class OrderStatus(str, Enum):
    """
    Order status enum that serializes as string.
    
    By inheriting from `str`, the enum values serialize as their string
    values ("Pending", "Shipped", etc.) rather than integers (0, 1, etc.).
    This ensures consistency between:
    - Cosmos DB storage
    - API responses
    - Query filters
    """
    PENDING = "Pending"
    CONFIRMED = "Confirmed"
    SHIPPED = "Shipped"
    DELIVERED = "Delivered"
    CANCELLED = "Cancelled"
