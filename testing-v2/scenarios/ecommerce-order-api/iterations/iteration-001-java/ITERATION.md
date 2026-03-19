# Iteration 001 - Java E-Commerce Order API

## Metadata
- **Date**: 2026-03-19
- **Language/SDK**: Java 17 / Spring Boot 3.2.5 / azure-cosmos 4.61.1
- **Skill Version**: N/A (control run)
- **Agent**: GitHub Copilot (Claude Opus 4.6)
- **Tester**: Automated CI

## ⚠️ Skills Verification

**Were skills loaded before building?** ❌ No

**How were skills loaded?**
- [ ] Read `skills/cosmosdb-best-practices/AGENTS.md` directly
- [ ] Skills auto-loaded from workspace
- [ ] Explicit instruction to follow skills
- [x] **Control run — skills intentionally NOT loaded**

> **Note**: This iteration tests baseline agent knowledge without the Cosmos DB
> best practices skill kit. The evaluation identifies which existing rules
> WOULD HAVE improved the output if they had been loaded.

## Prompt Used

The Java prompt from SCENARIO.md was used (Spring Boot 3 REST API with Cosmos DB NoSQL API).
The prompt specified endpoints with `totalAmount` and `orderDate` field names, but the agent
correctly followed the api-contract.yaml which uses `total` and `createdAt`.

## What the Agent Produced

### Data Model
- ✅ Embedded order items within the order document — correct for orders with 3-5 items avg (Rule 1.3)
- ✅ Used `@JsonIgnoreProperties(ignoreUnknown = true)` on Order model — handles Cosmos system properties (`_rid`, `_self`, `_etag`, `_ts`) without deserialization errors (Rule 1.5)
- ✅ Included `type = "order"` discriminator field with default value (Rule 1.11)
- ✅ Included `schemaVersion = "1.0"` field for document versioning (Rule 1.10)
- ✅ Used String fields throughout, avoiding numeric precision issues
- ⚠️ Used `double` for `total` — works for e-commerce totals but IEEE 754 can cause rounding issues at scale (Rule 1.7)
- ❌ Did not use point reads with `readItem()` for get-by-orderId — used cross-partition query instead (Rules 3.1, 3.2)

### Container Configuration
- ✅ Partition key `/customerId` — good high-cardinality choice aligned with primary access pattern (Rule 2.4, 2.6)
- ✅ Composite indexes defined for `(status, createdAt)` and `(customerId, createdAt)` (Rule 5.2)
- ✅ Autoscale throughput at 4000 RU/s (Rule 6.1)
- ⚠️ Indexed all paths (`/*`) — should selectively exclude large embedded arrays like `/items/*` to reduce write RU cost (Rule 5.3)
- ❌ No hierarchical partition key considered — could use `/customerId` + `/status` for more efficient cross-status queries (Rule 2.3)

### Repository Layer
- ✅ Parameterized queries with `SqlParameter` throughout (Rule 3.5)
- ✅ Customer queries use partition key scoping via `options.setPartitionKey()` (Rule 3.1)
- ⚠️ All queries use `SELECT *` — should project only needed fields for list endpoints (Rule 3.7)
- ❌ `getOrderById()` is a cross-partition query without partition key — fans out to all partitions (Rule 3.1)
- ❌ `getOrdersByStatus()` is a cross-partition query — expected for single-container design, but no materialized view or change feed pattern considered (Rule 9.1)
- ❌ `getOrdersByDateRange()` is a cross-partition query — same issue (Rule 9.1)
- ❌ No pagination with continuation tokens — all queries return unbounded result sets (Rule 3.4)

