package com.iot.telemetry.repository;

import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.iot.telemetry.config.CosmosConfig;
import com.iot.telemetry.model.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class DeviceRepository {

    private static final Logger logger = LoggerFactory.getLogger(DeviceRepository.class);
    private final CosmosConfig cosmosConfig;

    public DeviceRepository(CosmosConfig cosmosConfig) {
        this.cosmosConfig = cosmosConfig;
    }

    private CosmosAsyncContainer container() {
        return cosmosConfig.getDevicesContainer();
    }

    public Device createDevice(Device device) {
        device.setId(device.getDeviceId());
        CosmosItemRequestOptions options = new CosmosItemRequestOptions();
        return container()
                .createItem(device, new PartitionKey(device.getDeviceId()), options)
                .map(response -> response.getItem())
                .block();
    }

    public Device getDevice(String deviceId) {
        try {
            return container()
                    .readItem(deviceId, new PartitionKey(deviceId), Device.class)
                    .map(response -> response.getItem())
                    .block();
        } catch (Exception e) {
            if (isNotFound(e)) {
                return null;
            }
            throw new RuntimeException("Error reading device: " + deviceId, e);
        }
    }

    public List<Device> getDevicesByLocation(String location) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.location = @location",
                List.of(new SqlParameter("@location", location))
        );
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        List<Device> devices = new ArrayList<>();
        container()
                .queryItems(query, options, Device.class)
                .byPage()
                .toIterable()
                .forEach(page -> devices.addAll(page.getResults()));
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

        return container()
                .replaceItem(existing, existing.getId(), new PartitionKey(existing.getDeviceId()),
                        new CosmosItemRequestOptions())
                .map(response -> response.getItem())
                .block();
    }

    public boolean deleteDevice(String deviceId) {
        Device existing = getDevice(deviceId);
        if (existing == null) {
            return false;
        }
        try {
            container()
                    .deleteItem(deviceId, new PartitionKey(deviceId), new CosmosItemRequestOptions())
                    .block();
            return true;
        } catch (Exception e) {
            if (isNotFound(e)) {
                return false;
            }
            throw new RuntimeException("Error deleting device: " + deviceId, e);
        }
    }

    private boolean isNotFound(Exception e) {
        String msg = e.getMessage();
        if (msg != null && msg.contains("404")) {
            return true;
        }
        Throwable cause = e.getCause();
        while (cause != null) {
            String causeMsg = cause.getMessage();
            if (causeMsg != null && causeMsg.contains("404")) {
                return true;
            }
            if (cause instanceof com.azure.cosmos.CosmosException) {
                return ((com.azure.cosmos.CosmosException) cause).getStatusCode() == 404;
            }
            cause = cause.getCause();
        }
        if (e instanceof com.azure.cosmos.CosmosException) {
            return ((com.azure.cosmos.CosmosException) e).getStatusCode() == 404;
        }
        return false;
    }
}
