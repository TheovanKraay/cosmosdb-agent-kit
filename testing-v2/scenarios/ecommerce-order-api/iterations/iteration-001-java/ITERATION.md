# Iteration 001 - Java E-Commerce Order API

## Metadata
- **Date**: 2026-03-16
- **Language/SDK**: Java 17 / Spring Boot 3.2.1 / azure-cosmos 4.55.0
- **Skill Version**: N/A (control run — no skills loaded)
- **Agent**: GitHub Copilot (Claude Opus 4.6)
- **Tester**: Automated CI

## ⚠️ Skills Verification

**Were skills loaded before building?** ❌ No

**How were skills loaded?**
- [ ] Read `skills/cosmosdb-best-practices/AGENTS.md` directly
- [ ] Skills auto-loaded from workspace
- [ ] Explicit instruction to follow skills
- [x] Other: **CONTROL RUN** — agent was explicitly told NOT to read AGENTS.md

**Verification question asked?** N/A — control run, no skills expected.

> **Note**: Skills were NOT loaded. This iteration tests baseline agent knowledge,
> not the skill kit effectiveness. Findings below identify what the agent got right
> using built-in knowledge and what skills WOULD HAVE improved.

## Prompt Used

```
I need to build a Spring Boot 3 REST API for an e-commerce order management system
using Azure Cosmos DB (NoSQL API). [Full Java prompt from SCENARIO.md]
```

## What the Agent Produced

### Data Model
- ✅ Embedded `OrderItem` list within `Order` — correct for items always retrieved with their order
- ✅ Used camelCase field names matching the API contract (`orderId`, `customerId`, `createdAt`, `total`)
- ✅ Separate `id` and `orderId` fields — both set to the same UUID, allowing the Cosmos `id` property to work correctly
- ⚠️ No schema version field — if the schema evolves, there's no way to distinguish document versions
- ⚠️ No type discriminator — if multiple entity types were stored in the same container, there'd be no way to distinguish them
- ❌ Used `double` for monetary values (`total`, `unitPrice`) — IEEE 754 floating-point can cause rounding errors for currency

### Container Configuration
- ✅ Partition key `/customerId` — high cardinality, aligned with the most frequent query pattern (get customer orders)
- ✅ Container auto-created with `createContainerIfNotExists`
- ❌ No custom indexing policy — uses default "index everything" policy, wasting write RU on fields like `shippingAddress` and `items` that are never queried
- ❌ Manual throughput (400 RU/s) instead of autoscale — for a production workload with ~1M orders/year, fixed 400 RU/s would be insufficient during peak loads and wasteful during idle periods
- ❌ No composite indexes defined — `ORDER BY` queries on `createdAt` for date range queries could benefit

### Repository Layer
- ✅ Parameterized queries throughout — all SQL queries use `@param` syntax (e.g., `@orderId`, `@customerId`, `@status`)
- ✅ Single-partition query for `getCustomerOrders` — correctly sets `PartitionKey` in request options
- ❌ `getOrder(orderId)` uses a cross-partition query (`SELECT * FROM c WHERE c.orderId = @orderId`) instead of a point read — since `id` equals `orderId`, a point read with `readItem(id, partitionKey)` would be ~1 RU instead of 3-10+ RU, but would require knowing the `customerId`
- ❌ `queryByStatus` and `queryByDateRange` are cross-partition queries with no partition key — these fan out to all physical partitions, consuming RU proportional to partition count
- ❌ All queries use `SELECT *` — no field projections, returning full documents including `items` array even when only summary fields are needed (e.g., customer orders listing)
- ❌ `getCustomerSummary` fetches all orders into memory and aggregates in Java — a server-side `SELECT VALUE { "totalOrders": COUNT(1), "totalSpent": SUM(c.total) }` query would be far more efficient
- ❌ `updateOrderStatus` does read-modify-write without ETag concurrency control — concurrent status updates could cause lost updates

