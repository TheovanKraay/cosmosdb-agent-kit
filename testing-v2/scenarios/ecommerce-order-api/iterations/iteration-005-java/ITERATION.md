# iteration-005-java - Java Ecommerce Order Api

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
| Custom indexing policy | Detected | `index-exclude-unused` |
| Throughput configuration | Not detected | `throughput-provision-rus` |
| Change feed usage | Not detected | `pattern-change-feed` |
| Diagnostics/logging | Not detected | `sdk-diagnostics` |

## Test Results

**Pass rate: 18.7%** (17/91 tests passed (18.7%))

| Status | Count |
|--------|-------|
| Passed | 17 |
| Failed | 16 |
| Errors | 58 |
| Skipped | 0 |

### Failures

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCreateOrder::test_create_order_response_has_required_fields**
  > requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCreateOrder::test_new_order_status_is_pending**
  > requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCreateOrder::test_create_order_calculates_total**
  > requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCreateOrder::test_create_order_has_timestamp**
  > requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCreateOrder::test_create_order_returns_items**
  > requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestGetOrder::test_get_existing_order**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestGetOrder::test_get_order_has_required_fields**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestGetOrder::test_get_order_returns_correct_data**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerOrders::test_customer_orders_returns_200**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerOrders::test_customer_orders_returns_array**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerOrders::test_customer_001_has_2_orders**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerOrders::test_customer_orders_only_contains_own_orders**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerOrders::test_customer_orders_entries_have_required_fields**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerOrders::test_empty_customer_returns_empty_array**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByStatus::test_query_by_status_returns_200**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByStatus::test_query_by_status_returns_array**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByStatus::test_all_seeded_orders_are_pending**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByStatus::test_query_by_status_filters_correctly**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByStatus::test_query_shipped_initially_empty**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByDateRange::test_query_by_date_range_returns_200**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByDateRange::test_query_by_date_range_returns_array**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByDateRange::test_wide_date_range_includes_seeded_orders**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByDateRange::test_past_date_range_returns_empty**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestUpdateOrderStatus::test_update_status_returns_200**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestUpdateOrderStatus::test_update_status_response_has_required_fields**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestUpdateOrderStatus::test_update_status_reflects_new_status**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestUpdateOrderStatus::test_updated_status_persists_on_get**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerSummary::test_customer_summary_returns_200**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerSummary::test_customer_summary_has_required_fields**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerSummary::test_customer_001_summary_correct**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerSummary::test_customer_003_summary_correct**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerSummary::test_customer_summary_average_is_correct**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerSummary::test_nonexistent_customer_summary_empty**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestDeleteOrder::test_delete_pending_order_returns_204**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestDeleteOrder::test_deleted_order_returns_404_on_get**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestDeleteOrder::test_delete_shipped_order_returns_409**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestIndexingPolicies::test_has_composite_indexes_for_order_queries**
  > AssertionError: No container has composite indexes defined. E-commerce queries like 'orders by status sorted by date' need composite indexes on (status, createdAt) to avoid expensive sorts. Without th

- **testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestEnumSerialization::test_status_stored_as_string**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestEnumSerialization::test_status_query_returns_correct_results**
  > requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)

- **testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestDocumentStructure::test_orders_have_embedded_items**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestDocumentStructure::test_documents_have_type_discriminator**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestDocumentStructure::test_documents_have_schema_version**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestCrossBoundaryConsistency::test_stored_total_matches_api_total**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestCrossBoundaryConsistency::test_stored_item_count_matches_api**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestCrossBoundaryConsistency::test_customer_id_stored_correctly**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestCrossBoundaryConsistency::test_timestamps_stored_as_iso_strings**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestCrossBoundaryConsistency::test_status_round_trip_through_cosmos**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_data_integrity.TestDataPersistence::test_containers_exist_in_cosmos**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestInvalidInput::test_update_status_invalid_value_returns_4xx**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestInvalidInput::test_update_status_empty_body_returns_4xx**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestComputedFieldAccuracy::test_single_item_total**
  > requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestComputedFieldAccuracy::test_multi_quantity_total**
  > requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestComputedFieldAccuracy::test_multi_item_total**
  > requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestComputedFieldAccuracy::test_seeded_order_totals_correct**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestDataTypeCorrectness::test_order_field_types**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestDataTypeCorrectness::test_order_item_field_types**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestWriteReadConsistency::test_created_order_fields_match_on_get**
  > requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestWriteReadConsistency::test_order_appears_in_customer_history**
  > requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestWriteReadConsistency::test_order_appears_in_status_query**
  > requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitions::test_updated_status_appears_in_status_query**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitions::test_updated_order_no_longer_in_original_status**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestDataIsolation::test_customer_orders_isolated**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestDataIsolation::test_different_customers_see_different_orders**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestEdgeCases::test_large_quantity_order**
  > requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestEdgeCases::test_order_with_many_items**
  > requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestEdgeCases::test_get_order_preserves_all_items**
  > requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitionRules::test_pending_to_shipped_allowed**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitionRules::test_pending_to_cancelled_allowed**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitionRules::test_shipped_to_delivered_allowed**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitionRules::test_shipped_to_pending_rejected**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitionRules::test_delivered_to_anything_rejected**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitionRules::test_cancelled_to_anything_rejected**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestCustomerSummaryConsistency::test_summary_updates_after_new_order**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

- **testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestCustomerSummaryConsistency::test_summary_reflects_deleted_order**
  > failed on setup with "requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)"

## Source Files

Source code archived in `source-code.zip` (34 files).

## Build & Startup Signals

- **Build**: PASS
- **Startup**: PASS

## Results by Category

| Category | Passed | Failed | Skipped |
|----------|--------|--------|---------|
| api_contract | 5 | 36 | 0 |
| cosmos_infrastructure | 4 | 11 | 0 |
| data_integrity | 4 | 1 | 0 |
| robustness | 4 | 26 | 0 |

## Score Summary

| Category | Score | Notes |
|----------|-------|-------|
| API Conformance | 1/10 | 18.7% pass rate; 11 infrastructure failures |
| **Overall** | **1/10** | **17/91 tests passed (18.7%)** |
