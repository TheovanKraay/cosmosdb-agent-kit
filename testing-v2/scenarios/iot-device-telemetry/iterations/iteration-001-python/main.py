"""
IoT Device Telemetry API
========================
FastAPI application for ingesting and querying IoT device telemetry data
using Azure Cosmos DB (NoSQL API).
"""

import os
import uuid
from datetime import datetime, timezone, timedelta
from typing import Optional

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
DATABASE_NAME = "iot-device-telemetry"
DEVICES_CONTAINER = "devices"
TELEMETRY_CONTAINER = "telemetry"
TELEMETRY_TTL_SECONDS = 30 * 24 * 60 * 60  # 30 days


# ---------------------------------------------------------------------------
# Pydantic Models
# ---------------------------------------------------------------------------

class DeviceCreate(BaseModel):
    deviceId: str
    name: str
    location: str
    deviceType: str


class DeviceUpdate(BaseModel):
    name: Optional[str] = None
    location: Optional[str] = None
    deviceType: Optional[str] = None


class TelemetryReading(BaseModel):
    deviceId: str
    temperature: float
    humidity: float
    batteryLevel: float
    timestamp: Optional[str] = None


class StatsField(BaseModel):
    min: float
    max: float
    avg: float


class DeviceStats(BaseModel):
    deviceId: str
    period: str
    temperature: StatsField
    humidity: StatsField
    batteryLevel: StatsField


# ---------------------------------------------------------------------------
# App Initialization
# ---------------------------------------------------------------------------

app = FastAPI(title="IoT Device Telemetry API")


def get_cosmos_client():
    """Create Cosmos DB client."""
    return CosmosClient(COSMOS_ENDPOINT, credential=COSMOS_KEY)


def init_database():
    """Initialize database and containers."""
    client = get_cosmos_client()
    database = client.create_database_if_not_exists(id=DATABASE_NAME)

    # Devices container — partition on /deviceId
    database.create_container_if_not_exists(
        id=DEVICES_CONTAINER,
        partition_key=PartitionKey(path="/deviceId"),
        default_ttl=-1,  # TTL enabled but no default expiry for devices
    )

    # Telemetry container — partition on /deviceId, with 30-day TTL
    database.create_container_if_not_exists(
        id=TELEMETRY_CONTAINER,
        partition_key=PartitionKey(path="/deviceId"),
        default_ttl=TELEMETRY_TTL_SECONDS,
    )

    return database


# Initialize on startup
database = None
devices_container = None
telemetry_container = None


@app.on_event("startup")
async def startup():
    global database, devices_container, telemetry_container
    database = init_database()
    devices_container = database.get_container_client(DEVICES_CONTAINER)
    telemetry_container = database.get_container_client(TELEMETRY_CONTAINER)


# ---------------------------------------------------------------------------
# Health Check
# ---------------------------------------------------------------------------

@app.get("/health")
async def health():
    return {"status": "healthy"}


# ---------------------------------------------------------------------------
# Device Management
# ---------------------------------------------------------------------------

@app.post("/api/devices", status_code=201)
async def register_device(device: DeviceCreate):
    doc = {
        "id": device.deviceId,
        "deviceId": device.deviceId,
        "name": device.name,
        "location": device.location,
        "deviceType": device.deviceType,
        "type": "device",
        "schemaVersion": "1.0",
    }
    try:
        devices_container.create_item(body=doc)
    except exceptions.CosmosResourceExistsError:
        raise HTTPException(status_code=409, detail="Device already exists")

    return JSONResponse(
        status_code=201,
        content={
            "deviceId": device.deviceId,
            "name": device.name,
            "location": device.location,
            "deviceType": device.deviceType,
        },
    )


@app.get("/api/devices")
async def get_devices_by_location(location: str = Query(...)):
    query = "SELECT * FROM c WHERE c.location = @location"
    params = [{"name": "@location", "value": location}]
    items = list(
        devices_container.query_items(
            query=query,
            parameters=params,
            enable_cross_partition_query=True,
        )
    )
    result = []
    for item in items:
        result.append({
            "deviceId": item["deviceId"],
            "name": item["name"],
            "location": item["location"],
            "deviceType": item["deviceType"],
        })
    return result


