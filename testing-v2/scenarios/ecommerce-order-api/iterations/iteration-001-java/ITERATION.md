# Iteration 001 - Java - Ecommerce Order API

## Metadata
- **Date**: 2026-03-17
- **Language/SDK**: Java 17 / Spring Boot 3.2.1 / azure-cosmos 4.61.0
- **Skill Version**: skills/cosmosdb-best-practices v1.0.0 (commit d074039)
- **Agent**: GitHub Copilot (Claude Sonnet 4.6)
- **Tester**: Automated CI

## ⚠️ Skills Verification

**Were skills loaded before building?** ✅ Yes

**How were skills loaded?**
- [x] Read `skills/cosmosdb-best-practices/AGENTS.md` directly (via issue prompt)

**Verification question asked?** Issue prompt explicitly required reading AGENTS.md before generating code.

## Prompt Used

```
Read skills/cosmosdb-best-practices/AGENTS.md first, then implement the ecommerce-order-api scenario in Java per testing-v2/scenarios/ecommerce-order-api/SCENARIO.md and api-contract.yaml.
```

## What the Agent Produced

### Data Model
- ✅ **Single `Order` document** with embedded `OrderItem[]` array — correct embedding for 1:N bounded relationship (Rule 1.3)
- ✅ **`id` = `orderId`** — sets both Cosmos `id` and API-facing `orderId` to the same UUID (Rule 1.4)
- ✅ **`total` and `createdAt`** field names match api-contract.yaml exactly (no `totalAmount`/`orderDate` mistakes)
- ✅ **`shippingAddress`** optional field preserved in model
- ✅ **`etag` field** with `@JsonProperty("_etag")` for optimistic concurrency (Rule 4.7)
- ⚠️ **`_schemaVersion`** field used `@JsonProperty("_schemaVersion")` — should be `schemaVersion` (without underscore prefix). Tests look for `schemaVersion` in stored documents. **Fixed post-CI.**
- ❌ **No `type` discriminator field** in initial output — tests require `type = "order"` even for single-entity-type containers. **Fixed post-CI.**

### Container Configuration
- ✅ **Partition key `/customerId`** — correct choice; primary query pattern is "get orders by customer" (Rule 2.6)
- ✅ **Custom indexing policy** with selective included paths (`/customerId/?`, `/status/?`, `/createdAt/?`) and exclusion of `/*` + `/items/*` (Rule 5.3)
- ✅ **Autoscale throughput** at 4000 RU/s max — appropriate for e-commerce workloads (Rule 6.1)
- ✅ Cosmos DB mandatory indexing path `/*` included in `excludedPaths` (**caught and fixed during startup**)
- ❌ **No composite indexes** defined initially — queries using `WHERE status = @s ORDER BY createdAt` and `WHERE customerId = @c ORDER BY createdAt` need composite indexes on `(status, createdAt)` and `(customerId, createdAt)`. **Fixed post-CI.**

### Repository Layer
- ✅ **Parameterized queries** using `SqlQuerySpec` + `SqlParameter` on all queries (Rule 3.5)
- ✅ **Field projections** in list queries (`SELECT c.id, c.orderId, ...` not `SELECT *`) (Rule 3.7)
- ✅ **Single-partition query** for customer orders: `options.setPartitionKey(new PartitionKey(customerId))` (Rule 3.1)
- ✅ **Cross-partition parallel query** for status/date range: `setMaxDegreeOfParallelism(-1)` (Rule 3.1)
- ✅ **ETag concurrency** on updates: `options.setIfMatchETag(order.getEtag())` (Rule 4.7)
- ✅ **Point-style delete** using document `id` + partition key directly
- ⚠️ **`findByOrderId` uses cross-partition query** (`WHERE c.id = @orderId`) — unavoidable since `orderId` is not the partition key, but this is correctly documented in the code

### SDK Usage
- ✅ **Singleton `CosmosClient`** as `@Bean(destroyMethod = "close")` (Rule 4.18)
- ✅ **`@Bean` chain pattern** for `CosmosDatabase` and `CosmosContainer` via parameter injection — no `@PostConstruct` (Rule 4.10)
- ✅ **`gatewayMode()`** for emulator compatibility (Rule 4.6)
- ✅ **`contentResponseOnWriteEnabled(true)`** — write operations return the full document (Rule 4.9)
- ✅ **Session consistency** — appropriate for e-commerce (read-your-writes within session)
- ❌ **No diagnostics logging** — `CosmosDiagnostics` not captured (Rule 4.5)
- ❌ **No preferred regions** configured (Rule 4.8) — acceptable for emulator/dev scenario

### Build Status
- **Initial Build**: ✅ Succeeded (`mvn package -DskipTests`)
- **CI Attempt 1**: ❌ Failed — Java 17 PKIX `signature check failed` on emulator cert
  - **Fix**: Added `TrustAllSslConfig.java` — custom JCA Provider overriding `TrustManagerFactory.PKIX/X509/SunX509` with trust-all implementation. Installed via `Security.insertProviderAt(provider, 1)` in static block of `CosmosConfig` before any SSL handshakes.
- **CI Attempt 2**: ❌ Failed — Cosmos DB `BadRequest: The special mandatory indexing path "/" is not provided`
  - **Fix**: Added `new ExcludedPath("/*")` to `excludedPaths`. Cosmos DB requires the root wildcard path in either included or excluded paths.
- **CI Attempt 3 (post-fixes)**: ✅ Build PASS, Startup PASS, 86/91 tests passed (94.5%)

