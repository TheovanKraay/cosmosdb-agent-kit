package com.iot.telemetry.repository;

import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.*;
import com.iot.telemetry.model.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * Repository for Device CRUD operations.
 *
 * Best Practices Applied:
 * - Rule 4.1: Async APIs with Mono/Flux
 * - Rule 3.1: Single-partition point reads (most efficient: 1 RU for <1KB)
 * - Rule 3.5: Parameterized queries to prevent injection
 */
@Repository
public class DeviceRepository {

    private static final Logger log = LoggerFactory.getLogger(DeviceRepository.class);
    private final CosmosAsyncContainer container;

    public DeviceRepository(CosmosAsyncDatabase database) {
        this.container = database.getContainer("devices");
    }

    public Mono<Device> createDevice(Device device) {
        device.setId(device.getDeviceId());
        PartitionKey pk = new PartitionKey(device.getDeviceId());

        return container.createItem(device, pk, new CosmosItemRequestOptions())
                .doOnSuccess(r -> log.debug("Created device {}, RU={}", device.getDeviceId(), r.getRequestCharge()))
                .map(CosmosItemResponse::getItem);
    }

    /**
     * Point read by deviceId — most efficient operation.
     */
    public Mono<Device> getDevice(String deviceId) {
        PartitionKey pk = new PartitionKey(deviceId);

        return container.readItem(deviceId, pk, Device.class)
                .doOnSuccess(r -> log.debug("Read device {}, RU={}", deviceId, r.getRequestCharge()))
                .map(CosmosItemResponse::getItem);
    }

    /**
     * Update device using read-then-replace pattern (preserves fields not in PATCH body).
     */
    public Mono<Device> updateDevice(Device device) {
        PartitionKey pk = new PartitionKey(device.getDeviceId());

        return container.upsertItem(device, pk, new CosmosItemRequestOptions())
                .doOnSuccess(r -> log.debug("Updated device {}, RU={}", device.getDeviceId(), r.getRequestCharge()))
                .map(CosmosItemResponse::getItem);
    }

    /**
     * Cross-partition query to find devices by location.
     * Rule 3.5: Parameterized query
     */
    public Flux<Device> getDevicesByLocation(String location) {
        String query = "SELECT * FROM c WHERE c.location = @location";
        SqlQuerySpec querySpec = new SqlQuerySpec(query,
                Arrays.asList(new SqlParameter("@location", location)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        return container.queryItems(querySpec, options, Device.class)
                .byPage()
                .flatMap(page -> {
                    log.debug("Location query: {}, count={}, RU={}",
                            location, page.getResults().size(), page.getRequestCharge());
                    return Flux.fromIterable(page.getResults());
                });
    }

    public Mono<Void> deleteDevice(String deviceId) {
        PartitionKey pk = new PartitionKey(deviceId);

        return container.deleteItem(deviceId, pk, new CosmosItemRequestOptions())
                .doOnSuccess(r -> log.debug("Deleted device {}, RU={}", deviceId, r.getRequestCharge()))
                .then();
    }

    /**
     * Check if a device exists (used before returning 404).
     */
    public Mono<Boolean> deviceExists(String deviceId) {
        return getDevice(deviceId)
                .map(d -> true)
                .onErrorResume(CosmosException.class, e -> {
                    if (e.getStatusCode() == 404) return Mono.just(false);
                    return Mono.error(e);
                });
    }
}