@app.get("/api/devices/{device_id}")
async def get_device(device_id: str):
    try:
        item = devices_container.read_item(item=device_id, partition_key=device_id)
        return {
            "deviceId": item["deviceId"],
            "name": item["name"],
            "location": item["location"],
            "deviceType": item["deviceType"],
        }
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Device not found")


@app.patch("/api/devices/{device_id}")
async def update_device(device_id: str, update: DeviceUpdate):
    try:
        item = devices_container.read_item(item=device_id, partition_key=device_id)
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Device not found")

    if update.name is not None:
        item["name"] = update.name
    if update.location is not None:
        item["location"] = update.location
    if update.deviceType is not None:
        item["deviceType"] = update.deviceType

    devices_container.replace_item(item=item["id"], body=item)

    return {
        "deviceId": item["deviceId"],
        "name": item["name"],
        "location": item["location"],
        "deviceType": item["deviceType"],
    }


@app.delete("/api/devices/{device_id}", status_code=204)
async def delete_device(device_id: str):
    # Check device exists
    try:
        devices_container.read_item(item=device_id, partition_key=device_id)
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Device not found")

    # Delete the device
    devices_container.delete_item(item=device_id, partition_key=device_id)

    # Delete all telemetry for this device
    telemetry_query = "SELECT c.id FROM c WHERE c.deviceId = @deviceId"
    params = [{"name": "@deviceId", "value": device_id}]
    telemetry_items = list(
        telemetry_container.query_items(
            query=telemetry_query,
            parameters=params,
            partition_key=device_id,
        )
    )
    for t_item in telemetry_items:
        try:
            telemetry_container.delete_item(item=t_item["id"], partition_key=device_id)
        except exceptions.CosmosResourceNotFoundError:
            pass

    return JSONResponse(status_code=204, content=None)


# ---------------------------------------------------------------------------
# Telemetry Ingestion
# ---------------------------------------------------------------------------

@app.post("/api/telemetry", status_code=201)
async def ingest_telemetry(reading: TelemetryReading):
    # Validate device exists
    try:
        devices_container.read_item(item=reading.deviceId, partition_key=reading.deviceId)
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Device not found")

    reading_id = str(uuid.uuid4())
    timestamp = reading.timestamp or datetime.now(timezone.utc).isoformat()

    doc = {
        "id": reading_id,
        "readingId": reading_id,
        "deviceId": reading.deviceId,
        "temperature": reading.temperature,
        "humidity": reading.humidity,
        "batteryLevel": reading.batteryLevel,
        "timestamp": timestamp,
        "type": "telemetry",
        "schemaVersion": "1.0",
    }
    telemetry_container.create_item(body=doc)

    return JSONResponse(
        status_code=201,
        content={
            "readingId": reading_id,
            "deviceId": reading.deviceId,
            "temperature": reading.temperature,
            "humidity": reading.humidity,
            "batteryLevel": reading.batteryLevel,
            "timestamp": timestamp,
        },
    )


@app.post("/api/telemetry/batch", status_code=201)
async def ingest_telemetry_batch(readings: list[TelemetryReading]):
    ingested = 0
    for reading in readings:
        reading_id = str(uuid.uuid4())
        timestamp = reading.timestamp or datetime.now(timezone.utc).isoformat()

        doc = {
            "id": reading_id,
            "readingId": reading_id,
            "deviceId": reading.deviceId,
            "temperature": reading.temperature,
            "humidity": reading.humidity,
            "batteryLevel": reading.batteryLevel,
            "timestamp": timestamp,
            "type": "telemetry",
            "schemaVersion": "1.0",
        }
        try:
            telemetry_container.create_item(body=doc)
            ingested += 1
        except Exception:
            pass

    return JSONResponse(status_code=201, content={"ingested": ingested})


# ---------------------------------------------------------------------------
# Telemetry Queries
# ---------------------------------------------------------------------------

@app.get("/api/devices/{device_id}/telemetry/latest")
async def get_latest_reading(device_id: str):
    # Check device exists
    try:
        devices_container.read_item(item=device_id, partition_key=device_id)
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Device not found")

    query = (
        "SELECT TOP 1 * FROM c WHERE c.deviceId = @deviceId "
        "ORDER BY c.timestamp DESC"
    )
    params = [{"name": "@deviceId", "value": device_id}]
    items = list(
        telemetry_container.query_items(
            query=query,
            parameters=params,
            partition_key=device_id,
        )
    )

    if not items:
        raise HTTPException(status_code=404, detail="No readings found for device")

    item = items[0]
    return {
        "readingId": item.get("readingId", item["id"]),
        "deviceId": item["deviceId"],
        "temperature": item["temperature"],
        "humidity": item["humidity"],
        "batteryLevel": item["batteryLevel"],
        "timestamp": item["timestamp"],
    }


