# iteration-001-python - Python Iot Device Telemetry

## Metadata
- **Date**: 2026-03-13
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

**Pass rate: 100.0%** (All 37 tests passed)

| Status | Count |
|--------|-------|
| Passed | 37 |
| Failed | 0 |
| Errors | 0 |
| Skipped | 0 |

### All tests passed

The generated application fully conforms to the API contract.

## Source Files

Source code archived in `source-code.zip` (3 files).

## Score Summary

| Category | Score | Notes |
|----------|-------|-------|
| API Conformance | 10/10 | 100.0% pass rate |
| **Overall** | **10/10** | **All 37 tests passed** |
