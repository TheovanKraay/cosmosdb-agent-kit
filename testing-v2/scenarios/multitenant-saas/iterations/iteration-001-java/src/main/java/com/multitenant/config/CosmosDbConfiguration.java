package com.multitenant.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cosmos DB configuration using raw SDK (no Spring Data Cosmos).
 * Rule 4.12: Use dependent @Bean methods, not @PostConstruct.
 * Rule 4.8: Use Gateway mode for the Cosmos DB Emulator.
 * Rule 4.11: Enable contentResponseOnWriteEnabled(true).
 * Rule 4.22: Reuse CosmosClient as singleton @Bean.
 *
 * Database and container initialization is done lazily in the repository
 * to avoid SSL connection failures during Spring Boot startup.
 */
@Configuration
public class CosmosDbConfiguration {

    @Value("${azure.cosmos.endpoint}")
    private String endpoint;

    @Value("${azure.cosmos.key}")
    private String key;

    /**
     * Rule 4.22: Singleton CosmosClient.
     * Rule 4.8: Use gatewayMode() for emulator compatibility.
     * Rule 4.11: Enable contentResponseOnWriteEnabled(true) so createItem returns the document.
     *
     * Note: No database/container creation here — that is deferred to lazy init
     * in the repository to avoid blocking startup on Cosmos DB connectivity.
     */
    @Bean(destroyMethod = "close")
    public CosmosClient cosmosClient() {
        return new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .contentResponseOnWriteEnabled(true)
                .gatewayMode()
                .buildClient();
    }
}