@app.get("/api/devices/{device_id}/telemetry")
async def get_readings_by_time_range(
    device_id: str,
    start: str = Query(...),
    end: str = Query(...),
):
    query = (
        "SELECT * FROM c WHERE c.deviceId = @deviceId "
        "AND c.timestamp >= @start AND c.timestamp <= @end "
        "ORDER BY c.timestamp ASC"
    )
    params = [
        {"name": "@deviceId", "value": device_id},
        {"name": "@start", "value": start},
        {"name": "@end", "value": end},
    ]
    items = list(
        telemetry_container.query_items(
            query=query,
            parameters=params,
            partition_key=device_id,
        )
    )
    result = []
    for item in items:
        result.append({
            "readingId": item.get("readingId", item["id"]),
            "deviceId": item["deviceId"],
            "temperature": item["temperature"],
            "humidity": item["humidity"],
            "batteryLevel": item["batteryLevel"],
            "timestamp": item["timestamp"],
        })
    return result


@app.get("/api/devices/{device_id}/telemetry/stats")
async def get_device_stats(
    device_id: str,
    period: str = Query(default="24h"),
):
    # Parse period to determine time window
    now = datetime.now(timezone.utc)
    if period.endswith("h"):
        hours = int(period[:-1])
        start_time = now - timedelta(hours=hours)
    elif period.endswith("d"):
        days = int(period[:-1])
        start_time = now - timedelta(days=days)
    else:
        start_time = now - timedelta(hours=24)

    start_str = start_time.isoformat()

    query = (
        "SELECT * FROM c WHERE c.deviceId = @deviceId "
        "AND c.timestamp >= @start "
        "ORDER BY c.timestamp ASC"
    )
    params = [
        {"name": "@deviceId", "value": device_id},
        {"name": "@start", "value": start_str},
    ]
    items = list(
        telemetry_container.query_items(
            query=query,
            parameters=params,
            partition_key=device_id,
        )
    )

    if not items:
        return {
            "deviceId": device_id,
            "period": period,
            "temperature": {"min": 0, "max": 0, "avg": 0},
            "humidity": {"min": 0, "max": 0, "avg": 0},
            "batteryLevel": {"min": 0, "max": 0, "avg": 0},
        }

    def compute_stats(values):
        return {
            "min": min(values),
            "max": max(values),
            "avg": sum(values) / len(values),
        }

    temps = [item["temperature"] for item in items]
    humids = [item["humidity"] for item in items]
    batteries = [item["batteryLevel"] for item in items]

    return {
        "deviceId": device_id,
        "period": period,
        "temperature": compute_stats(temps),
        "humidity": compute_stats(humids),
        "batteryLevel": compute_stats(batteries),
    }


# ---------------------------------------------------------------------------
# Location-Level Queries
# ---------------------------------------------------------------------------

@app.get("/api/locations/{location}/telemetry/latest")
async def get_location_summary(location: str):
    # Find all devices at this location
    device_query = "SELECT c.deviceId FROM c WHERE c.location = @location"
    params = [{"name": "@location", "value": location}]
    device_items = list(
        devices_container.query_items(
            query=device_query,
            parameters=params,
            enable_cross_partition_query=True,
        )
    )

    result = []
    for device in device_items:
        device_id = device["deviceId"]
        # Get latest reading for each device
        reading_query = (
            "SELECT TOP 1 * FROM c WHERE c.deviceId = @deviceId "
            "ORDER BY c.timestamp DESC"
        )
        reading_params = [{"name": "@deviceId", "value": device_id}]
        readings = list(
            telemetry_container.query_items(
                query=reading_query,
                parameters=reading_params,
                partition_key=device_id,
            )
        )
        if readings:
            item = readings[0]
            result.append({
                "deviceId": item["deviceId"],
                "temperature": item["temperature"],
                "humidity": item["humidity"],
                "batteryLevel": item["batteryLevel"],
                "timestamp": item["timestamp"],
            })

    return result
