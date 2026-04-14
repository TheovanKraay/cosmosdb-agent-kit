---
title: Eagerly warm up Cosmos DB connection and gate health endpoint on readiness
impact: HIGH
impactDescription: prevents first-request timeouts caused by deferred database and container creation
tags: sdk, java, spring-boot, lazy-initialization, warmup, background-thread, startup, health-check
---

## Eagerly Warm Up Cosmos DB Connection and Gate Health on Readiness

When using lazy initialization for `CosmosClient` (common when the Cosmos DB Emulator's SSL certificate is not yet available at Spring Bean creation time), the first API request triggers database and container creation, which can take 10–30+ seconds and cause request timeouts.

To avoid this:
1. Start a daemon background thread in `@PostConstruct` that eagerly calls the lazy initializer **in a loop with a generous time budget** (e.g., 110 seconds)
2. Track a `ready` flag that is set to `true` only after the warmup completes successfully
3. Make the health endpoint return **503 Service Unavailable** until the warmup completes (returns 200 only when `isReady()` is true)

This ensures that any external system (test harness, load balancer, Kubernetes readiness probe) that waits for health=200 will not send requests until Cosmos DB is fully initialized.

### Why

- `CosmosClient.buildClient()` performs initial metadata discovery
- `createDatabaseIfNotExists()` and `createContainerIfNotExists()` are slow operations on first run (especially against the emulator)
- Combined, these can exceed HTTP request timeouts (e.g., 30-second pytest timeout in CI)
- A background warmup alone is not sufficient — if health returns 200 immediately, test harnesses or load balancers may send requests before warmup completes
- The Cosmos DB Emulator may return 503/10001 (service starting) for 30–90+ seconds after launch — a single inner retry cycle may exhaust its attempts before the emulator is ready
- Gating health on readiness guarantees the first real request never blocks on initialization

### How

Use `@PostConstruct` to start a daemon thread with an **outer retry loop** (time-budgeted) that keeps calling `getContainer()` until success. Expose an `isReady()` method for the health endpoint:

```java
@Component
public class CosmosDbConfig {

    private volatile CosmosClient cosmosClient;
    private volatile CosmosContainer container;
    private volatile boolean ready = false;

    @PostConstruct
    public void warmup() {
        Thread warmupThread = new Thread(() -> {
            long deadline = System.currentTimeMillis() + 110_000; // 110s budget
            while (System.currentTimeMillis() < deadline) {
                try {
                    getContainer();
                    ready = true;
                    logger.info("Cosmos DB warmup completed");
                    return;
                } catch (Exception e) {
                    logger.warn("Warmup attempt failed, retrying...");
                    try { Thread.sleep(2000); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt(); return;
                    }
                }
            }
        }, "cosmos-warmup");
        warmupThread.setDaemon(true);
        warmupThread.start();
    }

    public boolean isReady() {
        return ready;
    }

    public synchronized CosmosContainer getContainer() {
        if (container != null) return container;
        // Build client, create database/container with inner retry loop (5 attempts)
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
// ❌ Warmup calls getContainer() once — if all inner retries fail, ready is never set
@Component
public class CosmosDbConfig {
    @PostConstruct
    public void warmup() {
        new Thread(() -> {
            try {
                getContainer();
                ready = true;
            } catch (Exception e) {
                // Warmup failed — ready stays false, health stays 503 FOREVER
            }
        }).start();
    }
}
// Problem: Emulator returns 503/10001 for 60+ seconds while starting.
// Inner retry loop (10 attempts) exhausts before emulator is ready.
// Warmup gives up, health never returns 200, CI declares startup failure.
```

### Example (Good)

```java
// ✅ Outer retry loop keeps trying for 110s — survives slow emulator startup
@Component
public class CosmosDbConfig {
    private volatile boolean ready = false;

    @PostConstruct
    public void warmup() {
        Thread t = new Thread(() -> {
            long deadline = System.currentTimeMillis() + 110_000;
            while (System.currentTimeMillis() < deadline) {
                try {
                    getContainer();
                    ready = true;
                    return;
                } catch (Exception e) {
                    try { Thread.sleep(2000); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt(); return;
                    }
                }
            }
        }, "cosmos-warmup");
        t.setDaemon(true);
        t.start();
    }

    public boolean isReady() { return ready; }

    public synchronized CosmosContainer getContainer() {
        if (container != null) return container;
        // Inner retry loop (5 attempts, 500ms–5s backoff)
        for (int i = 1; i <= 5; i++) {
            try {
                // buildClient, createDatabaseIfNotExists, createContainerIfNotExists
                return container;
            } catch (Exception e) {
                Thread.sleep(Math.min(500 * (1L << (i-1)), 5000));
            }
        }
        throw new RuntimeException("Failed after 5 attempts");
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
// Outer loop retries every ~15s (inner cycle + 2s pause).
// Even if emulator takes 90s to start, warmup succeeds within 110s budget.
// Health returns 503→200 when ready. CI healthcheck polls for 120s.
```

### Key Points

- **Two-level retry**: Inner retry loop (5 attempts, 500ms–5s backoff) handles transient failures. Outer loop (110s budget) handles slow emulator startup (503/10001).
- Use short backoffs in the inner loop (500ms initial, 5s cap) to keep each cycle fast
- Mark the warmup thread as daemon so it doesn't prevent JVM shutdown
- The `synchronized` keyword on `getContainer()` ensures thread safety between the warmup thread and API request threads
- Set `ready = true` **after** `getContainer()` succeeds, not before — this ensures health only returns 200 when Cosmos DB is truly ready
- If warmup eventually fails (budget exhausted), the first API request will trigger `getContainer()` directly — no data is lost
- This pattern applies to any environment with readiness probes: CI test harnesses, Kubernetes, Azure App Service health checks, load balancers

## References

- [Best practices for Azure Cosmos DB Java SDK](https://learn.microsoft.com/azure/cosmos-db/nosql/best-practice-java)
