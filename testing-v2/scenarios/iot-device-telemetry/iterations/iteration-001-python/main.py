import os
import uuid
from datetime import datetime, timedelta, timezone
from typing import List, Optional

from azure.cosmos import CosmosClient, PartitionKey, exceptions
from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

app = FastAPI(title="IoT Device Telemetry API")

COSMOS_ENDPOINT = os.environ.get("COSMOS_ENDPOINT", "https://localhost:8081")
COSMOS_KEY = os.environ.get(
    "COSMOS_KEY",
    "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==",
)
DATABASE_NAME = "iot-device-telemetry"

# Cosmos DB containers
devices_container = None
telemetry_container = None

TTL_30_DAYS = 30 * 24 * 60 * 60  # 2592000 seconds


class DeviceRequest(BaseModel):
    deviceId: str
    name: str
    location: str
    deviceType: str


class TelemetryRequest(BaseModel):
    deviceId: str
    temperature: float
    humidity: float
    batteryLevel: float
    timestamp: Optional[str] = None


@app.on_event("startup")
async def startup():
    global devices_container, telemetry_container

    client = CosmosClient(COSMOS_ENDPOINT, credential=COSMOS_KEY)

    database = client.create_database_if_not_exists(id=DATABASE_NAME)

    devices_container = database.create_container_if_not_exists(
        id="devices",
        partition_key=PartitionKey(path="/deviceId"),
    )

    telemetry_container = database.create_container_if_not_exists(
        id="telemetry",
        partition_key=PartitionKey(path="/deviceId"),
        default_ttl=TTL_30_DAYS,
    )


# ---- Health ----


@app.get("/health")
async def health():
    return {"status": "ok"}


# ---- Device Management ----


@app.post("/api/devices", status_code=201)
async def register_device(device: DeviceRequest):
    item = {
        "id": device.deviceId,
        "deviceId": device.deviceId,
        "name": device.name,
        "location": device.location,
        "deviceType": device.deviceType,
    }
    devices_container.upsert_item(item)
    return {
        "deviceId": item["deviceId"],
        "name": item["name"],
        "location": item["location"],
        "deviceType": item["deviceType"],
    }


@app.get("/api/devices/{deviceId}")
async def get_device(deviceId: str):
    try:
        item = devices_container.read_item(item=deviceId, partition_key=deviceId)
        return {
            "deviceId": item["deviceId"],
            "name": item["name"],
            "location": item["location"],
            "deviceType": item["deviceType"],
        }
    except exceptions.CosmosResourceNotFoundError:
        raise HTTPException(status_code=404, detail="Device not found")


@app.get("/api/devices")
async def get_devices_by_location(location: str = Query(...)):
    query = "SELECT * FROM c WHERE c.location = @location"
    parameters = [{"name": "@location", "value": location}]
    items = list(
        devices_container.query_items(
            query=query,
            parameters=parameters,
            enable_cross_partition_query=True,
        )
    )
    return [
        {
            "deviceId": item["deviceId"],
            "name": item["name"],
            "location": item["location"],
            "deviceType": item["deviceType"],
        }
        for item in items
    ]


# ---- Telemetry Ingestion ----


@app.post("/api/telemetry", status_code=201)
async def ingest_telemetry(reading: TelemetryRequest):
    reading_id = str(uuid.uuid4())
    timestamp = reading.timestamp or datetime.now(timezone.utc).isoformat()

    item = {
        "id": reading_id,
        "readingId": reading_id,
        "deviceId": reading.deviceId,
        "temperature": reading.temperature,
        "humidity": reading.humidity,
        "batteryLevel": reading.batteryLevel,
        "timestamp": timestamp,
    }
    telemetry_container.upsert_item(item)
    return {
        "readingId": item["readingId"],
        "deviceId": item["deviceId"],
        "temperature": item["temperature"],
        "humidity": item["humidity"],
        "batteryLevel": item["batteryLevel"],
        "timestamp": item["timestamp"],
    }


