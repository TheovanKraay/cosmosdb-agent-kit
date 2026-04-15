package com.iot.telemetry.repository;

import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.iot.telemetry.model.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;

@Repository
public class DeviceRepository {

    private static final Logger logger = LoggerFactory.getLogger(DeviceRepository.class);

    private final CosmosAsyncContainer container;

    public DeviceRepository(@Qualifier("devicesContainer") CosmosAsyncContainer container) {
        this.container = container;
    }

    // Rule 3.7: Point read for known ID and partition key
    public Mono<Device> findById(String deviceId) {
        return container.readItem(deviceId, new PartitionKey(deviceId), Device.class)
                .map(CosmosItemResponse::getItem);
    }

    public Mono<Device> createDevice(Device device) {
        // Rule 4.19: Fresh request options per call
        return container.createItem(device, new PartitionKey(device.getDeviceId()), new CosmosItemRequestOptions())
                .map(CosmosItemResponse::getItem);
    }

    public Mono<Device> updateDevice(Device device) {
        return container.replaceItem(device, device.getId(), new PartitionKey(device.getDeviceId()), new CosmosItemRequestOptions())
                .map(CosmosItemResponse::getItem);
    }

    public Mono<Void> deleteDevice(String deviceId) {
        return container.deleteItem(deviceId, new PartitionKey(deviceId), new CosmosItemRequestOptions())
                .then();
    }

    // Rule 3.6: Parameterized queries
    public Flux<Device> findByLocation(String location) {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.location = @location",
                Collections.singletonList(new SqlParameter("@location", location))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        return container.queryItems(querySpec, options, Device.class);
    }
}
