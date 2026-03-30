# Batch Test Results: Gaming Leaderboard

## Metadata
- **Date**: 2026-03-30
- **Scenario**: gaming-leaderboard
- **Language**: python
- **Skills loaded**: Yes (skills loaded)
- **Iterations**: 5
- **Batch issue**: #166
- **Child PRs**: 172,173,174,175,176

## Aggregate Summary

| Metric | Mean | Std Dev | Min | Max | Range |
|--------|------|---------|-----|-----|-------|
| Pass Rate | 77.5% | 14.6% | 64.9% | 96.8% | 31.89999999999999% |
| Score (1-10) | 6.6 | 1.5 | 5 | 9 | 4 |

## Per-Iteration Results

| Run | Build | Startup | Passed | Total | Pass Rate | Score |
|-----|-------|---------|--------|-------|-----------|-------|
| 1 | PASS | PASS | 64 | 94 | 68.1% | 6/10 |
| 2 | PASS | PASS | 64 | 94 | 68.1% | 6/10 |
| 3 | PASS | PASS | 61 | 94 | 64.9% | 5/10 |
| 4 | PASS | PASS | 84 | 94 | 89.4% | 7/10 |
| 5 | PASS | PASS | 91 | 94 | 96.8% | 9/10 |

## Category Breakdown

| Category | Mean | Std Dev | Min | Max |
|----------|------|---------|-----|-----|
| API Contract | 77.3% | 18.7% | 62.2% | 97.8% |
| Build & Startup | 100.0% | 0.0% | 100.0% | 100.0% |
| Cosmos Infrastructure | 81.5% | 12.9% | 69.2% | 100.0% |
| Data Integrity | 96.0% | 8.9% | 80.0% | 100.0% |
| Robustness | 72.9% | 16.0% | 61.3% | 93.5% |

## Test Consistency Analysis

- **Always pass**: 58 tests (60%)
- **Always fail**: 1 tests (1%)
- **Flaky** (stochastic): 37 tests (39%)

### Consistent Passes (58 tests)

