# iteration-002-java - Java Multitenant Saas

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
| Point reads (by ID + partition key) | Detected | `query-avoid-scans` |
| Cross-partition queries | Detected | `query-avoid-cross-partition` |
| Custom indexing policy | Detected | `index-exclude-unused` |
| Throughput configuration | Detected | `throughput-provision-rus` |
| Change feed usage | Not detected | `pattern-change-feed` |
| Diagnostics/logging | Not detected | `sdk-diagnostics` |

## Test Results

**Pass rate: 0%** (0/0 tests passed (0%))

| Status | Count |
|--------|-------|
| Passed | 0 |
| Failed | 0 |
| Errors | 1 |
| Skipped | 0 |

### Failures

- **startup**
  > Application failed to start. 	at io.netty.handler.ssl.util.LazyX509Certificate.verify(LazyX509Certificate.java:197) ~[netty-handler-4.1.104.Final.jar!/:4.1.104.Final]
	at java.base/sun.security.provid

## Source Files

Source code archived in `source-code.zip` (40 files).

## Build & Startup Signals

- **Build**: PASS
- **Startup**: PASS

## Results by Category

| Category | Passed | Failed | Skipped |
|----------|--------|--------|---------|
| build_startup | 1 | 1 | 0 |

## Score Summary

| Category | Score | Notes |
|----------|-------|-------|
| API Conformance | 1/10 | 0% pass rate |
| **Overall** | **1/10** | **0/0 tests passed (0%)** |
