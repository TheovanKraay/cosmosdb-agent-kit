"""
Order Models - Data model with Cosmos DB best practices

Rules Applied:
- Rule 1.1: Embedded OrderItems in Order (data retrieved together)
- Rule 1.5: Schema versioning with schemaVersion property
- Rule 1.6: Type discriminator with type property
- Rule 2.1: High-cardinality partition key (customerId)
- Rule 4.10: Enum string serialization via OrderStatus(str, Enum)
"""
from datetime import datetime
from typing import Optional
from pydantic import BaseModel, Field
from .order_status import OrderStatus


class OrderItem(BaseModel):
    """
    Order line item - embedded within Order document.
    Rule 1.1: Embed related data retrieved together.
    """
    productId: str
    productName: str
    quantity: int = Field(ge=1)
    unitPrice: float = Field(ge=0)
    totalPrice: float = Field(ge=0)


class CustomerInfo(BaseModel):
    """
    Denormalized customer information embedded in order.
    Rule 1.2: Denormalize for read-heavy workloads.
    """
    customerId: str
    name: str
    email: str


class Order(BaseModel):
    """
    Order document with embedded items and customer info.
    
    Partition Key: customerId (Rule 2.1 - high cardinality)
    """
    id: str
    customerId: str  # Partition key
    
    # Rule 1.6: Type discriminator for polymorphic queries
    type: str = "order"
    
    # Rule 1.5: Schema versioning
    schemaVersion: int = 1
    
    # Rule 4.10: Enum as string (OrderStatus inherits from str)
    status: OrderStatus = OrderStatus.PENDING
    
    # Denormalized customer info (Rule 1.2)
    customer: CustomerInfo
    
    # Rule 1.1: Embedded order items
    items: list[OrderItem] = []
    
    # Calculated totals
    subtotal: float = 0.0
    tax: float = 0.0
    total: float = 0.0
    
    # Timestamps
    createdAt: datetime = Field(default_factory=datetime.utcnow)
    updatedAt: datetime = Field(default_factory=datetime.utcnow)
    
    # Optional tracking
    shippedAt: Optional[datetime] = None
    deliveredAt: Optional[datetime] = None

    class Config:
        use_enum_values = True  # Serialize enums as their values


class OrderSummary(BaseModel):
    """
    Projected order summary for list views.
    Rule 3.2: Project only needed fields.
    """
    id: str
    customerId: str
    status: OrderStatus
    total: float
    createdAt: datetime
    itemCount: int

    class Config:
        use_enum_values = True


class CreateOrderRequest(BaseModel):
    """Request model for creating an order."""
    customerId: str
    customerName: str
    customerEmail: str
    items: list[OrderItem]


class UpdateStatusRequest(BaseModel):
    """Request model for updating order status."""
    status: OrderStatus

    class Config:
        use_enum_values = True


class PagedResult(BaseModel):
    """
    Paged result with continuation token.
    Rule 3.3: Use continuation tokens for pagination.
    """
    items: list[Order | OrderSummary]
    continuationToken: Optional[str] = None
    hasMore: bool = False
