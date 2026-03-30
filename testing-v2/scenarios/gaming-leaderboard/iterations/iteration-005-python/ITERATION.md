# iteration-005-python - Python Gaming Leaderboard

## Metadata
- **Date**: 2026-03-30
- **Language/SDK**: Python
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
| Cross-partition queries | Detected | `query-avoid-cross-partition` |
| Custom indexing policy | Detected | `index-exclude-unused` |
| Throughput configuration | Not detected | `throughput-provision-rus` |
| Change feed usage | Not detected | `pattern-change-feed` |
| Diagnostics/logging | Not detected | `sdk-diagnostics` |

## Test Results

**Pass rate: 96.8%** (91/94 tests passed (96.8%))

| Status | Count |
|--------|-------|
| Passed | 91 |
| Failed | 3 |
| Errors | 0 |
| Skipped | 0 |

### Failures

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestDeletePlayer::test_deleted_player_returns_404_on_get**
  > requests.exceptions.ConnectionError: ('Connection aborted.', ConnectionResetError(10054, 'An existing connection was forcibly closed by the remote host', None, 10054, None))

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestUpdateDeleteConsistency::test_deleted_player_removed_from_leaderboard**
  > requests.exceptions.ConnectionError: ('Connection aborted.', RemoteDisconnected('Remote end closed connection without response'))

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestUpdateDeleteConsistency::test_deleted_player_scores_not_in_history**
  > requests.exceptions.ConnectionError: ('Connection aborted.', RemoteDisconnected('Remote end closed connection without response'))

## Source Files

Source code archived in `source-code.zip` (6 files).

## Build & Startup Signals

- **Build**: PASS
- **Startup**: PASS

## Results by Category

| Category | Passed | Failed | Skipped |
|----------|--------|--------|---------|
| api_contract | 44 | 1 | 0 |
| build_startup | 2 | 0 | 0 |
| cosmos_infrastructure | 13 | 0 | 0 |
| data_integrity | 5 | 0 | 0 |
| robustness | 29 | 2 | 0 |

## Score Summary

| Category | Score | Notes |
|----------|-------|-------|
| API Conformance | 9/10 | 96.8% pass rate |
| **Overall** | **9/10** | **91/94 tests passed (96.8%)** |
