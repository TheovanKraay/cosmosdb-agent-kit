package com.iot.telemetry.repository;

import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.models.*;
import com.iot.telemetry.model.TelemetryReading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * Repository for Telemetry CRUD and query operations.
 *
 * Best Practices Applied:
 * - Rule 4.1: Async APIs with Mono/Flux
 * - Rule 3.1: Single-partition queries where possible
 * - Rule 3.5: Parameterized queries
 * - Rule 5.1: Leverages composite indexes for ORDER BY
 */
@Repository
public class TelemetryRepository {

    private static final Logger log = LoggerFactory.getLogger(TelemetryRepository.class);
    private final CosmosAsyncContainer container;

    public TelemetryRepository(CosmosAsyncDatabase database) {
        this.container = database.getContainer("telemetry");
    }

    public Mono<TelemetryReading> createReading(TelemetryReading reading) {
        reading.ensureDefaults();
        PartitionKey pk = new PartitionKey(reading.getDeviceId());

        return container.createItem(reading, pk, new CosmosItemRequestOptions())
                .doOnSuccess(r -> log.debug("Created reading for {}, RU={}",
                        reading.getDeviceId(), r.getRequestCharge()))
                .map(CosmosItemResponse::getItem);
    }

    /**
     * Bulk insert readings with concurrency.
     */
    public Flux<TelemetryReading> createReadingsBatch(List<TelemetryReading> readings) {
        return Flux.fromIterable(readings)
                .flatMap(reading -> {
                    reading.ensureDefaults();
                    PartitionKey pk = new PartitionKey(reading.getDeviceId());
                    return container.createItem(reading, pk, new CosmosItemRequestOptions())
                            .map(CosmosItemResponse::getItem);
                }, 10);
    }

    /**
     * Get the latest reading for a device.
     * Single-partition query ordered by timestamp DESC with TOP 1.
     */
    public Mono<TelemetryReading> getLatestReading(String deviceId) {
        String query = "SELECT TOP 1 * FROM c WHERE c.deviceId = @deviceId ORDER BY c.timestamp DESC";
        SqlQuerySpec querySpec = new SqlQuerySpec(query,
                Arrays.asList(new SqlParameter("@deviceId", deviceId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(deviceId));

        return container.queryItems(querySpec, options, TelemetryReading.class)
                .byPage()
                .next()
                .flatMap(page -> {
                    log.debug("Latest reading for {}: RU={}", deviceId, page.getRequestCharge());
                    List<TelemetryReading> results = page.getResults();
                    if (results != null && !results.isEmpty()) {
                        return Mono.just(results.get(0));
                    }
                    return Mono.empty();
                });
    }

    /**
     * Query readings for a device within a time range (ISO-8601 strings).
     * Single-partition query.
     */
    public Flux<TelemetryReading> getReadingsByTimeRange(String deviceId, String start, String end) {
        String query = "SELECT * FROM c WHERE c.deviceId = @deviceId " +
                "AND c.timestamp >= @start AND c.timestamp <= @end " +
                "ORDER BY c.timestamp DESC";

        SqlQuerySpec querySpec = new SqlQuerySpec(query,
                Arrays.asList(
                        new SqlParameter("@deviceId", deviceId),
                        new SqlParameter("@start", start),
                        new SqlParameter("@end", end)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(deviceId));

        return container.queryItems(querySpec, options, TelemetryReading.class)
                .byPage()
                .flatMap(page -> {
                    log.debug("Time range query for {}: count={}, RU={}",
                            deviceId, page.getResults().size(), page.getRequestCharge());
                    return Flux.fromIterable(page.getResults());
                });
    }

    /**
     * Delete all telemetry readings for a device (used on device deletion).
     */
    public Mono<Void> deleteReadingsForDevice(String deviceId) {
        String query = "SELECT c.id FROM c WHERE c.deviceId = @deviceId";
        SqlQuerySpec querySpec = new SqlQuerySpec(query,
                Arrays.asList(new SqlParameter("@deviceId", deviceId)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(deviceId));

        return container.queryItems(querySpec, options, TelemetryReading.class)
                .byPage()
                .flatMap(page -> Flux.fromIterable(page.getResults()))
                .flatMap(reading -> container.deleteItem(reading.getId(),
                        new PartitionKey(deviceId), new CosmosItemRequestOptions()).then())
                .then();
    }

    /**
     * Get aggregate statistics for a device within a time period.
     * Uses Cosmos DB aggregate functions (MIN, MAX, AVG).
     */
    public Mono<StatsResult> getDeviceStats(String deviceId, String startTime) {
        String query = "SELECT " +
                "MIN(c.temperature) AS tempMin, MAX(c.temperature) AS tempMax, AVG(c.temperature) AS tempAvg, " +
                "MIN(c.humidity) AS humMin, MAX(c.humidity) AS humMax, AVG(c.humidity) AS humAvg, " +
                "MIN(c.batteryLevel) AS batMin, MAX(c.batteryLevel) AS batMax, AVG(c.batteryLevel) AS batAvg " +
                "FROM c WHERE c.deviceId = @deviceId AND c.timestamp >= @start";

        SqlQuerySpec querySpec = new SqlQuerySpec(query,
                Arrays.asList(
                        new SqlParameter("@deviceId", deviceId),
                        new SqlParameter("@start", startTime)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(deviceId));

        return container.queryItems(querySpec, options, StatsResult.class)
                .byPage()
                .next()
                .flatMap(page -> {
                    List<StatsResult> results = page.getResults();
                    if (results != null && !results.isEmpty()) {
                        return Mono.just(results.get(0));
                    }
                    return Mono.just(new StatsResult());
                });
    }

    /**
     * Get the latest reading for each device at a location.
     * This requires: 1) find devices at location, 2) get latest reading per device.
     * We do this with a cross-partition query since location spans devices.
     */
    public Flux<TelemetryReading> getLatestReadingsByDeviceIds(List<String> deviceIds) {
        return Flux.fromIterable(deviceIds)
                .flatMap(this::getLatestReading);
    }

    /**
     * DTO for aggregate statistics result from Cosmos DB query.
     */
    public static class StatsResult {
        private Double tempMin;
        private Double tempMax;
        private Double tempAvg;
        private Double humMin;
        private Double humMax;
        private Double humAvg;
        private Double batMin;
        private Double batMax;
        private Double batAvg;

        public Double getTempMin() { return tempMin; }
        public void setTempMin(Double tempMin) { this.tempMin = tempMin; }
        public Double getTempMax() { return tempMax; }
        public void setTempMax(Double tempMax) { this.tempMax = tempMax; }
        public Double getTempAvg() { return tempAvg; }
        public void setTempAvg(Double tempAvg) { this.tempAvg = tempAvg; }
        public Double getHumMin() { return humMin; }
        public void setHumMin(Double humMin) { this.humMin = humMin; }
        public Double getHumMax() { return humMax; }
        public void setHumMax(Double humMax) { this.humMax = humMax; }
        public Double getHumAvg() { return humAvg; }
        public void setHumAvg(Double humAvg) { this.humAvg = humAvg; }
        public Double getBatMin() { return batMin; }
        public void setBatMin(Double batMin) { this.batMin = batMin; }
        public Double getBatMax() { return batMax; }
        public void setBatMax(Double batMax) { this.batMax = batMax; }
        public Double getBatAvg() { return batAvg; }
        public void setBatAvg(Double batAvg) { this.batAvg = batAvg; }
    }
}
