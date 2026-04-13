package com.multitenant.config;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.ExcludedPath;
import com.azure.cosmos.models.IncludedPath;
import com.azure.cosmos.models.IndexingPolicy;
import com.azure.cosmos.models.CompositePath;
import com.azure.cosmos.models.CompositePathSortOrder;
import com.azure.cosmos.models.PartitionKeyDefinition;
import com.azure.cosmos.models.PartitionKeyDefinitionVersion;
import com.azure.cosmos.models.PartitionKind;
import com.azure.cosmos.models.ThroughputProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class CosmosDbConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(CosmosDbConfiguration.class);
    private static final int MAX_RETRIES = 10;
    private static final long INITIAL_BACKOFF_MS = 2000;

    @Value("${azure.cosmos.endpoint}")
    private String endpoint;

    @Value("${azure.cosmos.key}")
    private String key;

    @Value("${azure.cosmos.database}")
    private String databaseName;

    private volatile CosmosClient client;
    private volatile CosmosContainer container;

    public synchronized CosmosContainer getContainer() {
        if (container != null) {
            return container;
        }

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (client == null) {
                    CosmosClientBuilder builder = new CosmosClientBuilder()
                            .endpoint(endpoint)
                            .key(key)
                            .consistencyLevel(ConsistencyLevel.SESSION)
                            .contentResponseOnWriteEnabled(true);

                    if (endpoint.contains("localhost") || endpoint.contains("127.0.0.1")) {
                        builder.gatewayMode();
                    } else {
                        builder.directMode();
                    }

                    client = builder.buildClient();
                }

                client.createDatabaseIfNotExists(databaseName);
                CosmosDatabase database = client.getDatabase(databaseName);

                // Hierarchical partition key: /tenantId + /type
                PartitionKeyDefinition pkDefinition = new PartitionKeyDefinition();
                pkDefinition.setPaths(Arrays.asList("/tenantId", "/type"));
                pkDefinition.setVersion(PartitionKeyDefinitionVersion.V2);
                pkDefinition.setKind(PartitionKind.MULTI_HASH);

                CosmosContainerProperties containerProperties =
                        new CosmosContainerProperties("multitenant-data", pkDefinition);

                // Custom indexing policy
                IndexingPolicy indexingPolicy = new IndexingPolicy();

                List<IncludedPath> includedPaths = new ArrayList<>();
                includedPaths.add(new IncludedPath("/*"));
                indexingPolicy.setIncludedPaths(includedPaths);

                List<ExcludedPath> excludedPaths = new ArrayList<>();
                excludedPaths.add(new ExcludedPath("/\"_etag\"/?"));
                excludedPaths.add(new ExcludedPath("/description/?"));
                excludedPaths.add(new ExcludedPath("/email/?"));
                indexingPolicy.setExcludedPaths(excludedPaths);

                // Composite indexes for common queries
                List<List<CompositePath>> compositeIndexes = new ArrayList<>();

                List<CompositePath> statusIndex = new ArrayList<>();
                statusIndex.add(new CompositePath().setPath("/tenantId").setOrder(CompositePathSortOrder.ASCENDING));
                statusIndex.add(new CompositePath().setPath("/status").setOrder(CompositePathSortOrder.ASCENDING));
                compositeIndexes.add(statusIndex);

                List<CompositePath> priorityIndex = new ArrayList<>();
                priorityIndex.add(new CompositePath().setPath("/tenantId").setOrder(CompositePathSortOrder.ASCENDING));
                priorityIndex.add(new CompositePath().setPath("/priority").setOrder(CompositePathSortOrder.ASCENDING));
                compositeIndexes.add(priorityIndex);

                List<CompositePath> assigneeIndex = new ArrayList<>();
                assigneeIndex.add(new CompositePath().setPath("/tenantId").setOrder(CompositePathSortOrder.ASCENDING));
                assigneeIndex.add(new CompositePath().setPath("/assigneeId").setOrder(CompositePathSortOrder.ASCENDING));
                compositeIndexes.add(assigneeIndex);

                indexingPolicy.setCompositeIndexes(compositeIndexes);
                containerProperties.setIndexingPolicy(indexingPolicy);

                database.createContainerIfNotExists(
                        containerProperties,
                        ThroughputProperties.createAutoscaledThroughput(4000));

                container = database.getContainer("multitenant-data");
                logger.info("Cosmos DB container initialized on attempt {}", attempt);
                return container;

            } catch (Exception e) {
                logger.warn("Cosmos DB init attempt {}/{} failed: {}", attempt, MAX_RETRIES, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        long backoff = INITIAL_BACKOFF_MS * (1L << (attempt - 1));
                        Thread.sleep(Math.min(backoff, 30000));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during Cosmos DB initialization", ie);
                    }
                } else {
                    throw new RuntimeException("Failed to initialize Cosmos DB after " + MAX_RETRIES + " attempts", e);
                }
            }
        }
        throw new RuntimeException("Failed to initialize Cosmos DB");
    }

    @PreDestroy
    public void close() {
        if (client != null) {
            client.close();
        }
    }
}
