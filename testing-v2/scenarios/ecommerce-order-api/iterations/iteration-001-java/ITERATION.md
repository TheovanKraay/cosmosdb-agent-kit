# iteration-001-java - Java Ecommerce Order Api

## Metadata
- **Date**: 2026-03-17
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

**Pass rate: 42.9%** (39/91 tests passed (42.9%))

| Status | Count |
|--------|-------|
| Passed | 39 |
| Failed | 51 |
| Errors | 0 |
| Skipped | 1 |

### Failures

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestGetOrder::test_get_existing_order**
  > AssertionError: GET /api/orders/bf44e08c-d9b8-41b4-a1ec-ce054857f162 should return 200, got 500
assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestGetOrder::test_get_order_has_required_fields**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestGetOrder::test_get_order_returns_correct_data**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerOrders::test_customer_orders_returns_200**
  > AssertionError: GET /api/customers/customer-001/orders should return 200, got 500
assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerOrders::test_customer_orders_returns_array**
  > AssertionError: Customer orders should return an array, got dict
assert False
 +  where False = isinstance({'error': 'Internal Server Error', 'path': '/api/customers/customer-001/orders', 'status': 50

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerOrders::test_customer_orders_only_contains_own_orders**
  > TypeError: string indices must be integers, not 'str'

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerOrders::test_customer_orders_entries_have_required_fields**
  > KeyError: 0

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByStatus::test_query_by_status_returns_200**
  > AssertionError: GET /api/orders?status=pending should return 200, got 500
assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByStatus::test_query_by_status_returns_array**
  > AssertionError: Status query should return an array, got dict
assert False
 +  where False = isinstance({'error': 'Failed to deserialize order'}, list)

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByStatus::test_all_seeded_orders_are_pending**
  > AssertionError: Expected at least 5 pending orders (seeded data), got 1
assert 1 >= 5
 +  where 1 = len({'error': 'Failed to deserialize order'})

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByStatus::test_query_by_status_filters_correctly**
  > TypeError: string indices must be integers, not 'str'

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByDateRange::test_query_by_date_range_returns_200**
  > AssertionError: GET /api/orders?startDate=...&endDate=... should return 200, got 500
assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByDateRange::test_query_by_date_range_returns_array**
  > AssertionError: Date range query should return an array, got dict
assert False
 +  where False = isinstance({'error': 'Failed to deserialize order'}, list)

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByDateRange::test_wide_date_range_includes_seeded_orders**
  > AssertionError: Wide date range should include at least 5 seeded orders, got 1
assert 1 >= 5
 +  where 1 = len({'error': 'Failed to deserialize order'})

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestUpdateOrderStatus::test_update_status_returns_200**
  > AssertionError: PATCH /api/orders/bf44e08c-d9b8-41b4-a1ec-ce054857f162/status should return 200, got 500. Response: {"error":"Failed to deserialize order"}
assert 500 == 200
 +  where 500 = <Response 

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestUpdateOrderStatus::test_update_status_response_has_required_fields**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestUpdateOrderStatus::test_update_status_reflects_new_status**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestUpdateOrderStatus::test_updated_status_persists_on_get**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerSummary::test_customer_summary_returns_200**
  > AssertionError: GET /api/customers/customer-001/orders/summary should return 200, got 500. Response: {"timestamp":"2026-03-17T10:59:09.048+00:00","status":500,"error":"Internal Server Error","path":"/

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

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestDeleteOrder::test_delete_pending_order_returns_204**
  > AssertionError: DELETE pending order should return 204, got 500
assert 500 == 204
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestDeleteOrder::test_deleted_order_returns_404_on_get**
  > AssertionError: Deleted order should return 404 on GET, got 500
assert 500 == 404
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestDeleteOrder::test_delete_shipped_order_returns_409**
  > AssertionError: DELETE shipped order should return 409 Conflict, got 500. Only pending orders can be deleted.
assert 500 == 409
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestIndexingPolicies::test_has_composite_indexes_for_order_queries**
  > AssertionError: No container has composite indexes defined. E-commerce queries like 'orders by status sorted by date' need composite indexes on (status, createdAt) to avoid expensive sorts. Without th

- **testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestEnumSerialization::test_status_query_returns_correct_results**
  > AssertionError: Status update failed: 500 {"error":"Failed to deserialize order"}
assert 500 in (200, 204)
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestDocumentStructure::test_documents_have_type_discriminator**
  > Failed: No documents have a type discriminator field. When a container holds multiple entity types (or for future extensibility), include a 'type' field (e.g., 'order', 'customer') to distinguish them

- **testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestDocumentStructure::test_documents_have_schema_version**
  > Failed: No documents have a schema version field. Include a 'schemaVersion' field in documents so future schema changes can be handled without rewriting all existing data. (Rule: model-schema-versioni

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestInvalidInput::test_create_order_empty_items_returns_4xx**
  > AssertionError: Empty items array should return 4xx, got 201. An order must have at least one item.
assert 400 <= 201
 +  where 201 = <Response [201]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestInvalidInput::test_update_status_invalid_value_returns_4xx**
  > AssertionError: Invalid status value caused a server error (500). The app should validate status values or handle them gracefully.
assert 500 < 500
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestInvalidInput::test_update_status_empty_body_returns_4xx**
  > AssertionError: Empty status update body caused a server error (500). Server must handle missing fields gracefully.
assert 500 < 500
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestDataTypeCorrectness::test_order_field_types**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestDataTypeCorrectness::test_order_item_field_types**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestWriteReadConsistency::test_created_order_fields_match_on_get**
  > AssertionError: Order was created successfully but GET returned 500. Data may not be persisted correctly.
assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestWriteReadConsistency::test_order_appears_in_customer_history**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestWriteReadConsistency::test_order_appears_in_status_query**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitions::test_updated_status_appears_in_status_query**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitions::test_updated_order_no_longer_in_original_status**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestDataIsolation::test_customer_orders_isolated**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestDataIsolation::test_different_customers_see_different_orders**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestEdgeCases::test_get_order_preserves_all_items**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitionRules::test_pending_to_shipped_allowed**
  > AssertionError: pending → shipped should be allowed, got 500
assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitionRules::test_pending_to_cancelled_allowed**
  > AssertionError: pending → cancelled should be allowed, got 500
assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitionRules::test_shipped_to_delivered_allowed**
  > AssertionError: shipped → delivered should be allowed, got 500
assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitionRules::test_shipped_to_pending_rejected**
  > AssertionError: shipped → pending should return 409 Conflict, got 500. Orders cannot be un-shipped.
assert 500 == 409
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitionRules::test_delivered_to_anything_rejected**
  > AssertionError: delivered → pending should return 409 Conflict, got 500. Delivered is a terminal state — no further transitions allowed.
assert 500 == 409
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitionRules::test_cancelled_to_anything_rejected**
  > AssertionError: cancelled → pending should return 409 Conflict, got 500. Cancelled is a terminal state — no further transitions allowed.
assert 500 == 409
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestCustomerSummaryConsistency::test_summary_updates_after_new_order**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestCustomerSummaryConsistency::test_summary_reflects_deleted_order**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

## Source Files

Source code archived in `source-code.zip` (27 files).

## Build & Startup Signals

- **Build**: PASS
- **Startup**: PASS

## Results by Category

| Category | Passed | Failed | Skipped |
|----------|--------|--------|---------|
| api_contract | 15 | 26 | 0 |
| cosmos_infrastructure | 10 | 4 | 1 |
| data_integrity | 5 | 0 | 0 |
| robustness | 9 | 21 | 0 |

## Score Summary

| Category | Score | Notes |
|----------|-------|-------|
| API Conformance | 2/10 | 42.9% pass rate; 4 infrastructure failures |
| **Overall** | **2/10** | **39/91 tests passed (42.9%)** |
