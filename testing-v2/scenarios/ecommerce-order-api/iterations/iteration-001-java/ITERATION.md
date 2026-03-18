# iteration-001-java - Java Ecommerce Order Api

## Metadata
- **Date**: 2026-03-18
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
| Cross-partition queries | Not detected | `query-avoid-cross-partition` |
| Custom indexing policy | Detected | `index-exclude-unused` |
| Throughput configuration | Detected | `throughput-provision-rus` |
| Change feed usage | Not detected | `pattern-change-feed` |
| Diagnostics/logging | Not detected | `sdk-diagnostics` |

## Test Results

**Pass rate: 89.0%** (81/91 tests passed (89.0%))

| Status | Count |
|--------|-------|
| Passed | 81 |
| Failed | 9 |
| Errors | 0 |
| Skipped | 1 |

### Failures

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerSummary::test_customer_summary_returns_200**
  > AssertionError: GET /api/customers/customer-001/orders/summary should return 200, got 500. Response: {"timestamp":"2026-03-18T13:54:51.459+00:00","status":500,"error":"Internal Server Error","path":"/

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerSummary::test_customer_summary_has_required_fields**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerSummary::test_customer_001_summary_correct**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerSummary::test_customer_003_summary_correct**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerSummary::test_customer_summary_average_is_correct**
  > KeyError: 'totalSpent'

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerSummary::test_nonexistent_customer_summary_empty**
  > AssertionError: Summary for nonexistent customer should return 200 (with zeros) or 404, got 500
assert 500 in (200, 404)
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestEnumSerialization::test_status_query_returns_correct_results**
  > AssertionError: Status update failed: 409 {"error":"Invalid status transition from shipped to shipped"}
assert 409 in (200, 204)
 +  where 409 = <Response [409]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestCustomerSummaryConsistency::test_summary_updates_after_new_order**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestCustomerSummaryConsistency::test_summary_reflects_deleted_order**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

## Source Files

Source code archived in `source-code.zip` (38 files).

## Build & Startup Signals

- **Build**: PASS
- **Startup**: PASS

## Results by Category

| Category | Passed | Failed | Skipped |
|----------|--------|--------|---------|
| api_contract | 35 | 6 | 0 |
| cosmos_infrastructure | 13 | 1 | 1 |
| data_integrity | 5 | 0 | 0 |
| robustness | 28 | 2 | 0 |

## Score Summary

| Category | Score | Notes |
|----------|-------|-------|
| API Conformance | 7/10 | 89.0% pass rate; 1 infrastructure failures |
| **Overall** | **7/10** | **81/91 tests passed (89.0%)** |
