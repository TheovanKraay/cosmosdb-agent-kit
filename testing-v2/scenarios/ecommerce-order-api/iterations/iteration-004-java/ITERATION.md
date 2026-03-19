# iteration-004-java - Java Ecommerce Order Api

## Metadata
- **Date**: 2026-03-19
- **Language/SDK**: Java
- **Agent**: GitHub Copilot (automated iteration)
- **Tester**: Automated CI
- **Run Type**: Control run (NO skills loaded)

## Skills Verification

**Were skills loaded before building?** No (CONTROL RUN)

> This is a control run. The agent generated code using only its built-in
> knowledge, without reading the Cosmos DB best practices skills.
> The evaluation below identifies which existing rules would have helped.

## Cosmos DB Patterns Detected

| Pattern | Status | Related Rule |
|---------|--------|--------------|
| Singleton CosmosClient | Detected | `sdk-singleton-client` |
| Direct connection mode | Not detected | `sdk-connection-mode` |
| Gateway connection mode | Detected | `sdk-connection-mode` |
| Partition key configured | Detected | `partition-high-cardinality` |
| Bulk operations | Not detected | `sdk-bulk-operations` |
| ETag optimistic concurrency | Not detected | `sdk-etag-concurrency` |
| Point reads (by ID + partition key) | Not detected | `query-avoid-scans` |
| Cross-partition queries | Not detected | `query-avoid-cross-partition` |
| Custom indexing policy | Not detected | `index-exclude-unused` |
| Throughput configuration | Detected | `throughput-provision-rus` |
| Change feed usage | Not detected | `pattern-change-feed` |
| Diagnostics/logging | Not detected | `sdk-diagnostics` |

## Test Results

**Pass rate: 92.3%** (84/91 tests passed (92.3%))

| Status | Count |
|--------|-------|
| Passed | 84 |
| Failed | 6 |
| Errors | 0 |
| Skipped | 1 |

### Failures

- **testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestIndexingPolicies::test_has_composite_indexes_for_order_queries**
  > AssertionError: No container has composite indexes defined. E-commerce queries like 'orders by status sorted by date' need composite indexes on (status, createdAt) to avoid expensive sorts. Without th

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestInvalidInput::test_create_order_missing_customer_id_returns_4xx**
  > AssertionError: Missing customerId should return 4xx, got 201. The app must validate required fields and return 400.
assert 400 <= 201
 +  where 201 = <Response [201]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestInvalidInput::test_create_order_missing_items_returns_4xx**
  > AssertionError: Missing items should return 4xx, got 500. The app must validate required fields.
assert 500 < 500
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestInvalidInput::test_create_order_empty_items_returns_4xx**
  > AssertionError: Empty items array should return 4xx, got 201. An order must have at least one item.
assert 400 <= 201
 +  where 201 = <Response [201]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestInvalidInput::test_create_order_empty_body_returns_4xx**
  > AssertionError: Empty body should return 4xx, got 500. Server must not crash (500) on missing fields.
assert 500 < 500
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestInvalidInput::test_update_status_empty_body_returns_4xx**
  > AssertionError: Empty status update body caused a server error (500). Server must handle missing fields gracefully.
assert 500 < 500
 +  where 500 = <Response [500]>.status_code

## Source Files

Source code archived in `source-code.zip` (33 files).

## Build & Startup Signals

- **Build**: PASS
- **Startup**: PASS

## Results by Category

| Category | Passed | Failed | Skipped |
|----------|--------|--------|---------|
| api_contract | 41 | 0 | 0 |
| cosmos_infrastructure | 13 | 1 | 1 |
| data_integrity | 5 | 0 | 0 |
| robustness | 25 | 5 | 0 |

## Score Summary

| Category | Score | Notes |
|----------|-------|-------|
| API Conformance | 8/10 | 92.3% pass rate; 1 infrastructure failures |
| **Overall** | **8/10** | **84/91 tests passed (92.3%)** |
