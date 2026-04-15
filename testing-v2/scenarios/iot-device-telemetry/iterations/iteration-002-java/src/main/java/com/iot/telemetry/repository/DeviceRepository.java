package com.iot.telemetry.repository;

import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
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

import java.util.ArrayList;
import java.util.List;

@Repository
public class DeviceRepository {

    private static final Logger logger = LoggerFactory.getLogger(DeviceRepository.class);

    private final CosmosContainer container;

    public DeviceRepository(@Qualifier("devicesContainer") CosmosContainer container) {
        this.container = container;
    }

    public Device createDevice(Device device) {
        device.setId(device.getDeviceId());
        device.setType("device");

        CosmosItemRequestOptions options = new CosmosItemRequestOptions();
        CosmosItemResponse<Device> response = container.createItem(
                device, new PartitionKey(device.getDeviceId()), options);
        return response.getItem();
    }

    public Device getDevice(String deviceId) {
        try {
            // Point read - most efficient (Rule 3.7)
            CosmosItemResponse<Device> response = container.readItem(
                    deviceId, new PartitionKey(deviceId), Device.class);
            return response.getItem();
        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public List<Device> getDevicesByLocation(String location) {
        // Cross-partition query needed since partition key is deviceId
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.location = @location",
                List.of(new SqlParameter("@location", location)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        List<Device> devices = new ArrayList<>();
        container.queryItems(querySpec, options, Device.class)
                .forEach(devices::add);
        return devices;
    }

    public Device updateDevice(String deviceId, Device updates) {
        Device existing = getDevice(deviceId);
        if (existing == null) {
            return null;
        }

        if (updates.getName() != null) {
            existing.setName(updates.getName());
        }
        if (updates.getLocation() != null) {
            existing.setLocation(updates.getLocation());
        }
        if (updates.getDeviceType() != null) {
            existing.setDeviceType(updates.getDeviceType());
        }

        CosmosItemRequestOptions options = new CosmosItemRequestOptions();
        CosmosItemResponse<Device> response = container.replaceItem(
                existing, existing.getId(), new PartitionKey(deviceId), options);
        return response.getItem();
    }

    public boolean deleteDevice(String deviceId) {
        try {
            container.deleteItem(deviceId, new PartitionKey(deviceId),
                    new CosmosItemRequestOptions());
            return true;
        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                return false;
            }
            throw e;
        }
    }
}
