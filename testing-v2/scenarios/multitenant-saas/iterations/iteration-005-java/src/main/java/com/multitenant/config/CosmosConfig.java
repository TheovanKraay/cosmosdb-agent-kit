package com.multitenant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Cosmos DB configuration holder — stores connection properties only.
 * CosmosClient is created lazily in the repository to avoid startup failures
 * when the emulator isn't ready or SSL certs aren't trusted yet.
 */
@Configuration
public class CosmosConfig {

    @Value("${cosmos.endpoint}")
    private String endpoint;

    @Value("${cosmos.key}")
    private String key;

    @Value("${cosmos.database}")
    private String databaseName;

    public String getEndpoint() {
        return endpoint;
    }

    public String getKey() {
        return key;
    }

    public String getDatabaseName() {
        return databaseName;
    }
}
