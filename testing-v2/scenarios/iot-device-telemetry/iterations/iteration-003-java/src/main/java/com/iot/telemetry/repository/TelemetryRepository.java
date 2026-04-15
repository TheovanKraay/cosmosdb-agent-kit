package com.iot.telemetry.repository;

import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.iot.telemetry.model.TelemetryReading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collections;

@Repository
public class TelemetryRepository {

    private static final Logger logger = LoggerFactory.getLogger(TelemetryRepository.class);

    private final CosmosAsyncContainer container;

    public TelemetryRepository(@Qualifier("telemetryContainer") CosmosAsyncContainer container) {
        this.container = container;
    }

    public Mono<TelemetryReading> createReading(TelemetryReading reading) {
        // Rule 4.19: Fresh request options per call
        return container.createItem(reading, new PartitionKey(reading.getDeviceId()), new CosmosItemRequestOptions())
                .map(CosmosItemResponse::getItem);
    }

    // Rule 3.6: Parameterized queries
    public Flux<TelemetryReading> findLatestByDeviceId(String deviceId) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT TOP 1 * FROM c WHERE c.deviceId = @deviceId ORDER BY c.timestamp DESC",
                Collections.singletonList(new SqlParameter("@deviceId", deviceId))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(deviceId));
        return container.queryItems(querySpec, options, TelemetryReading.class);
    }

    // Rule 3.6: Parameterized queries; Rule 3.1: Single-partition query
    public Flux<TelemetryReading> findByDeviceIdAndTimeRange(String deviceId, String start, String end) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.deviceId = @deviceId AND c.timestamp >= @start AND c.timestamp <= @end ORDER BY c.timestamp DESC",
                Arrays.asList(
                        new SqlParameter("@deviceId", deviceId),
                        new SqlParameter("@start", start),
                        new SqlParameter("@end", end)
                )
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(deviceId));
        return container.queryItems(querySpec, options, TelemetryReading.class);
    }

    public Flux<TelemetryReading> findAllByDeviceId(String deviceId) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.deviceId = @deviceId ORDER BY c.timestamp DESC",
                Collections.singletonList(new SqlParameter("@deviceId", deviceId))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        options.setPartitionKey(new PartitionKey(deviceId));
        return container.queryItems(querySpec, options, TelemetryReading.class);
    }

    public Mono<Void> deleteAllByDeviceId(String deviceId) {
        return findAllByDeviceId(deviceId)
                .flatMap(reading -> container.deleteItem(reading.getId(),
                        new PartitionKey(deviceId), new CosmosItemRequestOptions()))
                .then();
    }
}
