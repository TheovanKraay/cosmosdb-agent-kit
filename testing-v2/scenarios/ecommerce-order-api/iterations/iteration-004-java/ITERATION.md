# iteration-004-java - Java Ecommerce Order Api

## Metadata
- **Date**: 2026-03-21
- **Language/SDK**: Java
- **Agent**: GitHub Copilot (automated iteration)
- **Tester**: Automated CI
- **Run Type**: Normal run (skills loaded)

## Skills Verification

**Were skills loaded before building?** Yes (via issue prompt referencing AGENTS.md)

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
| Cross-partition queries | Detected | `query-avoid-cross-partition` |
| Custom indexing policy | Detected | `index-exclude-unused` |
| Throughput configuration | Detected | `throughput-provision-rus` |
| Change feed usage | Not detected | `pattern-change-feed` |
| Diagnostics/logging | Not detected | `sdk-diagnostics` |

## Test Results

**Pass rate: 95.6%** (87/91 tests passed (95.6%))

| Status | Count |
|--------|-------|
| Passed | 87 |
| Failed | 3 |
| Errors | 0 |
| Skipped | 1 |

### Failures

- **testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestIndexingPolicies::test_has_composite_indexes_for_order_queries**
  > AssertionError: No container has composite indexes defined. E-commerce queries like 'orders by status sorted by date' need composite indexes on (status, createdAt) to avoid expensive sorts. Without th

- **testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestDocumentStructure::test_documents_have_type_discriminator**
  > Failed: No documents have a type discriminator field. When a container holds multiple entity types (or for future extensibility), include a 'type' field (e.g., 'order', 'customer') to distinguish them

- **testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestDocumentStructure::test_documents_have_schema_version**
  > Failed: No documents have a schema version field. Include a 'schemaVersion' field in documents so future schema changes can be handled without rewriting all existing data. (Rule: model-schema-versioni

## Source Files

Source code archived in `source-code.zip` (30 files).

## Build & Startup Signals

- **Build**: PASS
- **Startup**: PASS

## Results by Category

| Category | Passed | Failed | Skipped |
|----------|--------|--------|---------|
| api_contract | 41 | 0 | 0 |
| build_startup | 2 | 0 | 0 |
| cosmos_infrastructure | 11 | 3 | 1 |
| data_integrity | 5 | 0 | 0 |
| robustness | 30 | 0 | 0 |

## Score Summary

| Category | Score | Notes |
|----------|-------|-------|
| API Conformance | 8/10 | 95.6% pass rate; 3 infrastructure failures |
| **Overall** | **8/10** | **87/91 tests passed (95.6%)** |
