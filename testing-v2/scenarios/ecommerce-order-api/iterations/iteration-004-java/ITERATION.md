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

**Pass rate: 97.8%** (89/91 tests passed (97.8%))

| Status | Count |
|--------|-------|
| Passed | 89 |
| Failed | 1 |
| Errors | 0 |
| Skipped | 1 |

### Failures

- **testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestIndexingPolicies::test_has_composite_indexes_for_order_queries**
  > AssertionError: No container has composite indexes defined. E-commerce queries like 'orders by status sorted by date' need composite indexes on (status, createdAt) to avoid expensive sorts. Without th

## Source Files

Source code archived in `source-code.zip` (30 files).

## Build & Startup Signals

- **Build**: PASS
- **Startup**: PASS

## Results by Category

| Category | Passed | Failed | Skipped |
|----------|--------|--------|---------|
| api_contract | 41 | 0 | 0 |
| cosmos_infrastructure | 13 | 1 | 1 |
| data_integrity | 5 | 0 | 0 |
| robustness | 30 | 0 | 0 |

## Score Summary

| Category | Score | Notes |
|----------|-------|-------|
| API Conformance | 8/10 | 97.8% pass rate; 1 infrastructure failures |
| **Overall** | **8/10** | **89/91 tests passed (97.8%)** |
