# Iteration 001 - Java - Ecommerce Order API

## Metadata
- **Date**: 2026-03-16
- **Language/SDK**: Java 17 / Spring Boot 3.2.0 / azure-cosmos 4.61.0
- **Skill Version**: N/A — control run
- **Agent**: GitHub Copilot (Claude Sonnet 4.6, automated iteration)
- **Tester**: Automated CI (windows-latest runner)
- **Run Type**: ❌ CONTROL RUN — Skills NOT loaded

## ⚠️ Skills Verification

**Were skills loaded before building?** ❌ No (CONTROL RUN)

**How were skills loaded?**
- [ ] Read `skills/cosmosdb-best-practices/AGENTS.md` directly
- [ ] Skills auto-loaded from workspace
- [ ] Explicit instruction to follow skills
- [x] **None** — this is a baseline/control run

> **Note**: This iteration tests baseline agent knowledge without the skill kit.
> All findings below identify gaps that **existing rules would have prevented**
> had the agent read `AGENTS.md` before generating code.

## Prompt Used

Java Spring Boot 3 prompt from `SCENARIO.md`:
- Build a Spring Boot 3 REST API backed by Azure Cosmos DB
- Implement all endpoints defined in `api-contract.yaml`
- Use `azure-cosmos` SDK 4.61.0 (not Spring Data Cosmos)
- Read `COSMOS_ENDPOINT` and `COSMOS_KEY` from environment variables
- Expose `/health` endpoint returning `{"status":"ok"}`

## What the Agent Produced

### Data Model

- ✅ `Order` model includes all required fields from `api-contract.yaml`: `orderId`, `customerId`, `status`, `items`, `total`, `createdAt`
- ✅ Correct field names (`total` not `totalAmount`, `createdAt` not `orderDate`)
- ✅ `OrderItem` has correct fields: `productId`, `productName`, `quantity`, `unitPrice`
- ✅ Both `Order` and `OrderItem` have default (no-arg) constructors required for deserialization
- ✅ `@JsonProperty` annotations on all fields
- ✅ Both `id` and `orderId` set to the same UUID, satisfying Cosmos DB's `id` requirement
- ❌ No `type` discriminator field (`"order"`) — Rule 1.11 not applied
- ❌ No `schemaVersion` field — Rule 1.10 not applied
- ❌ `ObjectMapper` created as `new ObjectMapper()` without `FAIL_ON_UNKNOWN_PROPERTIES = false`
  → **Primary failure cause**: Cosmos DB documents contain system properties (`_rid`, `_self`,
  `_etag`, `_ts`, `_attachments`) that Jackson's default ObjectMapper rejects with
  `UnrecognizedPropertyException`, surfaced as `"Failed to deserialize order"` on every read

### Container Configuration

- ✅ Partition key: `/customerId` — correct for the dominant "orders by customer" access pattern (Rule 2.6)
- ✅ Throughput provisioned at 400 RU/s manual (Rule 6.4)
- ✅ Container created on startup via `createContainerIfNotExists`
- ❌ No composite index policy defined for `(status, createdAt)` queries
  → Test `test_has_composite_indexes_for_order_queries` explicitly checks for this (Rule 5.2)
- ❌ Default indexing policy retained — all paths indexed, including `items[*].productId`,
  `shippingAddress`, etc. that are never queried (Rule 5.3)

### Repository Layer

- ⚠️ `findById(orderId)` uses a cross-partition SQL query `WHERE c.orderId = @orderId`
  instead of a point read. Because `orderId = id` and partition key is `customerId`, the agent
  cannot do a point read without also knowing `customerId`. This is the expected baseline trade-off
  when GET `/api/orders/{orderId}` does not receive `customerId` in the URL. However, the query
  options do not set a partition key, causing a full cross-partition scan on every GET, PATCH,
  and DELETE by orderId. (Rule 3.1)
- ❌ All queries use `SELECT *` instead of projecting only needed fields (Rule 3.7)
- ✅ `findByCustomerId` correctly sets `PartitionKey` on `CosmosQueryRequestOptions` for
  a single-partition query
- ✅ Parameterized queries throughout — no string interpolation (Rule 3.5)
- ✅ `delete` uses point delete with partition key (Rule 3.1)
- ✅ `update` uses `replaceItem` with partition key (correct pattern)
- ❌ **Critical bug**: `OrderRepository` uses `new ObjectMapper()` (no `FAIL_ON_UNKNOWN_PROPERTIES=false`)
  then calls `objectMapper.treeToValue(item, Order.class)` on the full Cosmos document node,
  which includes system fields → all read methods throw `RuntimeException("Failed to deserialize order")`

### SDK Usage

