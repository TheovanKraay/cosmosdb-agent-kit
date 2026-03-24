"""
Orders API Router - REST endpoints for order management

Rules Applied:
- Rule 4.10: Enum serialization - status values are strings in responses
"""
from datetime import datetime
from typing import Optional
from fastapi import APIRouter, HTTPException, Query
from ..models import (
    Order,
    CreateOrderRequest,
    UpdateStatusRequest,
    OrderStatus,
    PagedResult,
)
from ..repository import get_order_repository

router = APIRouter(prefix="/api/orders", tags=["orders"])


@router.post("", response_model=Order, status_code=201)
def create_order(request: CreateOrderRequest) -> Order:
    """
    Create a new order.
    
    Returns order with status as string (Rule 4.10):
    - "Pending" not 0
    - "Shipped" not 1
    """
    try:
        repo = get_order_repository()
        return repo.create_order(request)
    except Exception as e:
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))


# NOTE: Static paths must come before dynamic paths like /{order_id}
@router.get("/daterange", response_model=PagedResult)
def get_orders_by_date_range(
    start_date: datetime = Query(..., alias="startDate"),
    end_date: datetime = Query(..., alias="endDate"),
    max_items: int = Query(20, ge=1, le=100),
) -> PagedResult:
    """
    Get orders within a date range (admin endpoint).
    
    Cross-partition query for date range (Rule 3.1).
    """
    repo = get_order_repository()
    return repo.get_orders_by_date_range(
        start_date=start_date,
        end_date=end_date,
        max_items=max_items,
    )


@router.get("/status/{status}", response_model=PagedResult)
def get_orders_by_status(
    status: OrderStatus,
    max_items: int = Query(20, ge=1, le=100),
) -> PagedResult:
    """
    Get orders by status (admin endpoint).
    
    Cross-partition query filtering by status string (Rule 4.10).
    Example: GET /api/orders/status/Shipped
    """
    repo = get_order_repository()
    return repo.get_orders_by_status(status=status, max_items=max_items)


@router.get("/customer/{customer_id}", response_model=PagedResult)
def get_orders_by_customer(
    customer_id: str,
    max_items: int = Query(20, ge=1, le=100),
    continuation_token: Optional[str] = None,
) -> PagedResult:
    """
    Get orders for a customer.
    
    Single-partition query using customerId (Rule 3.1).
    """
    repo = get_order_repository()
    return repo.get_orders_by_customer(
        customer_id=customer_id,
        max_items=max_items,
        continuation_token=continuation_token,
    )


@router.get("/{order_id}", response_model=Order)
def get_order(
    order_id: str,
    customer_id: str = Query(..., alias="customerId", description="Customer ID (partition key)"),
) -> Order:
    """
    Get order by ID.
    
    Requires customerId as partition key for efficient point read (Rule 3.1).
    """
    repo = get_order_repository()
    order = repo.get_order(order_id, customer_id)
    
    if order is None:
        raise HTTPException(status_code=404, detail="Order not found")
    
    return order


@router.patch("/{order_id}/status", response_model=Order)
def update_order_status(
    order_id: str,
    request: UpdateStatusRequest,
    customer_id: str = Query(..., alias="customerId", description="Customer ID (partition key)"),
) -> Order:
    """
    Update order status.
    
    Status is stored and returned as string (Rule 4.10):
    - Input: {"status": "Shipped"}
    - Stored: {"status": "Shipped"}
    - Response: {"status": "Shipped"}
    """
    repo = get_order_repository()
    order = repo.update_order_status(
        order_id=order_id,
        customer_id=customer_id,
        new_status=request.status,
    )
    
    if order is None:
        raise HTTPException(status_code=404, detail="Order not found")
    
    return order