### SDK Usage
- ✅ Singleton `CosmosClient` via Spring `@Bean` (Rule 4.18)
- ✅ Dependent `@Bean` chain: `cosmosClient()` → `cosmosDatabase(CosmosClient)` → `ordersContainer(CosmosDatabase)` — avoids `@PostConstruct` circular dependency (Rule 4.10)
- ✅ Gateway mode for emulator compatibility (Rule 4.6)
- ✅ SSL trust-all provider with both `Security.insertProviderAt()` and `SSLContext.setDefault()` (Rule 4.6)
- ✅ Netty-tcnative exclusions in pom.xml (Rule 4.6)
- ✅ `@PreDestroy` cleanup of CosmosClient
- ✅ Session consistency level
- ❌ No `contentResponseOnWriteEnabled(true)` — Java SDK returns null from `getItem()` after create/upsert by default (Rule 4.9). The implementation works around this by returning the input object directly rather than using the response.
- ❌ No ETag-based optimistic concurrency on status updates — concurrent status transitions could cause lost updates (Rule 4.7)
- ❌ No diagnostics logging — no RU tracking, no slow query logging (Rule 4.5)
- ❌ Uses synchronous client exclusively — async `CosmosAsyncClient` would improve throughput under load (Rule 4.1)
- ❌ No Direct mode for production — always uses Gateway mode regardless of endpoint (Rule 4.4)
- ❌ No preferred regions or availability strategy configured (Rule 4.14, 4.2)
- ❌ No 429 retry configuration (Rule 4.16)

## Build Status
- **Initial Build**: ✅ Succeeded (mvn package -DskipTests)
- **Runtime Test**: ✅ All endpoints functional

## Test Results

**Pass rate: 98.9%** (90/91 tests passed)

| Status | Count |
|--------|-------|
| Passed | 90 |
| Failed | 0 |
| Errors | 0 |
| Skipped | 1 |

### Results by Category

| Category | Passed | Failed | Skipped |
|----------|--------|--------|---------|
| api_contract | 41 | 0 | 0 |
| cosmos_infrastructure | 14 | 0 | 1 |
| data_integrity | 5 | 0 | 0 |
| robustness | 30 | 0 | 0 |

### Tests Passed ✅

All 9 API endpoints implemented correctly with proper status codes:
- `POST /api/orders` → 201 with auto-calculated `total`, correct field names
- `GET /api/orders/{orderId}` → 200/404
- `GET /api/customers/{customerId}/orders` → 200 with array
- `GET /api/customers/{customerId}/orders/summary` → 200 with `totalOrders`, `totalSpent`, `averageOrderValue`
- `GET /api/orders?status=X` → 200 with filtered results
- `GET /api/orders?startDate=X&endDate=Y` → 200 with date-filtered results
- `PATCH /api/orders/{orderId}/status` → 200/404/409 with correct transition rules
- `DELETE /api/orders/{orderId}` → 204/404/409 (pending-only deletion)
- `GET /health` → 200

### Tests Skipped (1)

1 cosmos_infrastructure test skipped (likely an environment-dependent check).

## Gaps Identified

### Critical Gaps (functionality issues)
None — all 90 functional tests passed.

### Best Practice Gaps (suboptimal but works)

1. **Cross-partition query for getOrderById** — `getOrderById()` queries across all partitions because orderId is not the partition key. Since `id` is set equal to `orderId`, a point read with `readItem(orderId, partitionKey)` would be 1 RU vs 5-100+ RU, but requires knowing the customerId. Alternative: store orderId→customerId mapping or use hierarchical partition key.

2. **No ETag concurrency on status updates** — The read-modify-write pattern in `updateOrderStatus()` reads the order, modifies status, and upserts without ETag checking. Two concurrent status updates could conflict silently.

3. **SELECT * on all queries** — All queries fetch entire documents including embedded items arrays. List endpoints could benefit from projecting only needed fields (orderId, customerId, status, total, createdAt).

4. **No pagination** — All queries return unbounded result sets. At scale (~1M orders/year), status queries and date range queries could return massive result sets consuming excessive RU and memory.

5. **Indexing all paths** — `/*` included path indexes everything including the embedded `items` array. Excluding `/items/*` and `/shippingAddress/?` would reduce write RU cost by an estimated 20-40%.