- ✅ `CosmosClient` created once as a Spring `@Bean` singleton (Rule 4.18)
- ✅ Gateway mode used (`gatewayMode()`) — correct for emulator compatibility (Rule 4.6)
- ✅ Endpoint and key read from `COSMOS_ENDPOINT` / `COSMOS_KEY` environment variables
- ✅ SSL emulator fix applied (trust-all JCA provider to handle self-signed cert)
- ❌ No ETag optimistic concurrency on `updateStatus` — concurrent PATCH requests could overwrite
  each other silently (Rule 4.7)
- ❌ `contentResponseOnWriteEnabled` not set — Java SDK returns no body by default on writes,
  forcing an extra read after every create/update to get back the created document (Rule 4.9)
- ❌ No diagnostics or logging of Cosmos request charges (Rule 4.5)
- ❌ Direct mode not configured — Gateway mode appropriate for emulator but suboptimal for
  production (Rule 4.4); however this is acceptable for a test iteration

## Build Status
- **Initial Build**: ❌ Failed — `mvn package` succeeded but app crashed at startup with
  `SSLHandshakeException: PKIX path validation failed: signature check failed`
  (Java 17 strict PKIX validation rejects Cosmos Emulator cert even after keytool import)
- **After Fix**: ✅ Succeeded — installed trust-all JCA Security Provider in `CosmosConfig` static block
- **Runtime Test**: ✅ Performed — 91 tests executed (39 passed, 51 failed, 1 skipped)

## Runtime Test Results

| Category | Passed | Failed | Skipped |
|----------|--------|--------|---------|
| `api_contract` | 15 | 26 | 0 |
| `cosmos_infrastructure` | 10 | 4 | 1 |
| `data_integrity` | 5 | 0 | 0 |
| `robustness` | 9 | 21 | 0 |
| **Total** | **39** | **51** | **1** |

### Tests Passed ✅
- `POST /api/orders` — order creation returns 201 with all required fields ✅
- `/health` — returns 200 ✅
- `data_integrity` — all 5 write consistency tests pass (create is working) ✅
- `cosmos_infrastructure` — Emulator connectivity, singleton client, partition key presence,
  throughput provisioning, RU diagnostics (10/14) ✅

### Tests Failed ❌

**Root cause of 500 errors on reads**: `ObjectRepository.findById/findByCustomerId/findByStatus/findByDateRange`
all call `objectMapper.treeToValue(item, Order.class)` on the raw Cosmos JSON node (which includes
`_rid`, `_self`, `_etag`, `_ts`, `_attachments` system fields). The default Jackson `ObjectMapper`
throws `UnrecognizedPropertyException` for these unknown fields, wrapped as
`RuntimeException("Failed to deserialize order")`, which propagates as HTTP 500 from every endpoint
that reads from Cosmos.

| Test Class | Failure Pattern | Count |
|------------|----------------|-------|
| `TestGetOrder` | `GET /api/orders/{id}` → 500 "Failed to deserialize order" | 3 |
| `TestCustomerOrders` | `GET /api/customers/{id}/orders` → 500 | 4 |
| `TestQueryByStatus` | `GET /api/orders?status=pending` → 500 | 4 |
| `TestQueryByDateRange` | `GET /api/orders?startDate=...` → 500 | 4 |
| `TestUpdateOrderStatus` | `PATCH /api/orders/{id}/status` → 500 (findById fails first) | 4 |
| `TestCustomerSummary` | `GET /api/customers/{id}/orders/summary` → 500 | 5 |
| `TestDeleteOrder` | `DELETE /api/orders/{id}` → 500 (findById fails first) | 3 |
| `TestIndexingPolicies` | No composite indexes defined | 1 |
| `TestDocumentStructure` | No `type` discriminator field | 1 |
| `TestDocumentStructure` | No `schemaVersion` field | 1 |
| `TestEnumSerialization` | Status update fails due to deserialization bug | 1 |
| `TestInvalidInput` | Empty items array → 201 instead of 4xx | 1 |
| `TestInvalidInput` | Invalid status value → 500 instead of 4xx | 2 |
| `TestWriteReadConsistency` | GET after create → 500 (deserialization bug) | 3 |
| `TestStatusTransitionRules` | Status transitions fail because findById → 500 | 6 |
| `TestCustomerSummaryConsistency` | Summary endpoints → 500 | 2 |

### Bugs Found 🐛

1. **Jackson FAIL_ON_UNKNOWN_PROPERTIES bug** (CRITICAL): `ObjectMapper` not configured with
   `FAIL_ON_UNKNOWN_PROPERTIES = false`. All Cosmos reads fail because system properties like `_rid`,
   `_self`, `_etag`, `_ts` are unrecognized by the Order POJO.
   **Fix**: Add `objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)` or
   use `container.queryItems(spec, options, Order.class)` directly (SDK handles system fields gracefully).

