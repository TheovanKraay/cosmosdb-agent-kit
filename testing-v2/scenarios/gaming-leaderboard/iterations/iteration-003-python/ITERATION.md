# iteration-003-python - Python Gaming Leaderboard

## Metadata
- **Date**: 2026-04-01
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
| Gateway connection mode | Not detected | `sdk-connection-mode` |
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

**Pass rate: 92.6%** (87/94 tests passed (92.6%))

| Status | Count |
|--------|-------|
| Passed | 87 |
| Failed | 7 |
| Errors | 0 |
| Skipped | 0 |

### Failures

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestUpdatePlayer::test_update_player_returns_200**
  > AssertionError: PATCH /api/players/player-005 should return 200, got 500. Response: {"detail":"ClientSession._request() got an unexpected keyword argument 'enable_cross_partition_query'"}
assert 500 =

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestUpdatePlayer::test_update_player_response_has_required_fields**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestUpdatePlayer::test_update_player_reflects_new_display_name**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestDeletePlayer::test_delete_player_returns_204**
  > AssertionError: DELETE /api/players/delete-me-001 should return 204, got 500
assert 500 == 204
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestUpdateDeleteConsistency::test_updated_region_reflected_in_regional_leaderboard**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestUpdateDeleteConsistency::test_deleted_player_removed_from_leaderboard**
  > assert 500 == 204
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestUpdateDeleteConsistency::test_deleted_player_scores_not_in_history**
  > assert 500 == 204
 +  where 500 = <Response [500]>.status_code

## Source Files

Source code archived in `source-code.zip` (6 files).

## Build & Startup Signals

- **Build**: PASS
- **Startup**: PASS

## Results by Category

| Category | Passed | Failed | Skipped |
|----------|--------|--------|---------|
| api_contract | 41 | 4 | 0 |
| build_startup | 2 | 0 | 0 |
| cosmos_infrastructure | 13 | 0 | 0 |
| data_integrity | 5 | 0 | 0 |
| robustness | 28 | 3 | 0 |

## Score Summary

| Category | Score | Notes |
|----------|-------|-------|
| API Conformance | 9/10 | 92.6% pass rate |
| **Overall** | **9/10** | **87/94 tests passed (92.6%)** |
