package com.iot.telemetry.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.iot.telemetry.model.TelemetryReading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Telemetry CRUD and query operations.
 *
 * Best practices applied:
 * - Rule 2.7: Partition key /deviceId aligns with device-level queries
 * - Rule 3.1: Single-partition queries (always include deviceId)
 * - Rule 3.7: Point reads when ID + partition key known
 * - Rule 5.2: Uses composite index for ORDER BY timestamp queries
 */
@Repository
public class TelemetryRepository {

    private static final Logger logger = LoggerFactory.getLogger(TelemetryRepository.class);

    private final CosmosContainer container;

    public TelemetryRepository(@Qualifier("telemetryContainer") CosmosContainer container) {
        this.container = container;
    }

    /**
     * Ingest a single telemetry reading.
     * Generates readingId (UUID) and timestamp (ISO-8601) if not provided.
     */
    public TelemetryReading ingest(TelemetryReading reading) {
        String readingId = UUID.randomUUID().toString();
        reading.setId(readingId);
        reading.setReadingId(readingId);

        if (reading.getTimestamp() == null || reading.getTimestamp().isEmpty()) {
            reading.setTimestamp(Instant.now().toString());
        }

        CosmosItemResponse<TelemetryReading> response = container.createItem(
                reading, new PartitionKey(reading.getDeviceId()), new CosmosItemRequestOptions());
        logger.debug("Ingested reading '{}' for device '{}', RU: {}",
                readingId, reading.getDeviceId(), response.getRequestCharge());
        return response.getItem();
    }

    /**
     * Batch ingest multiple readings.
     * Returns count of successfully ingested readings.
     */
    public int ingestBatch(List<TelemetryReading> readings) {
        int count = 0;
        for (TelemetryReading reading : readings) {
            try {
                ingest(reading);
                count++;
            } catch (Exception e) {
                logger.warn("Failed to ingest reading for device '{}': {}",
                        reading.getDeviceId(), e.getMessage());
            }
        }
        return count;
    }

    /**
     * Get the latest telemetry reading for a device.
     * Uses single-partition query with ORDER BY timestamp DESC LIMIT 1.
     * Rule 3.1: Single-partition query (partition key = deviceId).
     */
    public Optional<TelemetryReading> getLatestReading(String deviceId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT TOP 1 * FROM c WHERE c.deviceId = @deviceId ORDER BY c.timestamp DESC",
                Arrays.asList(new SqlParameter("@deviceId", deviceId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(deviceId));

        CosmosPagedIterable<TelemetryReading> results = container.queryItems(
                query, options, TelemetryReading.class);

        List<TelemetryReading> list = new ArrayList<>();
        results.forEach(list::add);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * Get readings for a device within a time range.
     * Rule 3.1: Single-partition query with partition key.
     * Rule 5.2: Uses composite index on (deviceId, timestamp).
     */
    public List<TelemetryReading> getReadingsByTimeRange(String deviceId, String start, String end) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.deviceId = @deviceId AND c.timestamp >= @start AND c.timestamp <= @end ORDER BY c.timestamp ASC",
                Arrays.asList(
                        new SqlParameter("@deviceId", deviceId),
                        new SqlParameter("@start", start),
                        new SqlParameter("@end", end)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(deviceId));

        CosmosPagedIterable<TelemetryReading> results = container.queryItems(
                query, options, TelemetryReading.class);

        List<TelemetryReading> readings = new ArrayList<>();
        results.forEach(readings::add);
        logger.debug("Found {} readings for device '{}' between {} and {}", readings.size(), deviceId, start, end);
        return readings;
    }

    /**
     * Get aggregate statistics for a device over a given time period.
     * Rule 3.1: Single-partition aggregate query.
     */
    public Map<String, Object> getDeviceStats(String deviceId, String period) {
        // Parse period string (e.g., "1h", "24h", "7d")
        Instant now = Instant.now();
        Instant startTime = parsePeriodStart(now, period);

        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT " +
                "MIN(c.temperature) AS minTemp, MAX(c.temperature) AS maxTemp, AVG(c.temperature) AS avgTemp, " +
                "MIN(c.humidity) AS minHum, MAX(c.humidity) AS maxHum, AVG(c.humidity) AS avgHum, " +
                "MIN(c.batteryLevel) AS minBat, MAX(c.batteryLevel) AS maxBat, AVG(c.batteryLevel) AS avgBat " +
                "FROM c WHERE c.deviceId = @deviceId AND c.timestamp >= @startTime",
                Arrays.asList(
                        new SqlParameter("@deviceId", deviceId),
                        new SqlParameter("@startTime", startTime.toString())));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(deviceId));

