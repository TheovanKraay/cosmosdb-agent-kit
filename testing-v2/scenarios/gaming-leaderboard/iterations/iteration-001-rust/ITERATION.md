# iteration-001-rust - Rust Gaming Leaderboard

## Metadata
- **Date**: 2026-04-28
- **Language/SDK**: Rust
- **Agent**: GitHub Copilot (automated iteration)
- **Tester**: Automated CI
- **Run Type**: Normal run (skills loaded)

## Skills Verification

**Were skills loaded before building?** Yes (via issue prompt referencing AGENTS.md)

## Cosmos DB Patterns Detected

| Pattern | Status | Related Rule |
|---------|--------|--------------|
| Singleton CosmosClient | Not detected | `sdk-singleton-client` |
| Direct connection mode | Not detected | `sdk-connection-mode` |
| Gateway connection mode | Not detected | `sdk-connection-mode` |
| Partition key configured | Detected | `partition-high-cardinality` |
| Bulk operations | Not detected | `sdk-bulk-operations` |
| ETag optimistic concurrency | Detected | `sdk-etag-concurrency` |
| Point reads (by ID + partition key) | Detected | `query-avoid-scans` |
| Cross-partition queries | Not detected | `query-avoid-cross-partition` |
| Custom indexing policy | Not detected | `index-exclude-unused` |
| Throughput configuration | Not detected | `throughput-provision-rus` |
| Change feed usage | Not detected | `pattern-change-feed` |
| Diagnostics/logging | Not detected | `sdk-diagnostics` |

## Test Results

**Pass rate: 40.4%** (38/94 tests passed (40.4%))

| Status | Count |
|--------|-------|
| Passed | 38 |
| Failed | 55 |
| Errors | 0 |
| Skipped | 1 |

### Failures

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGetPlayer::test_get_existing_player**
  > AssertionError: GET /api/players/player-001 should return 200, got 404
assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGetPlayer::test_get_player_has_required_fields**
  > assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGetPlayer::test_get_player_stats_updated_after_scores**
  > assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGlobalLeaderboard::test_global_leaderboard_returns_200**
  > AssertionError: GET /api/leaderboards/global should return 200, got 500
assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGlobalLeaderboard::test_global_leaderboard_returns_array**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGlobalLeaderboard::test_global_leaderboard_entries_have_required_fields**
  > KeyError: 0

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGlobalLeaderboard::test_global_leaderboard_sorted_descending**
  > TypeError: string indices must be integers, not 'str'

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGlobalLeaderboard::test_global_leaderboard_ranks_sequential**
  > TypeError: string indices must be integers, not 'str'

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGlobalLeaderboard::test_global_leaderboard_top_player_is_highest_scorer**
  > KeyError: 0

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGlobalLeaderboard::test_global_leaderboard_respects_top_parameter**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestRegionalLeaderboard::test_regional_leaderboard_returns_200**
  > AssertionError: GET /api/leaderboards/regional/US should return 200, got 404
assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestRegionalLeaderboard::test_regional_leaderboard_only_contains_region_players**
  > requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestRegionalLeaderboard::test_regional_leaderboard_sorted_descending**
  > requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestRegionalLeaderboard::test_regional_leaderboard_entries_have_required_fields**
  > requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestPlayerRank::test_player_rank_returns_200**
  > AssertionError: GET /api/players/player-001/rank should return 200, got 404
assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestPlayerRank::test_player_rank_has_required_fields**
  > assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestPlayerRank::test_player_rank_correct_for_top_player**
  > assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestPlayerRank::test_player_rank_neighbors_is_array**
  > requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestPlayerRank::test_player_rank_neighbors_have_required_fields**
  > requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGetPlayerScores::test_score_history_returns_200**
  > AssertionError: GET /api/players/player-001/scores should return 200, got 404. Response: 
assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGetPlayerScores::test_score_history_returns_array**
  > assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGetPlayerScores::test_score_history_entries_have_required_fields**
  > assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGetPlayerScores::test_score_history_contains_all_player_scores**
  > assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGetPlayerScores::test_score_history_ordered_by_most_recent_first**
  > assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGetPlayerScores::test_score_history_respects_limit**
  > assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGetPlayerScores::test_score_history_only_shows_own_scores**
  > assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestUpdatePlayer::test_update_player_returns_200**
  > AssertionError: PATCH /api/players/player-005 should return 200, got 404. Response: 
assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestUpdatePlayer::test_update_player_response_has_required_fields**
  > assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestUpdatePlayer::test_update_player_reflects_new_display_name**
  > assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestUpdatePlayer::test_update_player_preserves_stats**
  > requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)

- **testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestDeletePlayer::test_delete_player_returns_204**
  > AssertionError: DELETE /api/players/delete-me-001 should return 204, got 404
assert 404 == 204
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_cosmos_infrastructure.TestLeaderboardIndexing::test_has_composite_indexes**
  > AssertionError: No container has composite indexes. Leaderboard queries need composite indexes on (score DESC, timestamp ASC) for efficient ORDER BY within a partition. Without them, the query engine 

- **testing-v2.scenarios.gaming-leaderboard.tests.test_cosmos_infrastructure.TestDocumentStructure::test_documents_have_schema_version**
  > Failed: No documents have a schema version field. (Rule: model-schema-versioning)

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestComputedFieldAccuracy::test_average_score_mathematically_correct**
  > assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestComputedFieldAccuracy::test_total_games_count_correct**
  > assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestComputedFieldAccuracy::test_best_score_is_maximum**
  > assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestComputedFieldAccuracy::test_player_with_single_score_stats**
  > assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestComputedFieldAccuracy::test_new_score_updates_stats**
  > assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestDataTypeCorrectness::test_player_stats_types**
  > assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestDataTypeCorrectness::test_leaderboard_entry_types**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestWriteReadConsistency::test_created_player_fully_retrievable**
  > AssertionError: Player was created successfully but GET returned 404. Data may not be persisted correctly.
assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestWriteReadConsistency::test_score_reflected_in_leaderboard**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestWriteReadConsistency::test_regional_filter_matches_stored_region**
  > assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestWriteReadConsistency::test_player_rank_score_matches_leaderboard**
  > assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestEdgeCases::test_empty_region_leaderboard**
  > AssertionError: Empty region leaderboard should return 200, got 404. Must return empty array for regions with no players.
assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestEdgeCases::test_leaderboard_no_duplicate_players**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestEdgeCases::test_top_parameter_zero_returns_empty**
  > AssertionError: top=0 caused a server error (500). Edge case parameters must not crash the server.
assert 500 < 500
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestEdgeCases::test_top_parameter_one**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestRapidOperations::test_rapid_score_submissions_all_counted**
  > assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestRapidOperations::test_concurrent_score_submissions_all_counted**
  > assert 404 == 200
 +  where 404 = <Response [404]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestLeaderboardTiebreaking::test_tied_scores_sorted_by_display_name_ascending**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestLeaderboardTiebreaking::test_tied_scores_have_sequential_ranks**
  > assert 500 == 200
 +  where 500 = <Response [500]>.status_code

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestUpdateDeleteConsistency::test_updated_region_reflected_in_regional_leaderboard**
  > requests.exceptions.JSONDecodeError: Expecting value: line 1 column 1 (char 0)

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestUpdateDeleteConsistency::test_deleted_player_removed_from_leaderboard**
  > TypeError: string indices must be integers, not 'str'

- **testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestUpdateDeleteConsistency::test_deleted_player_scores_not_in_history**
  > assert 404 == 204
 +  where 404 = <Response [404]>.status_code

## Source Files

Source code archived in `source-code.zip` (1215 files).

## Build & Startup Signals

- **Build**: PASS
- **Startup**: PASS

## Results by Category

| Category | Passed | Failed | Skipped |
|----------|--------|--------|---------|
| api_contract | 14 | 31 | 0 |
| build_startup | 2 | 0 | 0 |
| cosmos_infrastructure | 10 | 2 | 1 |
| data_integrity | 5 | 0 | 0 |
| robustness | 9 | 22 | 0 |

## Score Summary

| Category | Score | Notes |
|----------|-------|-------|
| API Conformance | 3/10 | 40.4% pass rate; 2 infrastructure failures |
| **Overall** | **3/10** | **38/94 tests passed (40.4%)** |
