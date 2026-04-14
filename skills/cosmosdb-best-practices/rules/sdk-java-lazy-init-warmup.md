---
title: Eagerly warm up Cosmos DB connection and gate health endpoint on readiness
impact: HIGH
impactDescription: prevents first-request timeouts caused by deferred database and container creation
tags: sdk, java, spring-boot, lazy-initialization, warmup, background-thread, startup, health-check
---

## Eagerly Warm Up Cosmos DB Connection and Gate Health on Readiness

When using lazy initialization for `CosmosClient` (common when the Cosmos DB Emulator's SSL certificate is not yet available at Spring Bean creation time), the first API request triggers database and container creation, which can take 10–30+ seconds and cause request timeouts.

To avoid this:
1. Start a daemon background thread in `@PostConstruct` that eagerly calls the lazy initializer
2. Track a `ready` flag that is set to `true` only after the warmup completes successfully
3. Make the health endpoint return **503 Service Unavailable** until the warmup completes (returns 200 only when `isReady()` is true)

This ensures that any external system (test harness, load balancer, Kubernetes readiness probe) that waits for health=200 will not send requests until Cosmos DB is fully initialized.

### Why

- `CosmosClient.buildClient()` performs initial metadata discovery
- `createDatabaseIfNotExists()` and `createContainerIfNotExists()` are slow operations on first run (especially against the emulator)
- Combined, these can exceed HTTP request timeouts (e.g., 30-second pytest timeout in CI)
- A background warmup alone is not sufficient — if health returns 200 immediately, test harnesses or load balancers may send requests before warmup completes
- Gating health on readiness guarantees the first real request never blocks on initialization

### How

Use `@PostConstruct` to start a daemon thread that calls `getContainer()`, and expose an `isReady()` method for the health endpoint:

```java
@Component
public class CosmosDbConfig {

    private volatile CosmosClient cosmosClient;
    private volatile CosmosContainer container;
    private volatile boolean ready = false;

    @PostConstruct
    public void warmup() {
        Thread warmupThread = new Thread(() -> {
            try {
                logger.info("Starting Cosmos DB warmup in background...");
                getContainer();
                ready = true;
                logger.info("Cosmos DB warmup completed");
            } catch (Exception e) {
                logger.warn("Cosmos DB warmup failed (will retry on first request): {}",
                    e.getMessage());
            }
        }, "cosmos-warmup");
        warmupThread.setDaemon(true);
        warmupThread.start();
    }

    public boolean isReady() {
        return ready;
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

```java
@RestController
public class HealthController {

    private final CosmosDbConfig cosmosConfig;

    public HealthController(CosmosDbConfig cosmosConfig) {
        this.cosmosConfig = cosmosConfig;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        if (!cosmosConfig.isReady()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "initializing"));
        }
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
```

### Example (Bad)

```java
// ❌ Health returns 200 immediately — tests start before Cosmos DB is ready
@RestController
public class HealthController {
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok")); // Always 200!
    }
}

@Component
public class CosmosDbConfig {
    @PostConstruct
    public void warmup() {
        new Thread(() -> getContainer()).start(); // Background warmup
    }
}
// Problem: Health returns 200 while warmup is still running.
// Test harness sees health=200, starts tests, first POST times out at 30s.
```

### Example (Good)

```java
// ✅ Health gates on Cosmos DB readiness — tests wait until initialization is done
@Component
public class CosmosDbConfig {
    private volatile boolean ready = false;

    @PostConstruct
    public void warmup() {
        Thread t = new Thread(() -> {
            try {
                getContainer();
                ready = true; // Only set after successful init
            } catch (Exception e) { /* will retry on first request */ }
        }, "cosmos-warmup");
        t.setDaemon(true);
        t.start();
    }

    public boolean isReady() { return ready; }

    public synchronized CosmosContainer getContainer() {
        if (container != null) return container;
        // Initialization with retry loop...
        return container;
    }
}

@RestController
public class HealthController {
    private final CosmosDbConfig cosmosConfig;

    public HealthController(CosmosDbConfig cosmosConfig) {
        this.cosmosConfig = cosmosConfig;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        if (!cosmosConfig.isReady()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "initializing"));
        }
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
// Test harness polls health every 2s for up to 120s.
// Health returns 503 until Cosmos DB is ready, then 200.
// First test POST succeeds immediately — no timeout.
```

### Key Points

- Use short initial backoff (500ms) and low max backoff (10s) in the retry loop to avoid slow convergence
- Mark the warmup thread as daemon so it doesn't prevent JVM shutdown
- The `synchronized` keyword on `getContainer()` ensures thread safety between the warmup thread and API request threads
- Set `ready = true` **after** `getContainer()` succeeds, not before — this ensures health only returns 200 when Cosmos DB is truly ready
- If warmup fails, the first API request will trigger a retry — no data is lost
- This pattern applies to any environment with readiness probes: CI test harnesses, Kubernetes, Azure App Service health checks, load balancers

## References

- [Best practices for Azure Cosmos DB Java SDK](https://learn.microsoft.com/azure/cosmos-db/nosql/best-practice-java)