2. **Empty items array not validated**: `POST /api/orders` with `{"customerId":"c-1","items":[]}` returns
   201 instead of 400. The controller should validate `items != null && !items.isEmpty()`.

3. **Invalid status value not 4xx**: `PATCH /api/orders/{id}/status` with `{"status":"invalid-value"}`
   returns 500 (from findById deserialization failure) instead of 400. Even after the deserialization
   bug is fixed, `isValidTransition("pending", "invalid-value")` returns `true` because
   `case "pending" -> true` matches any new status. A check against `VALID_STATUSES` is needed
   before `isValidTransition`.

## Gaps Identified

### Critical Gaps (functionality failures — 51 tests fail)

1. **Jackson ObjectMapper not configured for Cosmos documents** — `new ObjectMapper()` fails on
   Cosmos system fields. This single bug causes ~40 of the 51 test failures.
   No existing rule covers this specific scenario (ObjectNode-based deserialization with system fields).

### Best Practice Gaps (suboptimal but would work once serialization is fixed)

2. **No composite index for `(status, createdAt)`** — Cross-partition queries sorting by date
   require a composite index. Test `test_has_composite_indexes_for_order_queries` explicitly checks.

3. **No `type` discriminator field** — Documents should include `"type": "order"` for query
   filtering efficiency when multiple entity types share a container (Rule 1.11).

4. **No `schemaVersion` field** — Documents should include `"schemaVersion": 1` for safe schema
   evolution (Rule 1.10).

5. **`findById` is a cross-partition scan** — No way to do a point read for `GET /api/orders/{orderId}`
   without also knowing `customerId`. Acceptable trade-off here, but Rule 3.1 would have flagged it.
   One solution: include `customerId` in the GET URL, or accept cross-partition as a deliberate design
   choice with an explanatory comment.

6. **`SELECT *` throughout** — All queries return full documents. Rule 3.7 recommends projecting
   only the fields the endpoint actually returns.

7. **No ETag optimistic concurrency on status update** — `PATCH .../status` does read-then-write
   without ETags, risking lost updates under concurrency (Rule 4.7).

8. **No `contentResponseOnWriteEnabled`** — `createItem` returns no body by default in Java SDK.
   The code returns the local `Order` object (which is correct here since no server-side fields are
   added), but in more complex scenarios this could return stale data (Rule 4.9).

### Knowledge Gaps (agent didn't know to apply)

9. **Cosmos DB document system fields** — Agent was unaware that Cosmos documents contain `_rid`,
   `_self`, `_etag`, `_ts`, `_attachments` system fields that a raw Jackson ObjectMapper will reject.
   SDK-native deserialization (`queryItems(..., Order.class)`) handles these transparently.

10. **Items-array validation** — Agent didn't validate that the `items` array is non-empty on create.

## Rules That Would Have Helped

### Rules That Would Have Prevented the Primary Failure

| Gap | Rule File | What It Would Have Changed |
|-----|-----------|---------------------------|
| Jackson FAIL_ON_UNKNOWN_PROPERTIES on Cosmos system fields | `model-json-serialization.md` (Rule 1.5) | Rule 1.5 covers Jackson serialization but does NOT specifically address Cosmos system fields (`_rid`, `_ts`, etc.) or the need to configure `FAIL_ON_UNKNOWN_PROPERTIES=false`. **This is a gap in the existing rule.** A note about using SDK-native deserialization (or configuring FAIL_ON_UNKNOWN_PROPERTIES=false when using raw ObjectMapper) would prevent this failure. |

### Rules That Exist and Would Have Directly Helped

| Gap | Rule File | What It Would Have Changed |
|-----|-----------|---------------------------|
| No composite indexes | `index-use-composite.md` (Rule 5.2) | Would have added `IndexingPolicy` with composite index on `(status ASC, createdAt ASC)` and `(customerId ASC, createdAt DESC)` to container creation. Fixes 1 infrastructure test + enables efficient query sorting. |
| No composite index directions | `index-composite-direction.md` (Rule 5.1) | Reinforces Rule 5.2 with direction-matching requirement. |
| No type discriminator | `model-type-discriminator.md` (Rule 1.11) | Would have added `"type": "order"` field to Order model. Fixes 1 infrastructure test. |
| No schema version | `model-schema-versioning.md` (Rule 1.10) | Would have added `"schemaVersion": 1` field. Fixes 1 infrastructure test. |
| SELECT * queries | `query-project-fields.md` (Rule 3.7) | Would have replaced `SELECT * FROM c` with specific field projections, reducing RU cost. |
| No ETag on status update | `sdk-etag-concurrency.md` (Rule 4.7) | Would have added `_etag` field and `IfMatchEtagOption` to the PATCH handler. |
| No contentResponseOnWriteEnabled | `sdk-content-response-java.md` (Rule 4.9) | Would have added `.contentResponseOnWriteEnabled(true)` to CosmosClientBuilder. |
| Cross-partition findById | `query-avoid-cross-partition.md` (Rule 3.1) | Would have flagged the cross-partition query for `findById` and prompted a design decision (add customerId to URL, or accept cross-partition with comment). |
| Direct mode not used | `sdk-connection-mode.md` (Rule 4.4) | Would have noted the production preference for Direct mode; acceptable to override for emulator. |

