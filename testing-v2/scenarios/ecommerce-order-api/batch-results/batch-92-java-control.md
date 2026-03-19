# Batch Test Results: Ecommerce Order Api

## Metadata
- **Date**: 2026-03-19
- **Scenario**: ecommerce-order-api
- **Language**: java
- **Skills loaded**: No (control run)
- **Iterations**: 4
- **Batch issue**: #92
- **Child PRs**: 98,99,100,101,102

## Aggregate Summary

| Metric | Mean | Std Dev | Min | Max | Range |
|--------|------|---------|-----|-----|-------|
| Pass Rate | 78.3% | 39.7% | 18.7% | 98.9% | 80.2% |
| Score (1-10) | 6.8 | 3.9 | 1 | 9 | 8 |

## Per-Iteration Results

| Run | Passed | Total | Pass Rate | Score |
|-----|--------|-------|-----------|-------|
| 1 | 90 | 91 | 98.9% | 9/10 |
| 2 | 90 | 91 | 98.9% | 9/10 |
| 3 | 88 | 91 | 96.7% | 8/10 |
| 4 | 17 | 91 | 18.7% | 1/10 |

## Category Breakdown

| Category | Mean | Std Dev | Min | Max |
|----------|------|---------|-----|-----|
| api_contract | 78.0% | 43.9% | 12.2% | 100.0% |
| cosmos_infrastructure | 75.0% | 32.3% | 26.7% | 93.3% |
| data_integrity | 95.0% | 10.0% | 80.0% | 100.0% |
| robustness | 77.5% | 42.8% | 13.3% | 100.0% |

## Test Consistency Analysis

- **Always pass**: 17 tests (19%)
- **Always fail**: 0 tests (0%)
- **Flaky** (stochastic): 74 tests (81%)

### Consistent Passes (17 tests)

- `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCreateOrder::test_create_order_returns_201`
- `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestDeleteOrder::test_delete_nonexistent_order_returns_404`
- `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestGetOrder::test_get_nonexistent_order_returns_404`
- `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestHealth::test_health_returns_200`
- `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestUpdateOrderStatus::test_update_nonexistent_order_returns_404`
- `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestContainerPartitionKeys::test_no_default_id_partition_key`
- `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestContainerPartitionKeys::test_order_container_uses_customer_partition_key`
- `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestIndexingPolicies::test_has_custom_indexing_policy`
- `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestThroughputConfiguration::test_throughput_is_configured`
- `testing-v2.scenarios.ecommerce-order-api.tests.test_data_integrity.TestDataPersistence::test_database_is_created`
- `testing-v2.scenarios.ecommerce-order-api.tests.test_data_integrity.TestIndexingPolicy::test_containers_have_indexing_policy`
- `testing-v2.scenarios.ecommerce-order-api.tests.test_data_integrity.TestPartitionKeyDesign::test_containers_have_partition_keys`
- `testing-v2.scenarios.ecommerce-order-api.tests.test_data_integrity.TestPartitionKeyDesign::test_no_container_uses_id_as_sole_partition_key`
- `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestInvalidInput::test_create_order_empty_body_returns_4xx`
- `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestInvalidInput::test_create_order_empty_items_returns_4xx`
- `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestInvalidInput::test_create_order_missing_customer_id_returns_4xx`
- `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestInvalidInput::test_create_order_missing_items_returns_4xx`

### Flaky Tests (74 tests)

These tests passed in some iterations but failed in others — indicates LLM stochasticity rather than a systematic gap.