6. **No diagnostics logging** — No RU consumption tracking, no slow query detection, no error diagnostics. Impossible to troubleshoot performance issues in production.

7. **Synchronous SDK only** — Uses `CosmosClient` (sync) exclusively. Under concurrent load, threads are blocked waiting for I/O, limiting throughput.

8. **No content response on write** — The code works around Java SDK's default behavior (returning null from `getItem()`) by returning the input object directly, but this means the response doesn't include server-generated fields like `_etag` or `_ts`.

### Knowledge Gaps (agent didn't know/mention)
1. **Change Feed / Materialized Views** — For cross-partition queries by status and date range, a materialized view pattern would eliminate fan-out queries entirely
2. **Hierarchical Partition Keys** — Could use `/customerId` + `/status` to optimize status queries within customer scope
3. **Direct vs Gateway mode selection** — Gateway mode used unconditionally; should auto-detect emulator vs production endpoints
4. **Availability strategy** — No hedging or circuit breaker configuration

## Rules That Would Have Helped

| Gap | Rule File | Impact |
|-----|-----------|--------|
| Cross-partition getOrderById | `query-avoid-cross-partition.md` (3.1) | Would have prompted point reads or partition-aware lookups |
| No ETag concurrency | `sdk-etag-concurrency.md` (4.7) | Would have added optimistic concurrency to status updates |
| SELECT * everywhere | `query-project-fields.md` (3.7) | Would have prompted field projection on list queries |
| No pagination | `query-pagination.md` (3.4) | Would have added continuation token support |
| Index all paths | `index-exclude-unused.md` (5.3) | Would have excluded `/items/*` from indexing |
| No diagnostics | `sdk-diagnostics.md` (4.5) | Would have added RU tracking and slow query logging |
| Sync-only SDK | `sdk-async-apis.md` (4.1) | Would have prompted async client usage |
| No content response | `sdk-java-content-response.md` (4.9) | Would have enabled `contentResponseOnWriteEnabled(true)` |
| Gateway mode always | `sdk-connection-mode.md` (4.4) | Would have prompted Direct mode for production |
| No materialized views | `pattern-change-feed.md` (9.1) | Would have suggested change feed for cross-partition queries |
| No HPK considered | `partition-hierarchical.md` (2.3) | Would have suggested hierarchical partition key design |
| No availability config | `sdk-preferred-regions.md` (4.14) | Would have prompted preferred regions configuration |
| Bean chain pattern | `sdk-java-cosmos-config.md` (4.10) | Agent already used this pattern independently ✅ |
| SSL emulator config | `sdk-emulator-ssl.md` (4.6) | Agent already handled this correctly ✅ |

## Score Summary

| Category | As-Is Score | Est. With-Skills | Notes |
|----------|-------------|------------------|-------|
| Data Model | 8/10 | 9/10 | Good embedding, type/schema version included. With skills: would add field projection DTOs |
| Partition Key | 7/10 | 8/10 | Correct `/customerId` choice. With skills: would consider HPK or materialized views |
| Indexing | 6/10 | 9/10 | Composite indexes present but indexes all paths. With skills: selective exclusions |
| SDK Usage | 5/10 | 8/10 | Singleton + emulator SSL correct. Missing: async, Direct mode, content response, diagnostics, ETags |
| Query Patterns | 5/10 | 8/10 | Parameterized + partition-scoped customer queries. Missing: point reads, projections, pagination |
| API Conformance | 10/10 | 10/10 | All 41 contract tests passed, correct field names |
| Robustness | 10/10 | 10/10 | All 30 robustness tests passed |
| **Overall** | **7/10** | **9/10** | **90/91 tests (98.9%). Strong API conformance. With skills: significant SDK and query improvements** |

## Next Steps
1. This is a control run — no code fixes or rule updates expected
2. Compare with skills-enabled iteration to validate skill kit effectiveness
3. Key areas where skills should make the biggest difference: SDK configuration (async, Direct mode, ETags, diagnostics) and query optimization (projections, pagination, point reads)
