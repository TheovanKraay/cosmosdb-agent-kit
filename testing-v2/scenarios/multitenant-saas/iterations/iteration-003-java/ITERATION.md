# iteration-003-java - Java Multitenant Saas

## Metadata
- **Date**: 2026-04-14
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
| Point reads (by ID + partition key) | Not detected | `query-avoid-scans` |
| Cross-partition queries | Not detected | `query-avoid-cross-partition` |
| Custom indexing policy | Detected | `index-exclude-unused` |
| Throughput configuration | Detected | `throughput-provision-rus` |
| Change feed usage | Not detected | `pattern-change-feed` |
| Diagnostics/logging | Not detected | `sdk-diagnostics` |

## Test Results

**Pass rate: 100.0%** (All 73 tests passed)

| Status | Count |
|--------|-------|
| Passed | 73 |
| Failed | 0 |
| Errors | 0 |
| Skipped | 0 |

### All tests passed

The generated application fully conforms to the API contract.

## Source Files

Source code archived in `source-code.zip` (41 files).

## Build & Startup Signals

- **Build**: PASS
- **Startup**: PASS

## Results by Category

| Category | Passed | Failed | Skipped |
|----------|--------|--------|---------|
| api_contract | 50 | 0 | 0 |
| build_startup | 2 | 0 | 0 |
| cosmos_infrastructure | 16 | 0 | 0 |
| data_integrity | 7 | 0 | 0 |

## Score Summary

| Category | Score | Notes |
|----------|-------|-------|
| API Conformance | 10/10 | 100.0% pass rate |
| **Overall** | **10/10** | **All 73 tests passed** |