| Test | Pass Rate | Outcomes |
|------|-----------|----------|
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCreateOrder::test_create_order_calculates_total` | 75.0% | passed, passed, passed, failed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCreateOrder::test_create_order_has_timestamp` | 75.0% | passed, passed, passed, failed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCreateOrder::test_create_order_response_has_required_fields` | 75.0% | passed, passed, passed, failed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCreateOrder::test_create_order_returns_items` | 75.0% | passed, passed, passed, failed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCreateOrder::test_new_order_status_is_pending` | 75.0% | passed, passed, passed, failed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerOrders::test_customer_001_has_2_orders` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerOrders::test_customer_orders_entries_have_required_fields` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerOrders::test_customer_orders_only_contains_own_orders` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerOrders::test_customer_orders_returns_200` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerOrders::test_customer_orders_returns_array` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerOrders::test_empty_customer_returns_empty_array` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerSummary::test_customer_001_summary_correct` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerSummary::test_customer_003_summary_correct` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerSummary::test_customer_summary_average_is_correct` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerSummary::test_customer_summary_has_required_fields` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerSummary::test_customer_summary_returns_200` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerSummary::test_nonexistent_customer_summary_empty` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestDeleteOrder::test_delete_pending_order_returns_204` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestDeleteOrder::test_delete_shipped_order_returns_409` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestDeleteOrder::test_deleted_order_returns_404_on_get` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestGetOrder::test_get_existing_order` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestGetOrder::test_get_order_has_required_fields` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestGetOrder::test_get_order_returns_correct_data` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByDateRange::test_past_date_range_returns_empty` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByDateRange::test_query_by_date_range_returns_200` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByDateRange::test_query_by_date_range_returns_array` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByDateRange::test_wide_date_range_includes_seeded_orders` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByStatus::test_all_seeded_orders_are_pending` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByStatus::test_query_by_status_filters_correctly` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByStatus::test_query_by_status_returns_200` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByStatus::test_query_by_status_returns_array` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByStatus::test_query_shipped_initially_empty` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestUpdateOrderStatus::test_update_status_reflects_new_status` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestUpdateOrderStatus::test_update_status_response_has_required_fields` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestUpdateOrderStatus::test_update_status_returns_200` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestUpdateOrderStatus::test_updated_status_persists_on_get` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestCrossBoundaryConsistency::test_customer_id_stored_correctly` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestCrossBoundaryConsistency::test_status_round_trip_through_cosmos` | 0.0% | skipped, skipped, skipped, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestCrossBoundaryConsistency::test_stored_item_count_matches_api` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestCrossBoundaryConsistency::test_stored_total_matches_api_total` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestCrossBoundaryConsistency::test_timestamps_stored_as_iso_strings` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestDocumentStructure::test_documents_have_schema_version` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestDocumentStructure::test_documents_have_type_discriminator` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestDocumentStructure::test_orders_have_embedded_items` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestEnumSerialization::test_status_query_returns_correct_results` | 75.0% | passed, passed, passed, failed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestEnumSerialization::test_status_stored_as_string` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestIndexingPolicies::test_has_composite_indexes_for_order_queries` | 50.0% | passed, passed, failed, failed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_data_integrity.TestDataPersistence::test_containers_exist_in_cosmos` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestComputedFieldAccuracy::test_multi_item_total` | 75.0% | passed, passed, passed, failed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestComputedFieldAccuracy::test_multi_quantity_total` | 75.0% | passed, passed, passed, failed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestComputedFieldAccuracy::test_seeded_order_totals_correct` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestComputedFieldAccuracy::test_single_item_total` | 75.0% | passed, passed, passed, failed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestCustomerSummaryConsistency::test_summary_reflects_deleted_order` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestCustomerSummaryConsistency::test_summary_updates_after_new_order` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestDataIsolation::test_customer_orders_isolated` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestDataIsolation::test_different_customers_see_different_orders` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestDataTypeCorrectness::test_order_field_types` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestDataTypeCorrectness::test_order_item_field_types` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestEdgeCases::test_get_order_preserves_all_items` | 75.0% | passed, passed, passed, failed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestEdgeCases::test_large_quantity_order` | 75.0% | passed, passed, passed, failed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestEdgeCases::test_order_with_many_items` | 75.0% | passed, passed, passed, failed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestInvalidInput::test_update_status_empty_body_returns_4xx` | 50.0% | passed, passed, failed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestInvalidInput::test_update_status_invalid_value_returns_4xx` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitionRules::test_cancelled_to_anything_rejected` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitionRules::test_delivered_to_anything_rejected` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitionRules::test_pending_to_cancelled_allowed` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitionRules::test_pending_to_shipped_allowed` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitionRules::test_shipped_to_delivered_allowed` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitionRules::test_shipped_to_pending_rejected` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitions::test_updated_order_no_longer_in_original_status` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitions::test_updated_status_appears_in_status_query` | 75.0% | passed, passed, passed, error |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestWriteReadConsistency::test_created_order_fields_match_on_get` | 75.0% | passed, passed, passed, failed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestWriteReadConsistency::test_order_appears_in_customer_history` | 75.0% | passed, passed, passed, failed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestWriteReadConsistency::test_order_appears_in_status_query` | 75.0% | passed, passed, passed, failed |

## Statistical Assessment

**Insufficient confidence** (σ ≥ 15%): Very high variance — results are dominated by LLM stochasticity. More iterations or scenario simplification needed.