@app.post("/api/telemetry/batch", status_code=201)
async def ingest_telemetry_batch(readings: List[TelemetryRequest]):
    count = 0
    for reading in readings:
        reading_id = str(uuid.uuid4())
        timestamp = reading.timestamp or datetime.now(timezone.utc).isoformat()

        item = {
            "id": reading_id,
            "readingId": reading_id,
            "deviceId": reading.deviceId,
            "temperature": reading.temperature,
            "humidity": reading.humidity,
            "batteryLevel": reading.batteryLevel,
            "timestamp": timestamp,
        }
        telemetry_container.upsert_item(item)
        count += 1

    return {"ingested": count}


# ---- Telemetry Queries ----


@app.get("/api/devices/{deviceId}/telemetry/latest")
async def get_latest_reading(deviceId: str):
    query = (
        "SELECT * FROM c WHERE c.deviceId = @deviceId "
        "ORDER BY c.timestamp DESC OFFSET 0 LIMIT 1"
    )
    parameters = [{"name": "@deviceId", "value": deviceId}]
    items = list(
        telemetry_container.query_items(
            query=query,
            parameters=parameters,
            partition_key=deviceId,
        )
    )
    if not items:
        raise HTTPException(
            status_code=404, detail="No readings found for device"
        )
    item = items[0]
    return {
        "readingId": item.get("readingId"),
        "deviceId": item["deviceId"],
        "temperature": item["temperature"],
        "humidity": item["humidity"],
        "batteryLevel": item["batteryLevel"],
        "timestamp": item["timestamp"],
    }


@app.get("/api/devices/{deviceId}/telemetry")
async def get_readings_by_time_range(
    deviceId: str,
    start: str = Query(...),
    end: str = Query(...),
):
    query = (
        "SELECT * FROM c WHERE c.deviceId = @deviceId "
        "AND c.timestamp >= @start AND c.timestamp <= @end "
        "ORDER BY c.timestamp DESC"
    )
    parameters = [
        {"name": "@deviceId", "value": deviceId},
        {"name": "@start", "value": start},
        {"name": "@end", "value": end},
    ]
    items = list(
        telemetry_container.query_items(
            query=query,
            parameters=parameters,
            partition_key=deviceId,
        )
    )
    return [
        {
            "readingId": item.get("readingId"),
            "deviceId": item["deviceId"],
            "temperature": item["temperature"],
            "humidity": item["humidity"],
            "batteryLevel": item["batteryLevel"],
            "timestamp": item["timestamp"],
        }
        for item in items
    ]


def parse_period(period: str) -> timedelta:
    """Parse a period string like '1h', '24h', '7d' into a timedelta."""
    if period.endswith("h"):
        return timedelta(hours=int(period[:-1]))
    elif period.endswith("d"):
        return timedelta(days=int(period[:-1]))
    else:
        return timedelta(hours=24)


@app.get("/api/devices/{deviceId}/telemetry/stats")
async def get_device_stats(
    deviceId: str,
    period: str = Query(default="24h"),
):
    delta = parse_period(period)
    start_time = (datetime.now(timezone.utc) - delta).isoformat()

    query = (
        "SELECT * FROM c WHERE c.deviceId = @deviceId "
        "AND c.timestamp >= @start"
    )
    parameters = [
        {"name": "@deviceId", "value": deviceId},
        {"name": "@start", "value": start_time},
    ]
    items = list(
        telemetry_container.query_items(
            query=query,
            parameters=parameters,
            partition_key=deviceId,
        )
    )

    if not items:
        return {
            "deviceId": deviceId,
            "period": period,
            "temperature": {"min": 0, "max": 0, "avg": 0},
            "humidity": {"min": 0, "max": 0, "avg": 0},
            "batteryLevel": {"min": 0, "max": 0, "avg": 0},
        }

    temps = [i["temperature"] for i in items]
    humids = [i["humidity"] for i in items]
    batteries = [i["batteryLevel"] for i in items]

    return {
        "deviceId": deviceId,
        "period": period,
        "temperature": {
            "min": min(temps),
            "max": max(temps),
            "avg": round(sum(temps) / len(temps), 2),
        },
        "humidity": {
            "min": min(humids),
            "max": max(humids),
            "avg": round(sum(humids) / len(humids), 2),
        },
        "batteryLevel": {
            "min": min(batteries),
            "max": max(batteries),
            "avg": round(sum(batteries) / len(batteries), 2),
        },
    }
