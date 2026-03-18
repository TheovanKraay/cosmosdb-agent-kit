# iteration-001-python - Python Iot Device Telemetry

## Metadata
- **Date**: 2026-03-18
- **Language/SDK**: Python
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
| Gateway connection mode | Not detected | `sdk-connection-mode` |
| Partition key configured | Detected | `partition-high-cardinality` |
| Bulk operations | Not detected | `sdk-bulk-operations` |
| ETag optimistic concurrency | Not detected | `sdk-etag-concurrency` |
| Point reads (by ID + partition key) | Detected | `query-avoid-scans` |
| Cross-partition queries | Detected | `query-avoid-cross-partition` |
| Custom indexing policy | Not detected | `index-exclude-unused` |
| Throughput configuration | Not detected | `throughput-provision-rus` |
| Change feed usage | Not detected | `pattern-change-feed` |
| Diagnostics/logging | Not detected | `sdk-diagnostics` |

## Test Results

**Pass rate: 91.3%** (84/92 tests passed (91.3%))

| Status | Count |
|--------|-------|
| Passed | 84 |
| Failed | 7 |
| Errors | 0 |
| Skipped | 1 |

### Failures

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestDeleteDevice::test_deleted_device_returns_404_on_get**
  > requests.exceptions.ConnectionError: ('Connection aborted.', ConnectionResetError(10054, 'An existing connection was forcibly closed by the remote host', None, 10054, None))

- **testing-v2.scenarios.iot-device-telemetry.tests.test_api_contract.TestDeleteDevice::test_deleted_device_removed_from_location_query**
  > requests.exceptions.ConnectionError: ('Connection aborted.', RemoteDisconnected('Remote end closed connection without response'))

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestComputedFieldAccuracy::test_stats_temperature_min_correct**
  > AssertionError: device-001 min temperature should be ~21.8 (readings: 22.5, 23.1, 21.8), got 20
assert 1.8000000000000007 < 0.1
 +  where 1.8000000000000007 = abs((20 - 21.8))

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestComputedFieldAccuracy::test_stats_humidity_values_correct**
  > AssertionError: humidity max should be ~46.5, got 50
assert 3.5 < 0.1
 +  where 3.5 = abs((50 - 46.5))

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestEdgeCases::test_latest_reading_is_most_recent**
  > AssertionError: Latest reading for device-001 should have temperature ~21.8 (the most recent seeded reading), got 22.5. The 'latest' endpoint may not be selecting by most recent timestamp.
assert 0.69

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestEdgeCases::test_batch_ingest_count_matches_input**
  > assert 422 == 201
 +  where 422 = <Response [422]>.status_code

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestUpdateDeleteConsistency::test_deleted_device_telemetry_returns_404**
  > requests.exceptions.ConnectionError: ('Connection aborted.', RemoteDisconnected('Remote end closed connection without response'))

## Source Files

Source code archived in `source-code.zip` (6 files).

## Build & Startup Signals

- **Build**: PASS
- **Startup**: PASS

## Results by Category

| Category | Passed | Failed | Skipped |
|----------|--------|--------|---------|
| api_contract | 44 | 2 | 0 |
| cosmos_infrastructure | 12 | 0 | 1 |
| data_integrity | 6 | 0 | 0 |
| robustness | 22 | 5 | 0 |

## Score Summary

| Category | Score | Notes |
|----------|-------|-------|
| API Conformance | 9/10 | 91.3% pass rate |
| **Overall** | **9/10** | **84/92 tests passed (91.3%)** |
