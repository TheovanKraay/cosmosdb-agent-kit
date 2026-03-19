package com.gaming.leaderboard.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.DirectConnectionConfig;
import com.azure.cosmos.GatewayConnectionConfig;
import com.azure.spring.data.cosmos.config.AbstractCosmosConfiguration;
import com.azure.spring.data.cosmos.config.CosmosConfig;
import com.azure.spring.data.cosmos.repository.config.EnableCosmosRepositories;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cosmos DB configuration.
 * 
 * Applied rules:
 * - Rule 4.16: Singleton CosmosClient (via Spring @Bean)
 * - Rule 4.6: Gateway mode for emulator, Direct for production
 * - Rule 4.9: Content response enabled for write operations
 * - Rule 4.4: Direct connection mode for production
 * - Rule 7.2: Session consistency for gaming (good balance of consistency/perf)
 */
@Configuration
@EnableCosmosRepositories(basePackages = "com.gaming.leaderboard.repository")
public class CosmosDbConfig extends AbstractCosmosConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(CosmosDbConfig.class);

    @Value("${azure.cosmos.endpoint}")
    private String endpoint;

    @Value("${azure.cosmos.key}")
    private String key;

    @Value("${azure.cosmos.database}")
    private String database;

    /**
     * Rule 4.16: CosmosClient as singleton via Spring bean.
     * Rule 4.6: Gateway mode for emulator (Direct mode has SSL issues with emulator).
     * Rule 4.9: contentResponseOnWriteEnabled so createItem/upsertItem returns documents.
     */
    @Bean
    public CosmosClientBuilder cosmosClientBuilder() {
        boolean isEmulator = endpoint.contains("localhost") || endpoint.contains("127.0.0.1");

        CosmosClientBuilder builder = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .contentResponseOnWriteEnabled(true)  // Rule 4.9: Return docs on writes
                .consistencyLevel(ConsistencyLevel.SESSION);  // Rule 7.2

        if (isEmulator) {
            // Rule 4.6: Gateway mode required for emulator
            logger.info("Using Gateway mode for Cosmos DB Emulator");
            builder.gatewayMode(new GatewayConnectionConfig());
        } else {
            // Rule 4.4: Direct mode for production (30-50% lower latency)
            logger.info("Using Direct mode for production Cosmos DB");
            builder.directMode(new DirectConnectionConfig());
        }

        return builder;
    }

    @Bean
    public CosmosConfig cosmosConfig() {
        return CosmosConfig.builder()
                .enableQueryMetrics(true)  // Rule 8.4: Track RU consumption
                .build();
    }

    @Override
    protected String getDatabaseName() {
        return database;
    }
}
