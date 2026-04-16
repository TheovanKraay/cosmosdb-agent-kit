# iteration-003-java - Java Iot Device Telemetry

## Metadata
- **Date**: 2026-04-16
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
| ETag optimistic concurrency | Detected | `sdk-etag-concurrency` |
| Point reads (by ID + partition key) | Detected | `query-avoid-scans` |
| Cross-partition queries | Not detected | `query-avoid-cross-partition` |
| Custom indexing policy | Detected | `index-exclude-unused` |
| Throughput configuration | Detected | `throughput-provision-rus` |
| Change feed usage | Not detected | `pattern-change-feed` |
| Diagnostics/logging | Not detected | `sdk-diagnostics` |

## Test Results

**Pass rate: 25.0%** (23/92 tests passed (25.0%))

| Status | Count |
|--------|-------|
| Passed | 23 |
| Failed | 0 |
| Errors | 69 |
| Skipped | 0 |

### Failures

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestGetDevice::test_get_existing_device**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestGetDevice::test_get_device_has_required_fields**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestGetDevicesByLocation::test_query_by_location_returns_200**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestGetDevicesByLocation::test_query_by_location_returns_array**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestGetDevicesByLocation::test_building_a_has_2_devices**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestGetDevicesByLocation::test_location_filter_is_correct**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestIngestTelemetry::test_ingest_returns_201**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestIngestTelemetry::test_ingest_response_has_required_fields**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestIngestTelemetry::test_ingest_returns_correct_values**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestBatchIngest::test_batch_ingest_returns_201**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestBatchIngest::test_batch_ingest_returns_count**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestLatestReading::test_latest_reading_returns_200**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestLatestReading::test_latest_reading_has_required_fields**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestLatestReading::test_latest_reading_is_for_correct_device**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestTimeRangeQuery::test_time_range_returns_200**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestTimeRangeQuery::test_time_range_returns_array**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestTimeRangeQuery::test_time_range_returns_readings_for_device**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestTimeRangeQuery::test_time_range_only_contains_correct_device**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestTimeRangeQuery::test_empty_time_range_returns_empty**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestDeviceStats::test_stats_returns_200**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestDeviceStats::test_stats_has_required_fields**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestDeviceStats::test_stats_temperature_has_min_max_avg**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestDeviceStats::test_stats_humidity_has_min_max_avg**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestDeviceStats::test_stats_values_are_numeric**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestDeviceStats::test_stats_with_period_parameter**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestUpdateDevice::test_update_device_returns_200**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestUpdateDevice::test_update_device_response_has_required_fields**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestUpdateDevice::test_update_device_reflects_new_name**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestUpdateDevice::test_update_device_location_reflected_in_queries**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestDeleteDevice::test_delete_device_returns_204**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestDeleteDevice::test_deleted_device_returns_404_on_get**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestDeleteDevice::test_deleted_device_removed_from_location_query**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestLocationSummary::test_location_summary_returns_200**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestLocationSummary::test_location_summary_returns_array**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestLocationSummary::test_location_summary_has_required_fields**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestLocationSummary::test_location_summary_contains_correct_devices**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestLocationSummary::test_location_summary_one_entry_per_device**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestLocationSummary::test_empty_location_returns_empty_array**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_cosmos_infrastructure.TestTelemetrySerialization::test_sensor_values_stored_as_numbers**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_cosmos_infrastructure.TestTelemetrySerialization::test_timestamps_stored_as_iso_strings**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_cosmos_infrastructure.TestDocumentStructure::test_documents_have_type_discriminator**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_cosmos_infrastructure.TestDocumentStructure::test_documents_have_schema_version**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_cosmos_infrastructure.TestCrossBoundaryConsistency::test_telemetry_stored_with_all_fields**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_cosmos_infrastructure.TestCrossBoundaryConsistency::test_device_metadata_stored_correctly**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_cosmos_infrastructure.TestCrossBoundaryConsistency::test_aggregation_values_match_raw_data**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_data_integrity.TestDataPersistence::test_containers_exist_in_cosmos**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestInvalidInput::test_ingest_telemetry_missing_device_id_returns_4xx**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestInvalidInput::test_ingest_telemetry_empty_body_returns_4xx**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestInvalidInput::test_ingest_telemetry_for_nonexistent_device_returns_4xx**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestComputedFieldAccuracy::test_stats_temperature_min_correct**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestComputedFieldAccuracy::test_stats_temperature_max_correct**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestComputedFieldAccuracy::test_stats_temperature_avg_correct**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestComputedFieldAccuracy::test_stats_humidity_values_correct**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestComputedFieldAccuracy::test_stats_values_are_consistent**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestDataTypeCorrectness::test_device_field_types**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestDataTypeCorrectness::test_telemetry_values_are_numbers**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestDataTypeCorrectness::test_stats_values_are_numeric**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestWriteReadConsistency::test_device_appears_in_location_query**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestWriteReadConsistency::test_ingested_reading_appears_in_latest**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestDataIsolation::test_time_range_only_returns_correct_device**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestDataIsolation::test_location_query_filters_correctly**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestEdgeCases::test_empty_location_returns_empty_array**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestEdgeCases::test_latest_reading_is_most_recent**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestEdgeCases::test_batch_ingest_count_matches_input**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestEdgeCases::test_future_time_range_returns_empty**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestEdgeCases::test_stats_for_device_with_single_reading**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestConcurrentIngestion::test_concurrent_ingestion_all_persisted**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestUpdateDeleteConsistency::test_deleted_device_telemetry_returns_404**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestUpdateDeleteConsistency::test_updated_device_preserves_telemetry**
  > failed on setup with "AssertionError: Failed to ingest reading for device-001: 500 {"error":"{\"innerErrorMessage\":\"Message: {\\\"Errors\\\":[\\\"The input ttl 'null' is invalid. Ensure to provide a

## Source Files

Source code archived in `source-code.zip` (30 files).

## Build & Startup Signals

- **Build**: PASS
- **Startup**: PASS

## Results by Category

| Category | Passed | Failed | Skipped |
|----------|--------|--------|---------|
| api_contract | 8 | 38 | 0 |
| build_startup | 2 | 0 | 0 |
| cosmos_infrastructure | 6 | 7 | 0 |
| data_integrity | 5 | 1 | 0 |
| robustness | 4 | 23 | 0 |

## Score Summary

| Category | Score | Notes |
|----------|-------|-------|
| API Conformance | 1/10 | 25.0% pass rate; 7 infrastructure failures |
| **Overall** | **1/10** | **23/92 tests passed (25.0%)** |
