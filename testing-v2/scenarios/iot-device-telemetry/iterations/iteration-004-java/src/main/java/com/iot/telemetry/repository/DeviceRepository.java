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
import com.iot.telemetry.model.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Device CRUD operations.
 *
 * Best practices applied:
 * - Rule 3.7: Point reads (readItem) instead of queries when ID + partition key known
 * - Rule 4.7: Log RU diagnostics
 * - Rule 4.22: Uses singleton CosmosClient via injected container
 */
@Repository
public class DeviceRepository {

    private static final Logger logger = LoggerFactory.getLogger(DeviceRepository.class);

    private final CosmosContainer container;

    public DeviceRepository(@Qualifier("devicesContainer") CosmosContainer container) {
        this.container = container;
    }

    /**
     * Create a new device. Uses point write with partition key.
     */
    public Device createDevice(Device device) {
        device.setId(device.getDeviceId()); // id = deviceId for point reads
        CosmosItemResponse<Device> response = container.createItem(
                device, new PartitionKey(device.getDeviceId()), new CosmosItemRequestOptions());
        logger.info("Created device '{}', RU: {}", device.getDeviceId(), response.getRequestCharge());
        return response.getItem();
    }

    /**
     * Get a device by ID. Uses point read (1 RU) instead of query (Rule 3.7).
     */
    public Optional<Device> getDevice(String deviceId) {
        try {
            CosmosItemResponse<Device> response = container.readItem(
                    deviceId, new PartitionKey(deviceId), Device.class);
            logger.debug("Read device '{}', RU: {}", deviceId, response.getRequestCharge());
            return Optional.ofNullable(response.getItem());
        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /**
     * Query devices by location. Cross-partition query since location is not partition key.
     * Rule 3.9: Project only needed fields.
     */
    public List<Device> getDevicesByLocation(String location) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT c.id, c.deviceId, c.name, c.location, c.deviceType FROM c WHERE c.location = @location",
                Arrays.asList(new SqlParameter("@location", location)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        CosmosPagedIterable<Device> results = container.queryItems(query, options, Device.class);

        List<Device> devices = new ArrayList<>();
        results.forEach(devices::add);
        logger.debug("Found {} devices at location '{}'", devices.size(), location);
        return devices;
    }

    /**
     * Update device metadata. Uses point read + replace for optimistic concurrency.
     */
    public Optional<Device> updateDevice(String deviceId, Device updates) {
        Optional<Device> existing = getDevice(deviceId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Device device = existing.get();
        if (updates.getName() != null) device.setName(updates.getName());
        if (updates.getLocation() != null) device.setLocation(updates.getLocation());
        if (updates.getDeviceType() != null) device.setDeviceType(updates.getDeviceType());

        CosmosItemResponse<Device> response = container.replaceItem(
                device, device.getId(), new PartitionKey(deviceId), new CosmosItemRequestOptions());
        logger.info("Updated device '{}', RU: {}", deviceId, response.getRequestCharge());
        return Optional.ofNullable(response.getItem());
    }

    /**
     * Delete a device by ID. Point delete with partition key.
     */
    public boolean deleteDevice(String deviceId) {
        try {
            CosmosItemResponse<Object> response = container.deleteItem(
                    deviceId, new PartitionKey(deviceId), new CosmosItemRequestOptions());
            logger.info("Deleted device '{}', RU: {}", deviceId, response.getRequestCharge());
            return true;
        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    /**
     * Get all device IDs at a given location.
     */
    public List<String> getDeviceIdsByLocation(String location) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT c.deviceId FROM c WHERE c.location = @location",
                Arrays.asList(new SqlParameter("@location", location)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        CosmosPagedIterable<Device> results = container.queryItems(query, options, Device.class);

        List<String> deviceIds = new ArrayList<>();
        results.forEach(d -> deviceIds.add(d.getDeviceId()));
        return deviceIds;
    }
}