### Skill Gap Identified (Rule Does Not Yet Exist)

| Gap | Recommended New Rule | Description |
|-----|----------------------|-------------|
| Cosmos system fields break raw Jackson deserialization | **`model-cosmos-deserialization-java.md`** | When using raw `ObjectMapper.treeToValue()` on Cosmos query results, documents contain system fields (`_rid`, `_self`, `_etag`, `_ts`, `_attachments`) that Jackson rejects by default. Fix: use `container.queryItems(..., Order.class)` directly (SDK strips system fields), or configure `objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)` / add `@JsonIgnoreProperties(ignoreUnknown = true)` to the POJO. This is the most impactful gap to address — it caused ~40/51 test failures in this control run. |

## Score Summary

### As-Is (control run, no skills)

| Category | Score | Notes |
|----------|-------|-------|
| Data Model | 5/10 | Correct fields and field names. Missing `type`, `schemaVersion`. Primary failure: ObjectMapper deserialization bug with Cosmos system fields. |
| Container Configuration | 4/10 | Correct partition key (`/customerId`). No composite index for status/date queries. Default indexing policy (all paths). |
| Repository Layer | 4/10 | Parameterized queries ✅. Single-partition customer queries ✅. `findById` is a cross-partition scan. All reads fail due to serialization bug. |
| SDK Usage | 5/10 | Singleton client ✅. Gateway mode ✅ (emulator). No ETag. No contentResponseOnWriteEnabled. No diagnostics. |
| Query Patterns | 4/10 | `SELECT *` throughout. Cross-partition status/date queries (no composite index). findByCustomerId is single-partition ✅. |
| Input Validation | 3/10 | Empty items returns 201 (should be 400). Invalid status value causes 500 (should be 400). Status transitions logic has a bug (pending → any is too broad). |
| **Overall** | **4/10** | **39/91 tests passed (42.9%).** Primary failure: Jackson ObjectMapper doesn't handle Cosmos system fields. Secondary: no composite indexes, no type/schema fields. |

### Estimated With Skills (if agent had read AGENTS.md)

| Category | Estimated Score | What Skills Would Fix |
|----------|----------------|----------------------|
| Data Model | 7/10 | `type` discriminator (Rule 1.11) ✅, `schemaVersion` (Rule 1.10) ✅. Serialization bug **not directly covered** by existing rules — partial improvement if agent uses SDK-native deserialization. |
| Container Configuration | 8/10 | Composite index for `(status, createdAt)` (Rule 5.2) ✅. Custom indexing policy (Rule 5.3) ✅. |
| Repository Layer | 7/10 | Cross-partition awareness (Rule 3.1) ✅. Field projections (Rule 3.7) ✅. Deserialization fix depends on whether agent uses SDK-native approach. |
| SDK Usage | 8/10 | ETag (Rule 4.7) ✅. contentResponseOnWriteEnabled (Rule 4.9) ✅. Diagnostics logging (Rule 4.5) ✅. |
| Query Patterns | 7/10 | Projected fields (Rule 3.7) ✅. Composite index enables efficient sort (Rule 5.2) ✅. |
| Input Validation | 5/10 | Not directly covered by existing rules — agent would need built-in knowledge. |
| **Estimated Overall** | **7/10** | **~65-70/91 tests (71-77%).** Skills would fix composite indexes, type/schema fields, ETag, contentResponseOnWrite. The primary serialization failure is NOT covered by existing rules; a new rule is needed. |

## Next Steps

1. **Add new rule** `model-cosmos-deserialization-java.md` to address ObjectMapper FAIL_ON_UNKNOWN_PROPERTIES with Cosmos system fields — this is the highest-impact gap found in this iteration.
2. **Strengthen Rule 1.5** (`model-json-serialization.md`) with a note about Cosmos system fields and the preference for SDK-native deserialization over manual ObjectMapper.treeToValue().
3. **Run iteration-002-java** with skills loaded to validate improvement.
4. Input validation guidance (non-empty items, valid status enum before transition check) could be added to a scenario-specific note or a new validation rule.

## Source Files

Source code archived in `source-code.zip`.

## Build & Startup Signals

- **Build**: PASS (after SSL fix)
- **Startup**: PASS (after SSL fix — trust-all JCA Security Provider)