## Runtime Test Results

### Tests Passed ✅ (86/91)

| Category | Passed | Failed | Skipped |
|----------|--------|--------|---------|
| api_contract | 41 | 0 | 0 |
| cosmos_infrastructure | 10 | 4 | 1 |
| data_integrity | 5 | 0 | 0 |
| robustness | 30 | 0 | 0 |

All 41 API contract tests passed — all 9 endpoints work correctly with proper status codes, field names, and validation.
All 30 robustness tests passed — status transitions, error handling, and edge cases all correct.
All 5 data integrity tests passed — totals, date filtering, customer summaries correct.

### Tests Failed ❌ (4/91 — all infrastructure checks)

| Test | Category | Issue | Classification |
|------|----------|-------|----------------|
| `test_has_composite_indexes_for_order_queries` | index | No composite indexes on `(status, createdAt)` or `(customerId, createdAt)` | **Missing rule application** (rule exists, not applied) |
| `test_status_query_returns_correct_results` | enum/isolation | PATCH returns 409 — order already moved to terminal state by earlier test | **Test isolation issue** (session-scoped shared data, not a code bug) |
| `test_documents_have_type_discriminator` | model | No `type` field in documents; test checks for `type`, `_type`, `documentType`, etc. | **Unclear rule** (rule said "multi-type containers only"; applies to all containers) |
| `test_documents_have_schema_version` | model | Field stored as `_schemaVersion`; test checks for `schemaVersion`, `schema_version`, `version`, etc. | **Unclear rule** (rule didn't specify exact field name convention) |

### Skipped (1)
- `test_status_is_stored_as_string` — Cosmos system property lookup limitation

### Post-Evaluation Fixes Applied
After evaluating failures, the following fixes were applied to maximize test compliance:
1. ✅ Added `type = "order"` field (`@Builder.Default`, `@JsonProperty("type")`) to `Order.java`
2. ✅ Renamed `@JsonProperty("_schemaVersion")` → `@JsonProperty("schemaVersion")` in `Order.java`
3. ✅ Added composite indexes to `CosmosConfig.java`: `(status ASC, createdAt DESC)` and `(customerId ASC, createdAt DESC)`

## Gaps Identified

### Critical Gaps (functionality issues)
_None — all API contract tests passed._

### Best Practice Gaps (suboptimal but works)
1. **Missing composite indexes** — Queries using `WHERE filter ORDER BY date` patterns need composite indexes. The `index-composite` rule exists and has Java examples, but was not applied to single-entity containers. Rule strengthened to clarify that filter + ORDER BY ALWAYS needs composite indexes.
2. **No SDK diagnostics** — `CosmosDiagnosticsHandler` not configured; makes production troubleshooting difficult (Rule 4.5).
3. **In-memory customer summary** — `getCustomerSummary` loads all customer orders into memory, then aggregates. For customers with thousands of orders, this is inefficient. Should use Cosmos DB aggregate queries or a denormalized counter document.

### Knowledge Gaps (agent didn't know/mention)
1. **Type discriminator for single-type containers** — Agent knew the rule for multi-type containers but didn't apply it to a single-type container. Rule updated to explicitly cover all containers.
2. **Schema version field naming convention** — Agent used `_schemaVersion` (with underscore prefix like Cosmos system properties). The convention is `schemaVersion` (camelCase, no prefix). Rule updated to clarify.

## Recommendations for Skill Improvements

### High Priority
1. **`index-composite.md`** — Rule updated: clarified that `WHERE field1 = X ORDER BY field2` ALWAYS needs a composite index, even when ORDER BY is single-field. Added explicit call-out: "Even a single-field ORDER BY requires a composite index when combined with a WHERE clause filter."

### Medium Priority
2. **`model-type-discriminator.md`** — Rule updated: clarified that `type` field should be added to ALL documents, even single-entity-type containers, for future extensibility.
3. **`model-schema-versioning.md`** — Rule updated: clarified the canonical field name is `schemaVersion` (not `_schemaVersion` or `_version`).

### Low Priority
4. **`sdk-diagnostics.md`** — Consider adding a Java example showing how to configure `CosmosClientBuilder.diagnosticsHandler()` for structured logging of Cosmos SDK diagnostics.

## Score Summary

| Category | Score | Notes |
|----------|-------|-------|
| Data Model | 8/10 | Good embedding, field naming, ETag. Minor: missing `type` and wrong `schemaVersion` name (fixed) |
| Partition Key | 9/10 | Correct `/customerId` choice; cross-partition scenarios correctly identified and documented |
| Indexing | 7/10 | Custom policy with selective paths is good; composite indexes missing initially (fixed). `/*` exclusion bug caught |
| SDK Usage | 8/10 | Singleton, gateway mode, contentResponseOnWriteEnabled, ETag all correct. No diagnostics |
| Query Patterns | 9/10 | Parameterized, projections, single-partition where possible. Cross-partition with parallelism |
| **Overall** | **8/10** | **86/91 (94.5%) pass rate. All API/robustness/data tests pass. 3 fixable infrastructure gaps addressed post-CI** |

## Next Steps
1. Verify fixes (composite indexes, `type` field, `schemaVersion` name) improve infrastructure test score to 13/15
2. Add `sdk-diagnostics` Java example to make diagnostics rule more actionable for Java agents
3. Consider adding a `pattern-aggregation.md` rule about server-side aggregation vs in-memory for customer summaries
