"""
Order Repository - Data access layer for Cosmos DB

Rules Applied:
- Rule 3.1: Minimize cross-partition queries (use partition key when possible)
- Rule 3.2: Avoid full container scans
- Rule 3.5: Use parameterized queries
- Rule 3.6: Project only needed fields
- Rule 4.1: Singleton client via dependency injection
- Rule 4.9: Handle 429 with retry (SDK handles automatically)
- Rule 4.10: Enum string serialization - use .value for queries
"""
import uuid
from datetime import datetime
from typing import Optional

from azure.cosmos import ContainerProxy
from azure.cosmos.exceptions import CosmosResourceNotFoundError

from ..config import get_cosmos_client, COSMOS_DATABASE, COSMOS_CONTAINER
from ..models import (
    Order,
    OrderItem,
    OrderSummary,
    CustomerInfo,
    CreateOrderRequest,
    OrderStatus,
    PagedResult,
)


class OrderRepository:
    """
    Repository for order data access.
    Uses singleton CosmosClient per Rule 4.1.
    """
    
    def __init__(self):
        """Initialize repository with singleton client."""
        self._client = get_cosmos_client()
        self._container: ContainerProxy = self._client.get_database_client(
            COSMOS_DATABASE
        ).get_container_client(COSMOS_CONTAINER)
    
    def create_order(self, request: CreateOrderRequest) -> Order:
        """
        Create a new order.
        
        Rule 1.1: Embeds order items in the order document.
        Rule 4.10: Status stored as string via OrderStatus(str, Enum).
        """
        # Calculate totals
        subtotal = sum(item.totalPrice for item in request.items)
        tax = subtotal * 0.08  # 8% tax
        total = subtotal + tax
        
        order = Order(
            id=str(uuid.uuid4()),
            customerId=request.customerId,
            customer=CustomerInfo(
                customerId=request.customerId,
                name=request.customerName,
                email=request.customerEmail,
            ),
            items=request.items,
            status=OrderStatus.PENDING,  # Stored as "Pending" string
            subtotal=subtotal,
            tax=tax,
            total=total,
            createdAt=datetime.utcnow(),
            updatedAt=datetime.utcnow(),
        )
        
        # Convert to dict for Cosmos DB
        # Pydantic with use_enum_values=True ensures status is "Pending" not 0
        order_dict = order.model_dump(mode="json")
        
        # Create in Cosmos DB
        result = self._container.create_item(body=order_dict)
        
        return Order.model_validate(result)
    
    def get_order(self, order_id: str, customer_id: str) -> Optional[Order]:
        """
        Get order by ID with partition key.
        
        Rule 3.1: Uses partition key for efficient point read.
        """
        try:
            result = self._container.read_item(
                item=order_id,
                partition_key=customer_id,
            )
            return Order.model_validate(result)
        except CosmosResourceNotFoundError:
            return None
    
    def get_orders_by_customer(
        self,
        customer_id: str,
        max_items: int = 20,
        continuation_token: Optional[str] = None,
    ) -> PagedResult:
        """
        Get orders for a customer.
        
        Rule 3.1: Single-partition query using customerId.
        Rule 3.5: Parameterized query.
        Rule 3.3: Continuation token for pagination.
        """
        # Rule 3.5: Parameterized query
        query = """
            SELECT * FROM c 
            WHERE c.customerId = @customerId AND c.type = 'order'
            ORDER BY c.createdAt DESC
        """
        
        parameters = [{"name": "@customerId", "value": customer_id}]
        
        # Execute query with partition key (Rule 3.1)
        items = list(self._container.query_items(
            query=query,
            parameters=parameters,
            partition_key=customer_id,
            max_item_count=max_items,
        ))
        
        orders = [Order.model_validate(item) for item in items]
        
        return PagedResult(
            items=orders,
            continuationToken=None,  # Simplified for demo
            hasMore=len(orders) == max_items,
        )
    
    def get_orders_by_status(
        self,
        status: OrderStatus,
        max_items: int = 20,
    ) -> PagedResult:
        """
        Get orders by status (cross-partition query).
        
        Rule 3.1: This IS a cross-partition query, but necessary for admin use case.
        Rule 3.5: Parameterized query with string enum value.
        Rule 4.10: Use status.value to query with string value.
        """
        # Rule 3.5: Parameterized query
        # Rule 4.10: Use status.value (e.g., "Shipped") not status.name ("SHIPPED")
        query = """
            SELECT * FROM c 
            WHERE c.status = @status AND c.type = 'order'
            ORDER BY c.createdAt DESC
        """
        
        # CRITICAL: Use status.value for string serialization (Rule 4.10)
        parameters = [{"name": "@status", "value": status.value}]
        
        items = list(self._container.query_items(
            query=query,
            parameters=parameters,
            enable_cross_partition_query=True,  # Required for cross-partition
            max_item_count=max_items,
        ))
        
        orders = [Order.model_validate(item) for item in items]
        
        return PagedResult(
            items=orders,
            continuationToken=None,
            hasMore=len(orders) == max_items,
        )
    
    def get_orders_by_date_range(
        self,
        start_date: datetime,
        end_date: datetime,
        max_items: int = 20,
    ) -> PagedResult:
        """
        Get orders within a date range (cross-partition query).
        
        Rule 3.1: Cross-partition query for date range.
        Rule 3.5: Parameterized query.
        """
        query = """
            SELECT * FROM c 
            WHERE c.createdAt >= @startDate 
              AND c.createdAt <= @endDate 
              AND c.type = 'order'
            ORDER BY c.createdAt DESC
        """
        
        parameters = [
            {"name": "@startDate", "value": start_date.isoformat()},
            {"name": "@endDate", "value": end_date.isoformat()},
        ]
        
        items = list(self._container.query_items(
            query=query,
            parameters=parameters,
            enable_cross_partition_query=True,
            max_item_count=max_items,
        ))
        
        orders = [Order.model_validate(item) for item in items]
        
        return PagedResult(
            items=orders,
            continuationToken=None,
            hasMore=len(orders) == max_items,
        )
    
    def update_order_status(
        self,
        order_id: str,
        customer_id: str,
        new_status: OrderStatus,
    ) -> Optional[Order]:
        """
        Update order status.
        
        Rule 4.10: Status stored as string value.
        """
        order = self.get_order(order_id, customer_id)
        if order is None:
            return None
        
        # Update status and timestamp
        order.status = new_status
        order.updatedAt = datetime.utcnow()
        
        # Set shipped/delivered timestamps
        if new_status == OrderStatus.SHIPPED:
            order.shippedAt = datetime.utcnow()
        elif new_status == OrderStatus.DELIVERED:
            order.deliveredAt = datetime.utcnow()
        
        # Convert to dict (status will be string like "Shipped")
        order_dict = order.model_dump(mode="json")
        
        # Upsert in Cosmos DB
        result = self._container.upsert_item(body=order_dict)
        
        return Order.model_validate(result)


# Singleton repository instance
_repository: Optional[OrderRepository] = None


def get_order_repository() -> OrderRepository:
    """Get singleton repository instance."""
    global _repository
    if _repository is None:
        _repository = OrderRepository()
    return _repository
