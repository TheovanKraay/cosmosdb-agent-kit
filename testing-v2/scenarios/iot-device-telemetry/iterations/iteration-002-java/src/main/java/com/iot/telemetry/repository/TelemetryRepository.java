package com.iot.telemetry.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.iot.telemetry.dto.DeviceStatsResponse;
import com.iot.telemetry.dto.TelemetrySummaryEntry;
import com.iot.telemetry.model.TelemetryReading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class TelemetryRepository {

    private static final Logger logger = LoggerFactory.getLogger(TelemetryRepository.class);

    private final CosmosContainer container;

    public TelemetryRepository(@Qualifier("telemetryContainer") CosmosContainer container) {
        this.container = container;
    }

    public TelemetryReading ingestReading(TelemetryReading reading) {
        String readingId = UUID.randomUUID().toString();
        reading.setId(readingId);
        reading.setReadingId(readingId);
        reading.setType("telemetry");

        if (reading.getTimestamp() == null || reading.getTimestamp().isEmpty()) {
            reading.setTimestamp(Instant.now().toString());
        }

        CosmosItemRequestOptions options = new CosmosItemRequestOptions();
        CosmosItemResponse<TelemetryReading> response = container.createItem(
                reading, new PartitionKey(reading.getDeviceId()), options);
        return response.getItem();
    }

    public int ingestBatch(List<TelemetryReading> readings) {
        int count = 0;
        for (TelemetryReading reading : readings) {
            ingestReading(reading);
            count++;
        }
        return count;
    }

    public TelemetryReading getLatestReading(String deviceId) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.deviceId = @deviceId ORDER BY c.timestamp DESC OFFSET 0 LIMIT 1",
                List.of(new SqlParameter("@deviceId", deviceId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(deviceId));

        List<TelemetryReading> results = new ArrayList<>();
        container.queryItems(querySpec, options, TelemetryReading.class)
                .forEach(results::add);

        return results.isEmpty() ? null : results.get(0);
    }

    public List<TelemetryReading> getReadingsByTimeRange(String deviceId, String start, String end) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.deviceId = @deviceId AND c.timestamp >= @start AND c.timestamp <= @end ORDER BY c.timestamp DESC",
                List.of(
                        new SqlParameter("@deviceId", deviceId),
                        new SqlParameter("@start", start),
                        new SqlParameter("@end", end)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(deviceId));

        List<TelemetryReading> results = new ArrayList<>();
        container.queryItems(querySpec, options, TelemetryReading.class)
                .forEach(results::add);
        return results;
    }

    public DeviceStatsResponse getDeviceStats(String deviceId, String period) {
        // Parse period to get time range
        Instant now = Instant.now();
        Instant since = parsePeriod(now, period);

        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.deviceId = @deviceId AND c.timestamp >= @since",
                List.of(
                        new SqlParameter("@deviceId", deviceId),
                        new SqlParameter("@since", since.toString())));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(deviceId));

        List<TelemetryReading> readings = new ArrayList<>();
        container.queryItems(querySpec, options, TelemetryReading.class)
                .forEach(readings::add);

        DeviceStatsResponse stats = new DeviceStatsResponse();
        stats.setDeviceId(deviceId);
        stats.setPeriod(period);

        if (readings.isEmpty()) {
            stats.setTemperature(new DeviceStatsResponse.StatValues(0, 0, 0));
            stats.setHumidity(new DeviceStatsResponse.StatValues(0, 0, 0));
            stats.setBatteryLevel(new DeviceStatsResponse.StatValues(0, 0, 0));
        } else {
            stats.setTemperature(computeStats(readings, TelemetryReading::getTemperature));
            stats.setHumidity(computeStats(readings, TelemetryReading::getHumidity));
            stats.setBatteryLevel(computeStats(readings, TelemetryReading::getBatteryLevel));
        }

        return stats;
    }

    public List<TelemetrySummaryEntry> getLocationSummary(String location, List<String> deviceIds) {
        // Get latest reading for each device at this location
        List<TelemetrySummaryEntry> summary = new ArrayList<>();

        for (String deviceId : deviceIds) {
            TelemetryReading latest = getLatestReading(deviceId);
            if (latest != null) {
                TelemetrySummaryEntry entry = new TelemetrySummaryEntry();
                entry.setDeviceId(latest.getDeviceId());
                entry.setTemperature(latest.getTemperature());
                entry.setHumidity(latest.getHumidity());
                entry.setBatteryLevel(latest.getBatteryLevel());
                entry.setTimestamp(latest.getTimestamp());
                summary.add(entry);
            }
        }

        return summary;
    }

    public void deleteReadingsForDevice(String deviceId) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT c.id FROM c WHERE c.deviceId = @deviceId",
                List.of(new SqlParameter("@deviceId", deviceId)));

        CosmosQueryRequestOptions queryOptions = new CosmosQueryRequestOptions();
        queryOptions.setPartitionKey(new PartitionKey(deviceId));

        List<String> ids = new ArrayList<>();
        container.queryItems(querySpec, queryOptions, Map.class)
                .forEach(item -> ids.add((String) ((Map<?, ?>) item).get("id")));

        for (String id : ids) {
            try {
                container.deleteItem(id, new PartitionKey(deviceId),
                        new CosmosItemRequestOptions());
            } catch (Exception e) {
                logger.warn("Failed to delete telemetry reading {}: {}", id, e.getMessage());
            }
        }
    }

    private Instant parsePeriod(Instant now, String period) {
        if (period == null || period.isEmpty()) {
            period = "24h";
        }
        String normalized = period.toLowerCase().trim();
        try {
            if (normalized.endsWith("h")) {
                int hours = Integer.parseInt(normalized.substring(0, normalized.length() - 1));
                return now.minus(Duration.ofHours(hours));
            } else if (normalized.endsWith("d")) {
                int days = Integer.parseInt(normalized.substring(0, normalized.length() - 1));
                return now.minus(Duration.ofDays(days));
            } else if (normalized.endsWith("m")) {
                int minutes = Integer.parseInt(normalized.substring(0, normalized.length() - 1));
                return now.minus(Duration.ofMinutes(minutes));
            }
        } catch (NumberFormatException e) {
            // fall through to default
        }
        return now.minus(Duration.ofHours(24));
    }

    private DeviceStatsResponse.StatValues computeStats(
            List<TelemetryReading> readings,
            java.util.function.ToDoubleFunction<TelemetryReading> extractor) {
        double min = readings.stream().mapToDouble(extractor).min().orElse(0);
        double max = readings.stream().mapToDouble(extractor).max().orElse(0);
        double avg = readings.stream().mapToDouble(extractor).average().orElse(0);
        return new DeviceStatsResponse.StatValues(min, max, avg);
    }
}
