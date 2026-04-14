---
title: Eagerly warm up Cosmos DB connection and gate health endpoint on readiness
impact: HIGH
impactDescription: prevents first-request timeouts caused by deferred database and container creation
tags: sdk, java, spring-boot, lazy-initialization, warmup, background-thread, startup, health-check
---

## Eagerly Warm Up Cosmos DB Connection and Gate Health on Readiness

When using lazy initialization for `CosmosClient` (common when the Cosmos DB Emulator's SSL certificate is not yet available at Spring Bean creation time), the first API request triggers database and container creation, which can take 10–30+ seconds and cause request timeouts.

To avoid this:
1. Start a **daemon** background thread in `@PostConstruct` with a **two-phase warmup**:
   - **Phase 1**: Lightweight HTTP pre-check — poll the Cosmos DB endpoint every 2s with a short timeout (2s) until it responds with HTTP 200. This avoids wasting time in the SDK's internal ~23s timeout per call when the emulator returns 503/10001.
   - **Phase 2**: Once the endpoint is responding, build the client and create database/container. On failure, close and null the client, then retry.
2. Track a `ready` flag that is set to `true` only after the warmup completes successfully
3. Make the health endpoint return **503 Service Unavailable** until the warmup completes (returns 200 only when `isReady()` is true)

This ensures that any external system (test harness, load balancer, Kubernetes readiness probe) that waits for health=200 will not send requests until Cosmos DB is fully initialized.

### Why

- `CosmosClient.buildClient()` performs initial metadata discovery
- `createDatabaseIfNotExists()` and `createContainerIfNotExists()` are slow operations on first run (especially against the emulator)
- Combined, these can exceed HTTP request timeouts (e.g., 30-second pytest timeout in CI)
- A background warmup alone is not sufficient — if health returns 200 immediately, test harnesses or load balancers may send requests before warmup completes
- The Cosmos DB Emulator may return 503/10001 (service starting) for **60–120+ seconds** after launch
- Each SDK call that fails on 503 takes ~23s (SDK internal timeout), wasting the 120s health check window. With 3 inner retries, each warmup cycle takes ~69s, giving only 1-2 chances in 120s
- A lightweight HTTP pre-check (2s timeout) lets the warmup poll every 2s without wasting time in SDK timeouts
- Gating health on readiness guarantees the first real request never blocks on initialization

### How

Use `@PostConstruct` to start a daemon thread with **two-phase warmup**: Phase 1 polls the endpoint with lightweight HTTP, Phase 2 does the actual SDK initialization. Expose an `isReady()` method for the health endpoint:

```java
@Component
public class CosmosDbConfig {

    private volatile CosmosClient cosmosClient;
    private volatile CosmosContainer container;
    private volatile boolean ready = false;

    private boolean isEndpointReady() {
        try {
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            if (conn instanceof HttpsURLConnection && isEmulatorEndpoint()) {
                ((HttpsURLConnection) conn).setHostnameVerifier((h, s) ->
                        "localhost".equals(h) || "127.0.0.1".equals(h));
            }
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestMethod("GET");
            try {
                return conn.getResponseCode() == 200;
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            return false;
        }
    }

    @PostConstruct
    public void warmup() {
        Thread warmupThread = new Thread(() -> {
            // Phase 1: Poll endpoint until it responds with 200
            while (!Thread.currentThread().isInterrupted()) {
                if (isEndpointReady()) break;
                try { Thread.sleep(2000); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); return;
                }
            }
            // Phase 2: Build client and create database/container
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    getContainer();
                    ready = true;
                    return;
                } catch (Exception e) {
                    closeClient();
                    try { Thread.sleep(2000); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt(); return;
                    }
                }
            }
        }, "cosmos-warmup");
        warmupThread.setDaemon(true);
        warmupThread.start();
    }

    public boolean isReady() { return ready; }

    public synchronized CosmosContainer getContainer() {
        if (container != null) return container;
        // No inner retry — warmup outer loop handles retry
        // buildClient, createDatabaseIfNotExists, createContainerIfNotExists
        return container;
    }

    private synchronized void closeClient() {
        if (cosmosClient != null) {
            try { cosmosClient.close(); } catch (Exception e) {}
            cosmosClient = null;
        }
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
// ❌ Warmup calls SDK directly — each failed call takes ~23s (SDK internal timeout)
// In 120s health check window, only 1-2 full retry cycles complete
@Component
public class CosmosDbConfig {
    @PostConstruct
    public void warmup() {
        new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    getContainer(); // Takes ~23s per failed attempt!
                    ready = true;
                    return;
                } catch (Exception e) {
                    Thread.sleep(1000);
                }
            }
        }).start();
    }
}
// Problem: Emulator returns 503/10001 for 60–120+ seconds while starting.
// Each getContainer() call takes ~23s on failure (SDK internal timeout).
// In 120s window: only ~4-5 attempts. May not be enough.
```

### Example (Good)

```java
// ✅ Two-phase warmup: lightweight pre-check → SDK init
@Component
public class CosmosDbConfig {
    private volatile boolean ready = false;

    private boolean isEndpointReady() {
        // 2s timeout HTTP GET to endpoint — fast check, no SDK overhead
        try {
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            if (conn instanceof HttpsURLConnection && isEmulatorEndpoint()) {
                // Only bypass hostname verification for local emulator
                ((HttpsURLConnection) conn).setHostnameVerifier((h, s) ->
                        "localhost".equals(h) || "127.0.0.1".equals(h));
            }
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            try { return conn.getResponseCode() == 200; }
            finally { conn.disconnect(); }
        } catch (Exception e) { return false; }
    }

    @PostConstruct
    public void warmup() {
        Thread t = new Thread(() -> {
            // Phase 1: Poll every 2s until endpoint responds with 200
            while (!Thread.currentThread().isInterrupted()) {
                if (isEndpointReady()) break;
                try { Thread.sleep(2000); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); return;
                }
            }
            // Phase 2: Endpoint ready — build client, create db/container
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    getContainer();
                    ready = true;
                    return;
                } catch (Exception e) {
                    closeClient(); // Fresh client on next attempt
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
        // Single attempt — warmup outer loop handles retry
        // buildClient, createDatabaseIfNotExists, createContainerIfNotExists
        return container;
    }
}

@RestController
public class HealthController {
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        if (!cosmosConfig.isReady()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "initializing"));
        }
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
// Phase 1 polls every 2s with 2s timeout — detects emulator readiness in seconds.
// Phase 2 only starts once emulator responds with 200 — SDK init succeeds quickly.
// Health returns 503→200 when ready. CI healthcheck polls for 120s.
```

### Key Points

- **Two-phase warmup**: Phase 1 uses lightweight HTTP to detect emulator readiness (2s per poll). Phase 2 only calls the SDK after the endpoint responds with 200. This avoids wasting the 120s health check window on SDK internal timeouts (~23s per failed call).
- **Close and rebuild client on failure**: If Phase 2 fails, close the `CosmosClient` and set it to `null` so the next attempt builds a fresh client with clean state.
- Mark the warmup thread as **daemon** so it doesn't prevent JVM shutdown
- The `synchronized` keyword on `getContainer()` ensures thread safety between the warmup thread and API request threads
- Set `ready = true` **after** `getContainer()` succeeds, not before — this ensures health only returns 200 when Cosmos DB is truly ready
- This pattern applies to any environment with readiness probes: CI test harnesses, Kubernetes, Azure App Service health checks, load balancers

## References

- [Best practices for Azure Cosmos DB Java SDK](https://learn.microsoft.com/azure/cosmos-db/nosql/best-practice-java)
