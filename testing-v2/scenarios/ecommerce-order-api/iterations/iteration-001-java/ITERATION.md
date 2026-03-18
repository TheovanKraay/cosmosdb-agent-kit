# iteration-001-java - Java Ecommerce Order Api

## Metadata
- **Date**: 2026-03-18
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
| Custom indexing policy | Detected | `index-exclude-unused` |
| Throughput configuration | Detected | `throughput-provision-rus` |
| Change feed usage | Not detected | `pattern-change-feed` |
| Diagnostics/logging | Not detected | `sdk-diagnostics` |

## Test Results

**Pass rate: 96.7%** (88/91 tests passed (96.7%))

| Status | Count |
|--------|-------|
| Passed | 88 |
| Failed | 2 |
| Errors | 0 |
| Skipped | 1 |

### Failures

- **testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestIndexingPolicies::test_has_composite_indexes_for_order_queries**
  > AssertionError: No container has composite indexes defined. E-commerce queries like 'orders by status sorted by date' need composite indexes on (status, createdAt) to avoid expensive sorts. Without th

- **testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestEnumSerialization::test_status_query_returns_correct_results**
  > AssertionError: Status update failed: 409 {"error":"Invalid status transition from shipped to shipped"}
assert 409 in (200, 204)
 +  where 409 = <Response [409]>.status_code

## Source Files

Source code archived in `source-code.zip` (35 files).

## Build & Startup Signals

- **Build**: PASS
- **Startup**: PASS

## Results by Category

| Category | Passed | Failed | Skipped |
|----------|--------|--------|---------|
| api_contract | 41 | 0 | 0 |
| cosmos_infrastructure | 12 | 2 | 1 |
| data_integrity | 5 | 0 | 0 |
| robustness | 30 | 0 | 0 |

## Score Summary

| Category | Score | Notes |
|----------|-------|-------|
| API Conformance | 8/10 | 96.7% pass rate; 2 infrastructure failures |
| **Overall** | **8/10** | **88/91 tests passed (96.7%)** |
