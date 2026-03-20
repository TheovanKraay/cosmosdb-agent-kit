# Batch Test Results: Ecommerce Order Api

## Metadata
- **Date**: 2026-03-20
- **Scenario**: ecommerce-order-api
- **Language**: java
- **Skills loaded**: No (control run)
- **Iterations**: 5
- **Batch issue**: #103
- **Child PRs**: 109,110,112,111,113

## Aggregate Summary

| Metric | Mean | Std Dev | Min | Max | Range |
|--------|------|---------|-----|-----|-------|
| Pass Rate | 67.3% | 43.3% | 19.8% | 98.9% | 79.10000000000001% |
| Score (1-10) | 5.8 | 4.4 | 1 | 9 | 8 |

## Per-Iteration Results

| Run | Passed | Total | Pass Rate | Score |
|-----|--------|-------|-----------|-------|
| 1 | 90 | 91 | 98.9% | 9/10 |
| 2 | 18 | 91 | 19.8% | 1/10 |
| 3 | 18 | 91 | 19.8% | 1/10 |
| 4 | 90 | 91 | 98.9% | 9/10 |
| 5 | 90 | 91 | 98.9% | 9/10 |

## Category Breakdown

| Category | Mean | Std Dev | Min | Max |
|----------|------|---------|-----|-----|
| api_contract | 64.9% | 48.1% | 12.2% | 100.0% |
| cosmos_infrastructure | 69.3% | 32.9% | 33.3% | 93.3% |
| data_integrity | 92.0% | 11.0% | 80.0% | 100.0% |
| robustness | 65.3% | 47.5% | 13.3% | 100.0% |

## Test Consistency Analysis

- **Always pass**: 18 tests (20%)
- **Always fail**: 0 tests (0%)
- **Flaky** (stochastic): 73 tests (80%)

### Consistent Passes (18 tests)

- `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCreateOrder::test_create_order_returns_201`
- `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestDeleteOrder::test_delete_nonexistent_order_returns_404`
- `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestGetOrder::test_get_nonexistent_order_returns_404`
- `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestHealth::test_health_returns_200`
- `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestUpdateOrderStatus::test_update_nonexistent_order_returns_404`
- `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestContainerPartitionKeys::test_no_default_id_partition_key`
- `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestContainerPartitionKeys::test_order_container_uses_customer_partition_key`
- `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestIndexingPolicies::test_has_composite_indexes_for_order_queries`
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

### Flaky Tests (73 tests)

These tests passed in some iterations but failed in others — indicates LLM stochasticity rather than a systematic gap.

