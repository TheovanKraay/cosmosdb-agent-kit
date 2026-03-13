"""
E-Commerce Order API
====================
A FastAPI REST API for e-commerce order management using Azure Cosmos DB (NoSQL API).
"""

import os
import uuid
from datetime import datetime, timezone
from typing import List, Optional

from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from azure.cosmos import CosmosClient, PartitionKey, exceptions

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

COSMOS_ENDPOINT = os.environ.get("COSMOS_ENDPOINT", "https://localhost:8081")
COSMOS_KEY = os.environ.get(
    "COSMOS_KEY",
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==",
)
DATABASE_NAME = os.environ.get("DATABASE_NAME", "ecommerce-order-api")
CONTAINER_NAME = "orders"

# ---------------------------------------------------------------------------
# Pydantic Models
# ---------------------------------------------------------------------------


class OrderItem(BaseModel):
    productId: str
    productName: str
    quantity: int = Field(ge=1)
    unitPrice: float


class CreateOrderRequest(BaseModel):
    customerId: str
    items: List[OrderItem]
    shippingAddress: Optional[str] = None


class UpdateStatusRequest(BaseModel):
    status: str


# ---------------------------------------------------------------------------
# Cosmos DB initialisation
# ---------------------------------------------------------------------------

client = CosmosClient(COSMOS_ENDPOINT, credential=COSMOS_KEY)
database = client.create_database_if_not_exists(id=DATABASE_NAME)
container = database.create_container_if_not_exists(
    id=CONTAINER_NAME,
    partition_key=PartitionKey(path="/customerId"),
    indexing_policy={
        "indexingMode": "consistent",
        "automatic": True,
        "includedPaths": [{"path": "/*"}],
        "excludedPaths": [{"path": '/"_etag"/?'}],
        "compositeIndexes": [
            [
                {"path": "/status", "order": "ascending"},
                {"path": "/createdAt", "order": "descending"},
            ]
        ],
    },
)

# ---------------------------------------------------------------------------
# FastAPI App
# ---------------------------------------------------------------------------

app = FastAPI(title="E-Commerce Order API")


# ---------------------------------------------------------------------------
# Helper
# ---------------------------------------------------------------------------


def _order_response(doc: dict) -> dict:
    """Return a clean order representation from a Cosmos DB document."""
    return {
        "orderId": doc["id"],
        "customerId": doc["customerId"],
        "status": doc["status"],
        "items": doc["items"],
        "total": doc["total"],
        "createdAt": doc["createdAt"],
        "shippingAddress": doc.get("shippingAddress"),
    }


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/api/orders", status_code=201)
def create_order(order: CreateOrderRequest):
    total = sum(item.quantity * item.unitPrice for item in order.items)
    total = round(total, 2)

    order_id = str(uuid.uuid4())
    now = datetime.now(timezone.utc).isoformat()

    doc = {
        "id": order_id,
        "customerId": order.customerId,
        "status": "pending",
        "items": [item.model_dump() for item in order.items],
        "total": total,
        "createdAt": now,
        "shippingAddress": order.shippingAddress,
    }

    container.create_item(body=doc)

    return _order_response(doc)


@app.get("/api/orders/{orderId}")
def get_order(orderId: str):
    query = "SELECT * FROM c WHERE c.id = @id"
    params = [{"name": "@id", "value": orderId}]
    items = list(
        container.query_items(query=query, parameters=params, enable_cross_partition_query=True)
    )
    if not items:
        raise HTTPException(status_code=404, detail="Order not found")
    return _order_response(items[0])


@app.get("/api/customers/{customerId}/orders")
def get_customer_orders(customerId: str):
    query = "SELECT * FROM c WHERE c.customerId = @customerId"
    params = [{"name": "@customerId", "value": customerId}]
    items = list(
        container.query_items(
            query=query,
            parameters=params,
            partition_key=customerId,
        )
    )
    return [_order_response(doc) for doc in items]


@app.get("/api/orders")
def query_orders(
    status: Optional[str] = Query(None),
    startDate: Optional[str] = Query(None),
    endDate: Optional[str] = Query(None),
):
    if status:
        query = "SELECT * FROM c WHERE c.status = @status"
        params = [{"name": "@status", "value": status}]
        items = list(
            container.query_items(
                query=query, parameters=params, enable_cross_partition_query=True
            )
        )
        return [_order_response(doc) for doc in items]

    if startDate and endDate:
        query = "SELECT * FROM c WHERE c.createdAt >= @startDate AND c.createdAt <= @endDate"
        params = [
            {"name": "@startDate", "value": startDate},
            {"name": "@endDate", "value": endDate},
        ]
        items = list(
            container.query_items(
                query=query, parameters=params, enable_cross_partition_query=True
            )
        )
        return [_order_response(doc) for doc in items]

    return []


@app.patch("/api/orders/{orderId}/status")
def update_order_status(orderId: str, body: UpdateStatusRequest):
    query = "SELECT * FROM c WHERE c.id = @id"
    params = [{"name": "@id", "value": orderId}]
    items = list(
        container.query_items(query=query, parameters=params, enable_cross_partition_query=True)
    )
    if not items:
        raise HTTPException(status_code=404, detail="Order not found")

    doc = items[0]
    doc["status"] = body.status
    container.replace_item(item=doc["id"], body=doc)

    return _order_response(doc)