- `build_startup::app_startup`
- `build_startup::build_compilation`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestCreatePlayer::test_create_player_response_has_required_fields`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestCreatePlayer::test_create_player_returns_201`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestCreatePlayer::test_create_player_returns_correct_data`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestCreatePlayer::test_new_player_has_zero_stats`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestDeletePlayer::test_delete_nonexistent_player_returns_404`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestDeletePlayer::test_delete_player_returns_204`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGetPlayer::test_get_existing_player`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGetPlayer::test_get_nonexistent_player_returns_404`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGetPlayer::test_get_player_has_required_fields`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGetPlayer::test_get_player_stats_updated_after_scores`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGetPlayerScores::test_score_history_contains_all_player_scores`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGetPlayerScores::test_score_history_entries_have_required_fields`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGetPlayerScores::test_score_history_for_nonexistent_player_returns_404`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGetPlayerScores::test_score_history_only_shows_own_scores`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGetPlayerScores::test_score_history_ordered_by_most_recent_first`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGetPlayerScores::test_score_history_respects_limit`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGetPlayerScores::test_score_history_returns_200`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGetPlayerScores::test_score_history_returns_array`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestHealth::test_health_returns_200`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestPlayerRank::test_nonexistent_player_rank_returns_404`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestSubmitScore::test_submit_score_response_has_required_fields`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestSubmitScore::test_submit_score_returns_201`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestSubmitScore::test_submit_score_returns_correct_data`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestUpdatePlayer::test_update_nonexistent_player_returns_404`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestUpdatePlayer::test_update_player_preserves_stats`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestUpdatePlayer::test_update_player_reflects_new_display_name`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestUpdatePlayer::test_update_player_response_has_required_fields`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestUpdatePlayer::test_update_player_returns_200`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_cosmos_infrastructure.TestContainerDesign::test_has_multiple_containers_or_synthetic_keys`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_cosmos_infrastructure.TestCrossBoundaryConsistency::test_leaderboard_entries_denormalized`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_cosmos_infrastructure.TestCrossBoundaryConsistency::test_player_stats_stored_correctly`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_cosmos_infrastructure.TestLeaderboardIndexing::test_has_composite_indexes`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_cosmos_infrastructure.TestLeaderboardIndexing::test_has_custom_indexing_policy`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_cosmos_infrastructure.TestPlayerScoreSerialization::test_etag_present_on_player_documents`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_cosmos_infrastructure.TestPlayerScoreSerialization::test_scores_stored_as_numbers`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_cosmos_infrastructure.TestThroughputConfiguration::test_throughput_is_configured`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_data_integrity.TestDataPersistence::test_database_is_created`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_data_integrity.TestDataPersistence::test_player_document_exists_in_cosmos`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_data_integrity.TestIndexingPolicy::test_containers_have_indexing_policy`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_data_integrity.TestPartitionKeyDesign::test_containers_have_partition_keys`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestComputedFieldAccuracy::test_average_score_mathematically_correct`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestComputedFieldAccuracy::test_best_score_is_maximum`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestComputedFieldAccuracy::test_new_score_updates_stats`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestComputedFieldAccuracy::test_player_with_single_score_stats`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestComputedFieldAccuracy::test_total_games_count_correct`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestDataTypeCorrectness::test_player_stats_types`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestDataTypeCorrectness::test_score_submission_returns_correct_types`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestEdgeCases::test_top_parameter_zero_returns_empty`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestEdgeCases::test_zero_score_submission`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestInvalidInput::test_create_player_empty_body_returns_4xx`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestInvalidInput::test_create_player_missing_required_fields_returns_4xx`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestInvalidInput::test_submit_score_for_nonexistent_player_returns_4xx`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestInvalidInput::test_submit_score_missing_player_id_returns_4xx`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestInvalidInput::test_submit_score_missing_score_returns_4xx`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestRapidOperations::test_rapid_score_submissions_all_counted`
- `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestWriteReadConsistency::test_created_player_fully_retrievable`

### Consistent Failures (1 tests)

These tests failed in EVERY iteration — likely indicates a real gap (missing rule, contract misunderstanding, or SDK issue).

- `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestUpdateDeleteConsistency::test_deleted_player_removed_from_leaderboard`

### Flaky Tests (37 tests)

These tests passed in some iterations but failed in others — indicates LLM stochasticity rather than a systematic gap.

| Test | Pass Rate | Outcomes |
|------|-----------|----------|
| `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestDeletePlayer::test_deleted_player_returns_404_on_get` | 40.0% | passed, passed, failed, failed, failed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGlobalLeaderboard::test_global_leaderboard_entries_have_required_fields` | 40.0% | failed, failed, failed, passed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGlobalLeaderboard::test_global_leaderboard_ranks_sequential` | 40.0% | failed, failed, failed, passed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGlobalLeaderboard::test_global_leaderboard_respects_top_parameter` | 40.0% | failed, failed, failed, passed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGlobalLeaderboard::test_global_leaderboard_returns_200` | 40.0% | failed, failed, failed, passed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGlobalLeaderboard::test_global_leaderboard_returns_array` | 40.0% | failed, failed, failed, passed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGlobalLeaderboard::test_global_leaderboard_sorted_descending` | 40.0% | failed, failed, failed, passed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestGlobalLeaderboard::test_global_leaderboard_top_player_is_highest_scorer` | 40.0% | failed, failed, failed, passed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestPlayerRank::test_player_rank_correct_for_top_player` | 40.0% | failed, failed, failed, passed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestPlayerRank::test_player_rank_has_required_fields` | 40.0% | failed, failed, failed, passed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestPlayerRank::test_player_rank_neighbors_have_required_fields` | 40.0% | failed, failed, failed, passed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestPlayerRank::test_player_rank_neighbors_is_array` | 40.0% | failed, failed, failed, passed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestPlayerRank::test_player_rank_returns_200` | 40.0% | failed, failed, failed, passed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestRegionalLeaderboard::test_regional_leaderboard_entries_have_required_fields` | 40.0% | failed, failed, failed, passed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestRegionalLeaderboard::test_regional_leaderboard_only_contains_region_players` | 40.0% | failed, failed, failed, passed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestRegionalLeaderboard::test_regional_leaderboard_returns_200` | 40.0% | failed, failed, failed, passed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_api_contract.TestRegionalLeaderboard::test_regional_leaderboard_sorted_descending` | 40.0% | failed, failed, failed, passed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_cosmos_infrastructure.TestContainerDesign::test_leaderboard_container_uses_synthetic_key` | 20.0% | skipped, skipped, skipped, skipped, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_cosmos_infrastructure.TestContainerDesign::test_player_container_uses_player_id_key` | 80.0% | passed, passed, passed, failed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_cosmos_infrastructure.TestCrossBoundaryConsistency::test_synthetic_partition_key_value_format` | 20.0% | skipped, skipped, skipped, skipped, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_cosmos_infrastructure.TestDocumentStructure::test_documents_have_schema_version` | 60.0% | passed, passed, failed, failed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_cosmos_infrastructure.TestDocumentStructure::test_documents_have_type_discriminator` | 80.0% | passed, passed, failed, passed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_data_integrity.TestPartitionKeyDesign::test_no_container_uses_id_as_sole_partition_key` | 80.0% | passed, passed, passed, failed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestDataTypeCorrectness::test_leaderboard_entry_types` | 40.0% | failed, failed, failed, passed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestDuplicateHandling::test_create_duplicate_player_does_not_return_500` | 80.0% | passed, passed, passed, failed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestEdgeCases::test_empty_region_leaderboard` | 40.0% | failed, failed, failed, passed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestEdgeCases::test_leaderboard_no_duplicate_players` | 40.0% | failed, failed, failed, passed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestEdgeCases::test_top_parameter_one` | 40.0% | failed, failed, failed, passed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestInvalidInput::test_submit_score_negative_value_returns_4xx` | 80.0% | passed, passed, passed, failed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestLeaderboardTiebreaking::test_tied_scores_have_sequential_ranks` | 40.0% | failed, failed, failed, passed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestLeaderboardTiebreaking::test_tied_scores_sorted_by_display_name_ascending` | 40.0% | failed, failed, failed, passed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestRapidOperations::test_concurrent_score_submissions_all_counted` | 60.0% | failed, failed, passed, passed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestUpdateDeleteConsistency::test_deleted_player_scores_not_in_history` | 40.0% | passed, passed, failed, failed, failed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestUpdateDeleteConsistency::test_updated_region_reflected_in_regional_leaderboard` | 40.0% | failed, failed, failed, passed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestWriteReadConsistency::test_player_rank_score_matches_leaderboard` | 40.0% | failed, failed, failed, passed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestWriteReadConsistency::test_regional_filter_matches_stored_region` | 40.0% | failed, failed, failed, passed, passed |
| `testing-v2.scenarios.gaming-leaderboard.tests.test_robustness.TestWriteReadConsistency::test_score_reflected_in_leaderboard` | 40.0% | failed, failed, failed, passed, passed |

## Statistical Assessment

**Low confidence** (8% ≤ σ < 15%): Significant variance across iterations. Consider running more iterations for reliable comparison.
