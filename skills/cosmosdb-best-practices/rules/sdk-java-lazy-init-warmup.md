---
title: Use lazy initialization with background warmup for Java Cosmos DB client
impact: HIGH
impactDescription: prevents startup failures and test timeouts in CI environments
tags:
  - sdk
  - java
  - startup
  - emulator
  - lazy-initialization
  - spring-boot
---

## Rule

Never create `CosmosClient` eagerly during Spring Boot application startup (e.g., as a `@Bean` or `@PostConstruct` synchronous init). Instead, use fully lazy initialization with retry logic in a synchronized `getContainer()` method, combined with a `@PostConstruct` background warmup thread that starts the connection asynchronously. This prevents the application from crashing at startup when the Cosmos DB emulator or service isn't immediately available, while ensuring the connection is ready before the first request arrives.

## Why

The `CosmosClientBuilder.buildClient()` method connects to the Cosmos DB endpoint immediately during construction. When used with the Cosmos DB Emulator in CI environments:

1. **SSL certificate issues** — The emulator's self-signed certificate may not be properly imported, causing `CertPathValidatorException` during `buildClient()`
2. **Emulator not ready** — The emulator may still be starting when the Spring Boot app initializes its beans
3. **Health check failures** — If `CosmosClient` creation fails in a `@Bean` method, the entire Spring context fails and `/health` endpoints become unreachable
4. **Test timeouts** — CI tests use per-test timeouts (e.g., 30 seconds). If lazy init happens on the first test request, the combined buildClient + createDatabase + createContainer time may exceed the timeout

## How

1. **Configuration class as value holder only** — Store endpoint/key/database values but do NOT create `CosmosClient`
2. **Lazy init with double-checked locking** — Create `CosmosClient`, database, and container in a synchronized `getContainer()` method
3. **Retry with short exponential backoff** — Use 10 retries with 500ms initial backoff (max 5 seconds) to handle transient emulator failures
4. **Background warmup thread** — Start a `@PostConstruct` daemon thread that calls `getContainer()` so the connection is established while the health check is being polled

### Example (Good)

```java
@Configuration
public class CosmosDbConfig {
    @Value("${azure.cosmos.endpoint}") private String endpoint;
    @Value("${azure.cosmos.key}") private String key;
    @Value("${azure.cosmos.database}") private String databaseName;
    
    public String getEndpoint() { return endpoint; }
    public String getKey() { return key; }
    public String getDatabaseName() { return databaseName; }
    // NO @Bean for CosmosClient!
}

@Repository
public class MyRepository {
    private final CosmosDbConfig config;
    private volatile CosmosClient cosmosClient;
    private volatile CosmosContainer container;

    @PostConstruct
    public void warmup() {
        Thread t = new Thread(() -> {
            try { getContainer(); } catch (Exception e) {
                System.err.println("Warmup failed: " + e.getMessage());
            }
        }, "cosmos-warmup");
        t.setDaemon(true);
        t.start();
    }

    private synchronized CosmosContainer getContainer() {
        if (container != null) return container;
        for (int attempt = 1; attempt <= 10; attempt++) {
            try {
                cosmosClient = new CosmosClientBuilder()
                    .endpoint(config.getEndpoint())
                    .key(config.getKey())
                    .gatewayMode()
                    .contentResponseOnWriteEnabled(true)
                    .buildClient();
                cosmosClient.createDatabaseIfNotExists(config.getDatabaseName());
                // ... create container
                container = cosmosClient.getDatabase(config.getDatabaseName())
                    .getContainer("my-container");
                return container;
            } catch (Exception e) {
                if (cosmosClient != null) { cosmosClient.close(); cosmosClient = null; }
                if (attempt == 10) throw new RuntimeException("Failed after 10 attempts", e);
                Thread.sleep(Math.min(attempt * 500L, 5000L));
            }
        }
        return container;
    }
}
```

### Example (Bad)

```java
@Configuration
public class CosmosDbConfig {
    @Bean(destroyMethod = "close")
    public CosmosClient cosmosClient() {
        // BAD: buildClient() connects immediately, crashes Spring startup
        return new CosmosClientBuilder()
            .endpoint(endpoint).key(key).buildClient();
    }

    @Bean
    public CosmosDatabase database(CosmosClient client) {
        // BAD: also runs at startup, triggers SSL handshake
        client.createDatabaseIfNotExists(dbName);
        return client.getDatabase(dbName);
    }
}
```

## References

- [Azure Cosmos DB Java SDK best practices](https://learn.microsoft.com/azure/cosmos-db/nosql/best-practice-java)
- [Use the Azure Cosmos DB Emulator](https://learn.microsoft.com/azure/cosmos-db/emulator)
