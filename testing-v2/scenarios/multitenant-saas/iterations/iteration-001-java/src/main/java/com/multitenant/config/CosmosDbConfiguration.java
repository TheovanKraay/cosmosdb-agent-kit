package com.multitenant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Cosmos DB configuration holder — stores endpoint/key/database values.
 * CosmosClient is NOT created here as a @Bean because buildClient()
 * connects to the emulator immediately, causing SSL failures during
 * Spring context initialization. Instead, CosmosClient is created
 * lazily in MultitenantRepository with retry logic.
 */
@Configuration
public class CosmosDbConfiguration {

    @Value("${azure.cosmos.endpoint}")
    private String endpoint;

    @Value("${azure.cosmos.key}")
    private String key;

    @Value("${azure.cosmos.database}")
    private String databaseName;

    public String getEndpoint() { return endpoint; }
    public String getKey() { return key; }
    public String getDatabaseName() { return databaseName; }
}
