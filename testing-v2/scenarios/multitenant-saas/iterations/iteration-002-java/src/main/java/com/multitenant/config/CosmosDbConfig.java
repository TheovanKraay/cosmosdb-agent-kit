package com.multitenant.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.GatewayConnectionConfig;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.PartitionKeyDefinition;
import com.azure.cosmos.models.PartitionKeyDefinitionVersion;
import com.azure.cosmos.models.PartitionKind;
import com.azure.cosmos.models.ExcludedPath;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.ThroughputProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Collections;

// Rule 4.12: Do not name class CosmosConfig to avoid collision with Spring Data Cosmos
@Configuration
public class CosmosDbConfig {

    @Value("${azure.cosmos.endpoint}")
    private String endpoint;

    @Value("${azure.cosmos.key}")
    private String key;

    @Value("${azure.cosmos.database}")
    private String databaseName;

    @Value("${azure.cosmos.container}")
    private String containerName;

    // Rule 4.22: Reuse CosmosClient as Singleton
    // Rule 4.12: Use dependent @Bean methods, not @PostConstruct
    @Bean(destroyMethod = "close")
    public CosmosClient cosmosClient() {
        CosmosClientBuilder builder = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key)
                .consistencyLevel(ConsistencyLevel.SESSION)
                // Rule 4.11: Enable content response on write to get documents back
                .contentResponseOnWriteEnabled(true);

        // Rule 4.8: Use Gateway mode for emulator, Direct for production
        if (endpoint.contains("localhost") || endpoint.contains("127.0.0.1")) {
            builder.gatewayMode(GatewayConnectionConfig.getDefaultConfig());
        } else {
            builder.directMode();
        }

        return builder.buildClient();
    }

    // Rule 4.12: Create database in @Bean, not @PostConstruct
    @Bean
    public CosmosDatabase cosmosDatabase(CosmosClient cosmosClient) {
        cosmosClient.createDatabaseIfNotExists(databaseName);
        return cosmosClient.getDatabase(databaseName);
    }

    @Bean
    public CosmosContainer cosmosContainer(CosmosDatabase cosmosDatabase) {
        // Rule 2.3: Hierarchical partition keys for multi-tenant design
        PartitionKeyDefinition partitionKeyDef = new PartitionKeyDefinition();
        partitionKeyDef.setPaths(Arrays.asList("/tenantId", "/type"));
        partitionKeyDef.setKind(PartitionKind.MULTI_HASH);
        partitionKeyDef.setVersion(PartitionKeyDefinitionVersion.V2);

        CosmosContainerProperties containerProperties =
                new CosmosContainerProperties(containerName, partitionKeyDef);

        // Rule 5.3: Exclude unused index paths
        IndexingPolicy indexingPolicy = new IndexingPolicy();
        indexingPolicy.setIncludedPaths(Collections.singletonList(new IncludedPath("/*")));
        indexingPolicy.setExcludedPaths(Arrays.asList(
                new ExcludedPath("/_etag/?"),
                new ExcludedPath("/description/?")
        ));
        containerProperties.setIndexingPolicy(indexingPolicy);

        // Rule 6.1: Use autoscale for variable workloads
        cosmosDatabase.createContainerIfNotExists(
                containerProperties,
                ThroughputProperties.createAutoscaledThroughput(4000));

        return cosmosDatabase.getContainer(containerName);
    }
}
