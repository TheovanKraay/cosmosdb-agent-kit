---
title: Eagerly warm up Cosmos DB connection in background thread when using lazy initialization
impact: HIGH
impactDescription: prevents first-request timeouts caused by deferred database and container creation
tags: sdk, java, spring-boot, lazy-initialization, warmup, background-thread, startup
---

## Eagerly Warm Up Cosmos DB Connection When Using Lazy Initialization

When using lazy initialization for `CosmosClient` (common when the Cosmos DB Emulator's SSL certificate is not yet available at Spring Bean creation time), the first API request triggers database and container creation, which can take 10–30+ seconds and cause request timeouts.

To avoid this, start a daemon background thread in `@PostConstruct` that eagerly calls the lazy initializer. The thread runs in parallel with application startup, so the connection is typically ready before the first real request arrives.

### Why

- `CosmosClient.buildClient()` performs initial metadata discovery
- `createDatabaseIfNotExists()` and `createContainerIfNotExists()` are slow operations on first run (especially against the emulator)
- Combined, these can exceed HTTP request timeouts (e.g., 30-second pytest timeout in CI)
- Warming up in a background thread lets the app respond to health checks immediately while the Cosmos DB connection initializes

### How

Use `@PostConstruct` to start a daemon thread that calls `getContainer()`:

```java
@Component
public class CosmosDbConfig {

    private volatile CosmosClient cosmosClient;
    private volatile CosmosContainer container;

    @PostConstruct
    public void warmup() {
        Thread warmupThread = new Thread(() -> {
            try {
                logger.info("Starting Cosmos DB warmup in background...");
                getContainer();
                logger.info("Cosmos DB warmup completed");
            } catch (Exception e) {
                logger.warn("Cosmos DB warmup failed (will retry on first request): {}",
                    e.getMessage());
            }
        }, "cosmos-warmup");
        warmupThread.setDaemon(true);
        warmupThread.start();
    }

    public synchronized CosmosContainer getContainer() {
        if (container != null) {
            return container;
        }
        // Build client, create database/container with retry loop
        // ...
        return container;
    }
}
```

### Example (Bad)

```java
// ❌ No warmup — first API request blocks for 10–30+ seconds
@Component
public class CosmosDbConfig {

    private volatile CosmosContainer container;

    // No @PostConstruct warmup — container is null until first request
    public synchronized CosmosContainer getContainer() {
        if (container != null) return container;
        // Slow initialization happens here during the first HTTP request
        cosmosClient = new CosmosClientBuilder()...buildClient();
        cosmosClient.createDatabaseIfNotExists(dbName);
        // ... createContainerIfNotExists ...
        container = database.getContainer(containerName);
        return container;
    }
}
// First request to POST /api/tenants takes 20+ seconds → test timeout
```

### Example (Good)

```java
// ✅ Background warmup — connection ready before first request
@Component
public class CosmosDbConfig {

    private volatile CosmosContainer container;

    @PostConstruct
    public void warmup() {
        Thread t = new Thread(() -> {
            try { getContainer(); }
            catch (Exception e) { /* will retry on first request */ }
        }, "cosmos-warmup");
        t.setDaemon(true);
        t.start();
    }

    public synchronized CosmosContainer getContainer() {
        if (container != null) return container;
        // Initialization with retry loop
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                cosmosClient = new CosmosClientBuilder()...buildClient();
                cosmosClient.createDatabaseIfNotExists(dbName);
                container = database.getContainer(containerName);
                return container;
            } catch (Exception e) {
                Thread.sleep(INITIAL_BACKOFF_MS * (1L << (attempt - 1)));
            }
        }
        throw new RuntimeException("Failed to initialize Cosmos DB");
    }
}
// Background thread starts immediately after Spring context loads
// By the time tests or real requests arrive, container is ready
```

### Key Points

- Use short initial backoff (500ms) and low max backoff (10s) in the retry loop to avoid slow convergence
- Mark the warmup thread as daemon so it doesn't prevent JVM shutdown
- The `synchronized` keyword on `getContainer()` ensures thread safety between the warmup thread and API request threads
- If warmup fails, the first API request will trigger a retry — no data is lost
- This pattern is especially important in CI environments where test frameworks impose per-test timeouts

## References

- [Best practices for Azure Cosmos DB Java SDK](https://learn.microsoft.com/azure/cosmos-db/nosql/best-practice-java)