### SDK Usage
- ✅ Singleton `CosmosClient` via Spring `@Bean(destroyMethod = "close")` — proper lifecycle management
- ✅ Session consistency level — appropriate default for most workloads
- ⚠️ Gateway connection mode — works correctly but adds an extra network hop; Direct mode reduces latency by 30-50% in production
- ❌ No `contentResponseOnWriteEnabled` — Java SDK returns null from `createItem().getItem()` by default; the code works around this by returning the local `order` object, but the returned document may differ from what's actually stored
- ❌ No diagnostic logging — no way to troubleshoot slow queries or high RU consumption in production
- ❌ No retry/error handling for 429 (throttling) beyond SDK defaults — no custom retry configuration
- ❌ No preferred regions configured — in a multi-region setup, requests would not be routed optimally

## Build Status
- **Initial Build**: ✅ Succeeded (with `mvn package -DskipTests`)
- **CI Attempt 1**: ❌ Failed — jar glob pattern not expanded (`Unable to access jarfile target/*.jar`)
- **CI Attempt 2**: ❌ Failed — SSL cert validation error with Cosmos DB Emulator (`CertPathValidatorException: signature check failed`) due to `netty-tcnative-boringssl-static` using OpenSSL instead of JDK truststore
- **CI Attempt 3**: ✅ Succeeded — after excluding `netty-tcnative` and adding `-Dio.netty.handler.ssl.noOpenSsl=true`

## Runtime Test Results

**Pass rate: 100%** (76/76 tests passed)

### Tests Passed ✅
| Endpoint | Method | Result |
|----------|--------|--------|
| `/health` | GET | ✅ Returns 200 |
| `/api/orders` | POST | ✅ Creates order, returns 201 with correct fields |
| `/api/orders/{orderId}` | GET | ✅ Returns order or 404 |
| `/api/customers/{customerId}/orders` | GET | ✅ Returns customer order list |
| `/api/customers/{customerId}/orders/summary` | GET | ✅ Correct totalOrders, totalSpent, averageOrderValue |
| `/api/orders?status=X` | GET | ✅ Filters by status |
| `/api/orders?startDate=X&endDate=Y` | GET | ✅ Filters by date range |
| `/api/orders/{orderId}/status` | PATCH | ✅ Valid transitions succeed, invalid return 409 |
| `/api/orders/{orderId}` | DELETE | ✅ Pending deleted (204), non-pending returns 409, missing returns 404 |

### Tests Failed ❌
None — all 76 tests passed.

### Bugs Found 🐛
1. **Status transition allows `pending → delivered`** — The `isValidTransition` method allows direct `pending → delivered`, but the API contract specifies only `pending → shipped`, `pending → cancelled`, and `shipped → delivered` as valid. Tests don't explicitly check that `pending → delivered` is rejected, so this passes despite being incorrect behavior.

## Gaps Identified

### Critical Gaps (performance/cost issues in production)
1. **Cross-partition query for order lookup by ID** — `getOrder(orderId)` scans all partitions instead of using a point read. At scale with many physical partitions, this would consume 5-100x more RU than necessary.
2. **No custom indexing policy** — Default "index everything" means every write indexes all fields including the `items` array and `shippingAddress`, wasting 20-80% of write RU.
3. **In-memory aggregation for customer summary** — Fetches all orders to Java heap to compute totals instead of using server-side aggregation, wasting both RU and memory.

### Best Practice Gaps (suboptimal but works)
1. **Gateway connection mode** — Adds ~2-10ms latency per request compared to Direct mode.
2. **No `contentResponseOnWriteEnabled`** — Works around the null return by using local objects, but the stored document could differ.
3. **No ETag concurrency on status updates** — Concurrent updates could silently overwrite each other.
4. **`SELECT *` everywhere** — Returns full documents including large `items` arrays when only summary fields are needed.
5. **Fixed 400 RU/s throughput** — Would throttle under load and waste money during idle time; autoscale is preferred.
6. **No SDK diagnostics logging** — Makes production troubleshooting extremely difficult.
7. **`double` for monetary values** — IEEE 754 can't exactly represent all decimal fractions (e.g., `0.1 + 0.2 ≠ 0.3`).

