# iteration-004-python - Python Gaming Leaderboard

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
| Gateway connection mode | Not detected | `sdk-connection-mode` |
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

**Pass rate: 89.4%** (84/94 tests passed (89.4%))

| Status | Count |
|--------|-------|
| Passed | 84 |
| Failed | 8 |
| Errors | 0 |
| Skipped | 2 |

### Failures

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestDeletePlayer::test_deleted_player_returns_404_on_get**
  > requests.exceptions.ConnectionError: ('Connection aborted.', ConnectionResetError(10054, 'An existing connection was forcibly closed by the remote host', None, 10054, None))

- **testing-v2.scenarios.gaming-leaderboard.tests.test_cosmos_infrastructure.TestContainerDesign::test_player_container_uses_player_id_key**
  > AssertionError: Player container 'players' doesn't use playerId as partition key (has: ['/id']). Player profiles are looked up by playerId — partition key should match for efficient point reads. (Rule

- **testing-v2.scenarios.gaming-leaderboard.tests.test_cosmos_infrastructure.TestDocumentStructure::test_documents_have_schema_version**
  > Failed: No documents have a schema version field. (Rule: model-schema-versioning)

- **testing-v2.scenarios.gaming-leaderboard.tests.test_data_integrity.TestPartitionKeyDesign::test_no_container_uses_id_as_sole_partition_key**
  > Failed: Container 'players' uses /id as partition key. This is an anti-pattern — it prevents efficient cross-document queries within a partition. Consider /playerId, /region, or another high-cardinali

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestInvalidInput::test_submit_score_negative_value_returns_4xx**
  > AssertionError: Negative score should return 4xx, got 201. Scores should be positive integers per the contract.
assert 400 <= 201
 +  where 201 = <Response [201]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestDuplicateHandling::test_create_duplicate_player_does_not_return_500**
  > AssertionError: Duplicate player creation returned 500 — server crashed. Expected 409 Conflict or idempotent 200/201. Response: Internal Server Error
assert 500 != 500
 +  where 500 = <Response [500]>

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
| cosmos_infrastructure | 9 | 2 | 2 |
| data_integrity | 4 | 1 | 0 |
| robustness | 27 | 4 | 0 |

## Score Summary

| Category | Score | Notes |
|----------|-------|-------|
| API Conformance | 7/10 | 89.4% pass rate; 2 infrastructure failures |
| **Overall** | **7/10** | **84/94 tests passed (89.4%)** |
