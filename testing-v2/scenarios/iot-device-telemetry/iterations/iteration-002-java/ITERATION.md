# iteration-002-java - Java Iot Device Telemetry

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
| Direct connection mode | Detected | `sdk-connection-mode` |
| Gateway connection mode | Detected | `sdk-connection-mode` |
| Partition key configured | Detected | `partition-high-cardinality` |
| Bulk operations | Not detected | `sdk-bulk-operations` |
| ETag optimistic concurrency | Detected | `sdk-etag-concurrency` |
| Point reads (by ID + partition key) | Detected | `query-avoid-scans` |
| Cross-partition queries | Detected | `query-avoid-cross-partition` |
| Custom indexing policy | Detected | `index-exclude-unused` |
| Throughput configuration | Detected | `throughput-provision-rus` |
| Change feed usage | Not detected | `pattern-change-feed` |
| Diagnostics/logging | Not detected | `sdk-diagnostics` |

## Test Results

**Pass rate: 89.1%** (82/92 tests passed (89.1%))

| Status | Count |
|--------|-------|
| Passed | 82 |
| Failed | 9 |
| Errors | 0 |
| Skipped | 1 |

### Failures

- **testing-v2.scenarios.iot-device-telemetry.tests.test_cosmos_infrastructure.TestDocumentStructure::test_documents_have_schema_version**
  > Failed: No documents have a schema version field. (Rule: model-schema-versioning)

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestInvalidInput::test_register_device_missing_device_id_returns_4xx**
  > AssertionError: Missing deviceId should return 4xx, got 500. The app must validate required fields and return 400.
assert 500 < 500
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestInvalidInput::test_register_device_empty_body_returns_4xx**
  > AssertionError: Empty body should return 4xx, got 500. Server must not crash (500) on missing fields.
assert 500 < 500
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestInvalidInput::test_ingest_telemetry_missing_device_id_returns_4xx**
  > AssertionError: Missing deviceId in telemetry should return 4xx, got 201. Server must validate required fields.
assert 400 <= 201
 +  where 201 = <Response [201]>.status_code

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestInvalidInput::test_ingest_telemetry_empty_body_returns_4xx**
  > AssertionError: Empty telemetry body should return 4xx, got 201. Server must not crash on missing fields.
assert 400 <= 201
 +  where 201 = <Response [201]>.status_code

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestInvalidInput::test_ingest_telemetry_for_nonexistent_device_returns_4xx**
  > AssertionError: Telemetry for nonexistent device should return 4xx, got 201. Server should validate device exists.
assert 400 <= 201
 +  where 201 = <Response [201]>.status_code

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestComputedFieldAccuracy::test_stats_temperature_min_correct**
  > AssertionError: device-001 min temperature should be ~21.8 (readings: 22.5, 23.1, 21.8), got 20.0
assert 1.8000000000000007 < 0.1
 +  where 1.8000000000000007 = abs((20.0 - 21.8))

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestComputedFieldAccuracy::test_stats_humidity_values_correct**
  > AssertionError: humidity max should be ~46.5, got 50.0
assert 3.5 < 0.1
 +  where 3.5 = abs((50.0 - 46.5))

- **testing-v2.scenarios.iot-device-telemetry.tests.test_robustness.TestEdgeCases::test_latest_reading_is_most_recent**
  > AssertionError: Latest reading for device-001 should have temperature ~21.8 (the most recent seeded reading), got 22.5. The 'latest' endpoint may not be selecting by most recent timestamp.
assert 0.69

## Source Files

Source code archived in `source-code.zip` (39 files).

## Build & Startup Signals

- **Build**: PASS
- **Startup**: PASS

## Results by Category

| Category | Passed | Failed | Skipped |
|----------|--------|--------|---------|
| api_contract | 46 | 0 | 0 |
| build_startup | 2 | 0 | 0 |
| cosmos_infrastructure | 11 | 1 | 1 |
| data_integrity | 6 | 0 | 0 |
| robustness | 19 | 8 | 0 |

## Score Summary

| Category | Score | Notes |
|----------|-------|-------|
| API Conformance | 7/10 | 89.1% pass rate; 1 infrastructure failures |
| **Overall** | **7/10** | **82/92 tests passed (89.1%)** |
