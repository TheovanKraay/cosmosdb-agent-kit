package com.iot.telemetry.repository;

import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.iot.telemetry.config.CosmosConfig;
import com.iot.telemetry.model.TelemetryReading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class TelemetryRepository {

    private static final Logger logger = LoggerFactory.getLogger(TelemetryRepository.class);
    private final CosmosConfig cosmosConfig;

    public TelemetryRepository(CosmosConfig cosmosConfig) {
        this.cosmosConfig = cosmosConfig;
    }

    private CosmosAsyncContainer container() {
        return cosmosConfig.getTelemetryContainer();
    }

    public TelemetryReading ingestReading(TelemetryReading reading) {
        String readingId = UUID.randomUUID().toString();
        reading.setId(readingId);
        reading.setReadingId(readingId);

        if (reading.getTimestamp() == null || reading.getTimestamp().isEmpty()) {
            reading.setTimestamp(Instant.now().atOffset(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }

        CosmosItemRequestOptions options = new CosmosItemRequestOptions();
        return container()
                .createItem(reading, new PartitionKey(reading.getDeviceId()), options)
                .map(response -> response.getItem())
                .block();
    }

    public int ingestBatch(List<TelemetryReading> readings) {
        int ingested = 0;
        for (TelemetryReading reading : readings) {
            try {
                ingestReading(reading);
                ingested++;
            } catch (Exception e) {
                logger.warn("Failed to ingest reading for device {}: {}", reading.getDeviceId(), e.getMessage());
            }
        }
        return ingested;
    }

    public TelemetryReading getLatestReading(String deviceId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT TOP 1 * FROM c WHERE c.deviceId = @deviceId ORDER BY c.timestamp DESC",
                List.of(new SqlParameter("@deviceId", deviceId))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(deviceId));

        List<TelemetryReading> results = new ArrayList<>();
        container()
                .queryItems(query, options, TelemetryReading.class)
                .byPage(1)
                .toIterable()
                .forEach(page -> results.addAll(page.getResults()));

        return results.isEmpty() ? null : results.get(0);
    }

    public List<TelemetryReading> getReadingsByTimeRange(String deviceId, String start, String end) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.deviceId = @deviceId AND c.timestamp >= @start AND c.timestamp <= @end ORDER BY c.timestamp DESC",
                List.of(
                        new SqlParameter("@deviceId", deviceId),
                        new SqlParameter("@start", start),
                        new SqlParameter("@end", end)
                )
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(deviceId));

        List<TelemetryReading> results = new ArrayList<>();
        container()
                .queryItems(query, options, TelemetryReading.class)
                .byPage()
                .toIterable()
                .forEach(page -> results.addAll(page.getResults()));
        return results;
    }

    public List<TelemetryReading> getReadingsForStats(String deviceId, String period) {
        String startTime = calculateStartTime(period);
        String endTime = Instant.now().atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        return getReadingsByTimeRange(deviceId, startTime, endTime);
    }

    public void deleteReadingsForDevice(String deviceId) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT c.id FROM c WHERE c.deviceId = @deviceId",
                List.of(new SqlParameter("@deviceId", deviceId))
        );
        CosmosQueryRequestOptions queryOptions = new CosmosQueryRequestOptions();
        queryOptions.setPartitionKey(new PartitionKey(deviceId));

        List<String> ids = new ArrayList<>();
        container()
                .queryItems(query, queryOptions, TelemetryReading.class)
                .byPage()
                .toIterable()
                .forEach(page -> page.getResults().forEach(r -> ids.add(r.getId())));

        for (String id : ids) {
            try {
                container()
                        .deleteItem(id, new PartitionKey(deviceId), new CosmosItemRequestOptions())
                        .block();
            } catch (Exception e) {
                logger.warn("Failed to delete telemetry reading {}: {}", id, e.getMessage());
            }
        }
    }

    private String calculateStartTime(String period) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime start;

        if (period == null || period.isEmpty()) {
            period = "24h";
        }

        String normalizedPeriod = period.toLowerCase().trim();
        if (normalizedPeriod.endsWith("h")) {
            int hours = Integer.parseInt(normalizedPeriod.replace("h", ""));
            start = now.minusHours(hours);
        } else if (normalizedPeriod.endsWith("d")) {
            int days = Integer.parseInt(normalizedPeriod.replace("d", ""));
            start = now.minusDays(days);
        } else {
            // Default to 24h
            start = now.minusHours(24);
        }

        return start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