        CosmosPagedIterable<Map> results = container.queryItems(query, options, Map.class);

        Map<String, Object> stats = new HashMap<>();
        stats.put("deviceId", deviceId);
        stats.put("period", period);

        Map<String, Object> temperature = new HashMap<>();
        Map<String, Object> humidity = new HashMap<>();
        Map<String, Object> batteryLevel = new HashMap<>();

        for (Map row : results) {
            temperature.put("min", toDouble(row.get("minTemp")));
            temperature.put("max", toDouble(row.get("maxTemp")));
            temperature.put("avg", toDouble(row.get("avgTemp")));
            humidity.put("min", toDouble(row.get("minHum")));
            humidity.put("max", toDouble(row.get("maxHum")));
            humidity.put("avg", toDouble(row.get("avgHum")));
            batteryLevel.put("min", toDouble(row.get("minBat")));
            batteryLevel.put("max", toDouble(row.get("maxBat")));
            batteryLevel.put("avg", toDouble(row.get("avgBat")));
        }

        // If no data found, set all to 0
        if (temperature.isEmpty()) {
            temperature.put("min", 0.0);
            temperature.put("max", 0.0);
            temperature.put("avg", 0.0);
            humidity.put("min", 0.0);
            humidity.put("max", 0.0);
            humidity.put("avg", 0.0);
            batteryLevel.put("min", 0.0);
            batteryLevel.put("max", 0.0);
            batteryLevel.put("avg", 0.0);
        }

        stats.put("temperature", temperature);
        stats.put("humidity", humidity);
        stats.put("batteryLevel", batteryLevel);

        return stats;
    }

    /**
     * Delete all telemetry readings for a device.
     * Queries all readings then deletes them one by one (necessary for Cosmos DB).
     */
    public void deleteReadingsForDevice(String deviceId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT c.id FROM c WHERE c.deviceId = @deviceId",
                Arrays.asList(new SqlParameter("@deviceId", deviceId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(deviceId));

        CosmosPagedIterable<Map> results = container.queryItems(query, options, Map.class);

        int count = 0;
        for (Map item : results) {
            String id = (String) item.get("id");
            try {
                container.deleteItem(id, new PartitionKey(deviceId), new CosmosItemRequestOptions());
                count++;
            } catch (CosmosException e) {
                if (e.getStatusCode() != 404) {
                    logger.warn("Failed to delete telemetry reading '{}': {}", id, e.getMessage());
                }
            }
        }
        logger.info("Deleted {} telemetry readings for device '{}'", count, deviceId);
    }

    /**
     * Get the latest reading for each of the given device IDs.
     * Used for location summary endpoint.
     */
    public List<TelemetryReading> getLatestReadingsForDevices(List<String> deviceIds) {
        List<TelemetryReading> results = new ArrayList<>();
        for (String deviceId : deviceIds) {
            getLatestReading(deviceId).ifPresent(results::add);
        }
        return results;
    }

    private Instant parsePeriodStart(Instant now, String period) {
        if (period == null || period.isEmpty()) {
            period = "24h";
        }

        String numStr = period.substring(0, period.length() - 1);
        char unit = period.charAt(period.length() - 1);
        int num;
        try {
            num = Integer.parseInt(numStr);
        } catch (NumberFormatException e) {
            num = 24;
            unit = 'h';
        }

        return switch (unit) {
            case 'h' -> now.minus(num, ChronoUnit.HOURS);
            case 'd' -> now.minus(num, ChronoUnit.DAYS);
            case 'm' -> now.minus(num, ChronoUnit.MINUTES);
            default -> now.minus(24, ChronoUnit.HOURS);
        };
    }

    private double toDouble(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