| Test | Pass Rate | Outcomes |
|------|-----------|----------|
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCreateOrder::test_create_order_calculates_total` | 60.0% | passed, failed, failed, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCreateOrder::test_create_order_has_timestamp` | 60.0% | passed, failed, failed, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCreateOrder::test_create_order_response_has_required_fields` | 60.0% | passed, failed, failed, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCreateOrder::test_create_order_returns_items` | 60.0% | passed, failed, failed, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCreateOrder::test_new_order_status_is_pending` | 60.0% | passed, failed, failed, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerOrders::test_customer_001_has_2_orders` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerOrders::test_customer_orders_entries_have_required_fields` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerOrders::test_customer_orders_only_contains_own_orders` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerOrders::test_customer_orders_returns_200` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerOrders::test_customer_orders_returns_array` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerOrders::test_empty_customer_returns_empty_array` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerSummary::test_customer_001_summary_correct` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerSummary::test_customer_003_summary_correct` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerSummary::test_customer_summary_average_is_correct` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerSummary::test_customer_summary_has_required_fields` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerSummary::test_customer_summary_returns_200` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestCustomerSummary::test_nonexistent_customer_summary_empty` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestDeleteOrder::test_delete_pending_order_returns_204` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestDeleteOrder::test_delete_shipped_order_returns_409` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestDeleteOrder::test_deleted_order_returns_404_on_get` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestGetOrder::test_get_existing_order` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestGetOrder::test_get_order_has_required_fields` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestGetOrder::test_get_order_returns_correct_data` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByDateRange::test_past_date_range_returns_empty` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByDateRange::test_query_by_date_range_returns_200` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByDateRange::test_query_by_date_range_returns_array` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByDateRange::test_wide_date_range_includes_seeded_orders` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByStatus::test_all_seeded_orders_are_pending` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByStatus::test_query_by_status_filters_correctly` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByStatus::test_query_by_status_returns_200` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByStatus::test_query_by_status_returns_array` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestQueryByStatus::test_query_shipped_initially_empty` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestUpdateOrderStatus::test_update_status_reflects_new_status` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestUpdateOrderStatus::test_update_status_response_has_required_fields` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestUpdateOrderStatus::test_update_status_returns_200` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_api_contract.TestUpdateOrderStatus::test_updated_status_persists_on_get` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestCrossBoundaryConsistency::test_customer_id_stored_correctly` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestCrossBoundaryConsistency::test_status_round_trip_through_cosmos` | 0.0% | skipped, error, error, skipped, skipped |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestCrossBoundaryConsistency::test_stored_item_count_matches_api` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestCrossBoundaryConsistency::test_stored_total_matches_api_total` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestCrossBoundaryConsistency::test_timestamps_stored_as_iso_strings` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestDocumentStructure::test_documents_have_schema_version` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestDocumentStructure::test_documents_have_type_discriminator` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestDocumentStructure::test_orders_have_embedded_items` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestEnumSerialization::test_status_query_returns_correct_results` | 60.0% | passed, failed, failed, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_cosmos_infrastructure.TestEnumSerialization::test_status_stored_as_string` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_data_integrity.TestDataPersistence::test_containers_exist_in_cosmos` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestComputedFieldAccuracy::test_multi_item_total` | 60.0% | passed, failed, failed, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestComputedFieldAccuracy::test_multi_quantity_total` | 60.0% | passed, failed, failed, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestComputedFieldAccuracy::test_seeded_order_totals_correct` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestComputedFieldAccuracy::test_single_item_total` | 60.0% | passed, failed, failed, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestCustomerSummaryConsistency::test_summary_reflects_deleted_order` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestCustomerSummaryConsistency::test_summary_updates_after_new_order` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestDataIsolation::test_customer_orders_isolated` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestDataIsolation::test_different_customers_see_different_orders` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestDataTypeCorrectness::test_order_field_types` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestDataTypeCorrectness::test_order_item_field_types` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestEdgeCases::test_get_order_preserves_all_items` | 60.0% | passed, failed, failed, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestEdgeCases::test_large_quantity_order` | 60.0% | passed, failed, failed, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestEdgeCases::test_order_with_many_items` | 60.0% | passed, failed, failed, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestInvalidInput::test_update_status_empty_body_returns_4xx` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestInvalidInput::test_update_status_invalid_value_returns_4xx` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitionRules::test_cancelled_to_anything_rejected` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitionRules::test_delivered_to_anything_rejected` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitionRules::test_pending_to_cancelled_allowed` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitionRules::test_pending_to_shipped_allowed` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitionRules::test_shipped_to_delivered_allowed` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitionRules::test_shipped_to_pending_rejected` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitions::test_updated_order_no_longer_in_original_status` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestStatusTransitions::test_updated_status_appears_in_status_query` | 60.0% | passed, error, error, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestWriteReadConsistency::test_created_order_fields_match_on_get` | 60.0% | passed, failed, failed, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestWriteReadConsistency::test_order_appears_in_customer_history` | 60.0% | passed, failed, failed, passed, passed |
| `testing-v2.scenarios.ecommerce-order-api.tests.test_robustness.TestWriteReadConsistency::test_order_appears_in_status_query` | 60.0% | passed, failed, failed, passed, passed |

## Statistical Assessment

**Insufficient confidence** (σ ≥ 15%): Very high variance — results are dominated by LLM stochasticity. More iterations or scenario simplification needed.
