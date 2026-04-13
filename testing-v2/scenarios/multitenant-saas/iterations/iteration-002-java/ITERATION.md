# iteration-002-java - Java Multitenant Saas

## Metadata
- **Date**: 2026-04-13
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

**Pass rate: 97.3%** (71/73 tests passed (97.3%))

| Status | Count |
|--------|-------|
| Passed | 71 |
| Failed | 0 |
| Errors | 0 |
| Skipped | 2 |

## Source Files

Source code archived in `source-code.zip` (40 files).

## Build & Startup Signals

- **Build**: PASS
- **Startup**: PASS

## Results by Category

| Category | Passed | Failed | Skipped |
|----------|--------|--------|---------|
| api_contract | 50 | 0 | 0 |
| build_startup | 2 | 0 | 0 |
| cosmos_infrastructure | 15 | 0 | 1 |
| data_integrity | 6 | 0 | 1 |

## Score Summary

| Category | Score | Notes |
|----------|-------|-------|
| API Conformance | 9/10 | 97.3% pass rate |
| **Overall** | **9/10** | **71/73 tests passed (97.3%)** |
