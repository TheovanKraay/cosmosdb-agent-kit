---
title: Eagerly warm up Cosmos DB connection and gate health endpoint on readiness
impact: HIGH
impactDescription: prevents first-request timeouts caused by deferred database and container creation
tags: sdk, java, spring-boot, lazy-initialization, warmup, background-thread, startup, health-check
---

## Eagerly Warm Up Cosmos DB Connection and Gate Health on Readiness

When using lazy initialization for `CosmosClient` (common when the Cosmos DB Emulator's SSL certificate is not yet available at Spring Bean creation time), the first API request triggers database and container creation, which can take 10–30+ seconds and cause request timeouts.

To avoid this:
1. Start a **daemon** background thread in `@PostConstruct` that eagerly calls the lazy initializer **indefinitely** (no timeout — the daemon thread is killed when the JVM shuts down)
2. Track a `ready` flag that is set to `true` only after the warmup completes successfully
3. Make the health endpoint return **503 Service Unavailable** until the warmup completes (returns 200 only when `isReady()` is true)

This ensures that any external system (test harness, load balancer, Kubernetes readiness probe) that waits for health=200 will not send requests until Cosmos DB is fully initialized.

### Why

- `CosmosClient.buildClient()` performs initial metadata discovery
- `createDatabaseIfNotExists()` and `createContainerIfNotExists()` are slow operations on first run (especially against the emulator)
- Combined, these can exceed HTTP request timeouts (e.g., 30-second pytest timeout in CI)
- A background warmup alone is not sufficient — if health returns 200 immediately, test harnesses or load balancers may send requests before warmup completes
- The Cosmos DB Emulator may return 503/10001 (service starting) for **60–120+ seconds** after launch — any fixed timeout budget may be exceeded
- Using a daemon thread with no timeout ensures warmup never gives up prematurely — the external health check timeout (e.g., 120s in CI) is the real constraint
- Gating health on readiness guarantees the first real request never blocks on initialization

### How

Use `@PostConstruct` to start a daemon thread with an **infinite outer retry loop** that keeps calling `getContainer()` until success. Expose an `isReady()` method for the health endpoint:

```java
@Component
public class CosmosDbConfig {

    private volatile CosmosClient cosmosClient;
    private volatile CosmosContainer container;
    private volatile boolean ready = false;

    @PostConstruct
    public void warmup() {
        Thread warmupThread = new Thread(() -> {
            // No timeout — daemon thread is killed on JVM shutdown
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    getContainer();
                    ready = true;
                    logger.info("Cosmos DB warmup completed");
                    return;
                } catch (Exception e) {
                    logger.warn("Warmup cycle failed, retrying in 1s...");
                    try { Thread.sleep(1000); } catch (InterruptedException ie) {
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
        // Build client, create database/container with inner retry loop (3 attempts)
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
// ❌ Warmup uses a fixed timeout budget — can expire before emulator is ready
@Component
public class CosmosDbConfig {
    @PostConstruct
    public void warmup() {
        new Thread(() -> {
            long deadline = System.currentTimeMillis() + 110_000; // 110s budget
            while (System.currentTimeMillis() < deadline) {
                try {
                    getContainer();
                    ready = true;
                    return;
                } catch (Exception e) {
                    Thread.sleep(2000);
                }
            }
            // Budget exhausted — ready stays false, health stays 503 FOREVER
        }).start();
    }
}
// Problem: Emulator returns 503/10001 for 60–120+ seconds while starting.
// Fixed 110s budget expires before emulator is ready.
// Warmup gives up, health never returns 200, CI declares startup failure.
```

### Example (Good)

```java
// ✅ Infinite retry in daemon thread — never gives up, survives any emulator delay
@Component
public class CosmosDbConfig {
    private volatile boolean ready = false;

    @PostConstruct
    public void warmup() {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    getContainer();
                    ready = true;
                    return;
                } catch (Exception e) {
                    try { Thread.sleep(1000); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt(); return;
                    }
                }
            }
        }, "cosmos-warmup");
        t.setDaemon(true);  // Killed on JVM shutdown — no risk of hanging
        t.start();
    }

    public boolean isReady() { return ready; }

    public synchronized CosmosContainer getContainer() {
        if (container != null) return container;
        // Inner retry loop (3 attempts, 500ms–1s backoff) — keeps each cycle fast
        for (int i = 1; i <= 3; i++) {
            try {
                // buildClient, createDatabaseIfNotExists, createContainerIfNotExists
                return container;
            } catch (Exception e) {
                Thread.sleep(Math.min(500 * (1L << (i-1)), 1000));
            }
        }
        throw new RuntimeException("Failed after 3 attempts");
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
// Daemon thread retries every ~4s (inner cycle + 1s pause). Never gives up.
// Even if emulator takes 120s to start, warmup succeeds as soon as it's ready.
// Health returns 503→200 when ready. CI healthcheck polls for 120s.
```

### Key Points

- **No timeout on warmup**: The daemon thread retries indefinitely. The external health check timeout (e.g., 120s in CI) is the real constraint — don't duplicate it with a separate budget that might expire first.
- **Inner retry + outer retry**: Inner retry loop (3 attempts, fast backoff) handles per-cycle transient failures. Outer infinite loop handles slow emulator startup (503/10001 for 60–120+ seconds).
- Use short backoffs in the inner loop (500ms initial, 1s cap) to keep each cycle fast (~3-5s per cycle)
- Mark the warmup thread as **daemon** so it doesn't prevent JVM shutdown
- The `synchronized` keyword on `getContainer()` ensures thread safety between the warmup thread and API request threads
- Set `ready = true` **after** `getContainer()` succeeds, not before — this ensures health only returns 200 when Cosmos DB is truly ready
- If warmup hasn't completed yet, API requests will trigger `getContainer()` directly (which has its own retry) — no data is lost
- This pattern applies to any environment with readiness probes: CI test harnesses, Kubernetes, Azure App Service health checks, load balancers

## References

- [Best practices for Azure Cosmos DB Java SDK](https://learn.microsoft.com/azure/cosmos-db/nosql/best-practice-java)