### Knowledge Gaps (agent didn't know/mention)
1. **Hierarchical partition keys** — Could use `/customerId` + `/orderId` for more flexible querying.
2. **Change feed for materialized views** — Cross-partition queries (by status, by date) would benefit from a materialized view pattern.
3. **Composite indexes for ORDER BY on date queries** — No composite index for `createdAt` sorting.
4. **Schema versioning** — No `_schemaVersion` field for future schema evolution.

## Rules That Would Have Helped

| Gap | Rule File | What It Would Have Changed |
|-----|-----------|---------------------------|
| Cross-partition `getOrder` | `query-avoid-cross-partition.md` | Would have prompted including partition key in the query or storing a customerId→orderId mapping to avoid fan-out |
| No point reads for single-item fetch | `query-avoid-scans.md` | Would have emphasized using `readItem(id, partitionKey)` at 1 RU instead of a `SELECT *` query at 3+ RU for known-ID lookups |
| Default indexing | `index-exclude-unused.md` | Would have excluded `/items/*`, `/shippingAddress` from indexing, saving 20-80% write RU |
| Gateway mode | `sdk-connection-mode.md` | Would have used Direct mode for 30-50% lower latency |
| No contentResponseOnWrite | `sdk-java-content-response.md` | Would have enabled `contentResponseOnWriteEnabled(true)` on the client builder |
| No ETag concurrency | `sdk-etag-concurrency.md` | Would have added ETag check on `replaceItem` to prevent lost updates |
| No diagnostics | `sdk-diagnostics.md` | Would have added diagnostic logging for high-latency or high-RU operations |
| SELECT * everywhere | `query-use-projections.md` | Would have projected only needed fields, reducing RU and bandwidth by 30-80% |
| Fixed 400 RU throughput | `throughput-autoscale.md` | Would have used autoscale (e.g., 400-4000 RU/s) for variable workloads |
| No composite indexes | `index-composite.md` | Would have added composite index for `(createdAt ASC)` and `(status, createdAt)` |
| In-memory aggregation | `query-avoid-scans.md` | Would have used server-side aggregation (`COUNT`, `SUM`) instead of fetching all docs |
| No schema versioning | `model-schema-versioning.md` | Would have added `_schemaVersion` field to documents |
| Emulator SSL failure | `sdk-emulator-ssl.md` | Would have configured SSL correctly for the emulator from the start, avoiding CI attempt 2 failure |
| Partition key design | `partition-query-patterns.md` | Would have analyzed query patterns and considered hierarchical keys |
| No preferred regions | `sdk-preferred-regions.md` | Would have configured preferred regions for multi-region availability |

## Score Summary

| Category | As-Is Score | Est. With-Skills Score | Notes |
|----------|-------------|----------------------|-------|
| Data Model | 7/10 | 9/10 | Good embedding of items; missing schema version, type discriminator, monetary precision |
| Partition Key | 7/10 | 9/10 | Correct `/customerId` choice; missing hierarchical keys and cross-partition mitigation |
| Indexing | 3/10 | 8/10 | Default policy indexes everything; no composite indexes for sort queries |
| SDK Usage | 5/10 | 9/10 | Singleton client ✅, but Gateway mode, no contentResponse, no diagnostics, no ETag |
| Query Patterns | 4/10 | 8/10 | Parameterized ✅, but cross-partition lookups, SELECT *, in-memory aggregation |
| API Conformance | 10/10 | 10/10 | 76/76 tests passed |
| **Overall** | **6/10** | **9/10** | **Functionally correct but significant production best-practice gaps** |

## Next Steps
1. Run iteration-002-java WITH skills loaded to measure improvement
2. Compare scores between control (this iteration) and skills-loaded iteration
3. No rule changes needed — this is a control run baseline
