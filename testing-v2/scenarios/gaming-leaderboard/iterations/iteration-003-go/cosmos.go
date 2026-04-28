package main

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"

	"github.com/Azure/azure-sdk-for-go/sdk/azcore"
	"github.com/Azure/azure-sdk-for-go/sdk/data/azcosmos"
)

// readPlayer reads a player document by playerId using a point read.
func readPlayer(ctx context.Context, playerID string) (*Player, error) {
	pk := azcosmos.NewPartitionKeyString(playerID)
	resp, err := playersCC.ReadItem(ctx, pk, playerID, nil)
	if err != nil {
		return nil, err
	}

	var player Player
	if err := json.Unmarshal(resp.Value, &player); err != nil {
		return nil, fmt.Errorf("unmarshal player: %w", err)
	}
	player.ETag = string(resp.ETag)
	return &player, nil
}

// readPlayerWithETag reads a player and returns the ETag for optimistic concurrency.
func readPlayerWithETag(ctx context.Context, playerID string) (*Player, azcore.ETag, error) {
	pk := azcosmos.NewPartitionKeyString(playerID)
	resp, err := playersCC.ReadItem(ctx, pk, playerID, nil)
	if err != nil {
		return nil, "", err
	}

	var player Player
	if err := json.Unmarshal(resp.Value, &player); err != nil {
		return nil, "", fmt.Errorf("unmarshal player: %w", err)
	}
	return &player, resp.ETag, nil
}

// updatePlayerStats updates the player's stats after a score submission.
// Uses ETag-based optimistic concurrency with retry loop.
func updatePlayerStats(ctx context.Context, playerID string, newScore int) error {
	pk := azcosmos.NewPartitionKeyString(playerID)

	for retries := 0; retries < 20; retries++ {
		resp, err := playersCC.ReadItem(ctx, pk, playerID, nil)
		if err != nil {
			return fmt.Errorf("read player for stats update: %w", err)
		}

		var player Player
		if err := json.Unmarshal(resp.Value, &player); err != nil {
			return fmt.Errorf("unmarshal player: %w", err)
		}

		player.TotalGames++
		player.TotalScore += int64(newScore)
		if newScore > player.BestScore {
			player.BestScore = newScore
		}
		player.AverageScore = float64(player.TotalScore) / float64(player.TotalGames)

		data, _ := json.Marshal(player)
		opts := &azcosmos.ItemOptions{
			IfMatchEtag: &resp.ETag,
		}

		_, err = playersCC.ReplaceItem(ctx, pk, player.ID, data, opts)
		if err != nil {
			if isPreconditionFailed(err) {
				continue // Retry with fresh ETag
			}
			return fmt.Errorf("replace player: %w", err)
		}
		return nil
	}
	return fmt.Errorf("failed to update player stats after retries")
}

// queryScoresByPlayer queries scores for a player, ordered by timestamp DESC.
func queryScoresByPlayer(ctx context.Context, playerID string, limit int) ([]Score, error) {
	pk := azcosmos.NewPartitionKeyString(playerID)
	query := fmt.Sprintf(
		"SELECT * FROM c WHERE c.playerId = @playerId AND c.type = 'score' ORDER BY c.timestamp DESC OFFSET 0 LIMIT %d",
		limit,
	)
	params := []azcosmos.QueryParameter{
		{Name: "@playerId", Value: playerID},
	}
	queryOpts := &azcosmos.QueryOptions{
		QueryParameters: params,
	}

	pager := scoresCC.NewQueryItemsPager(query, pk, queryOpts)
	var scores []Score

	for pager.More() {
		page, err := pager.NextPage(ctx)
		if err != nil {
			return nil, fmt.Errorf("query scores: %w", err)
		}
		for _, item := range page.Items {
			var s Score
			if err := json.Unmarshal(item, &s); err != nil {
				continue
			}
			scores = append(scores, s)
		}
	}

	return scores, nil
}

// queryLeaderboard queries leaderboard entries for a given scope.
func queryLeaderboard(ctx context.Context, scope string, limit int) ([]LeaderboardEntry, error) {
	pk := azcosmos.NewPartitionKeyString(scope)
	query := "SELECT * FROM c WHERE c.leaderboardScope = @scope AND c.type = 'leaderboard'"
	params := []azcosmos.QueryParameter{
		{Name: "@scope", Value: scope},
	}
	queryOpts := &azcosmos.QueryOptions{
		QueryParameters: params,
	}

	pager := leaderboardCC.NewQueryItemsPager(query, pk, queryOpts)
	var entries []LeaderboardEntry

	for pager.More() {
		page, err := pager.NextPage(ctx)
		if err != nil {
			return nil, fmt.Errorf("query leaderboard: %w", err)
		}
		for _, item := range page.Items {
			var e LeaderboardEntry
			if err := json.Unmarshal(item, &e); err != nil {
				continue
			}
			entries = append(entries, e)
		}
	}

	return entries, nil
}

// upsertLeaderboardEntry upserts the global and regional leaderboard entries for a player.
func upsertLeaderboardEntry(ctx context.Context, player *Player, newScore int) {
	// Re-read the player to get updated stats
	updatedPlayer, err := readPlayer(ctx, player.PlayerID)
	if err != nil {
		return
	}

	bestScore := updatedPlayer.BestScore

	// Upsert global leaderboard entry
	globalEntry := LeaderboardEntry{
		ID:               player.PlayerID,
		PlayerID:         player.PlayerID,
		DisplayName:      updatedPlayer.DisplayName,
		Score:            bestScore,
		Region:           updatedPlayer.Region,
		LeaderboardScope: "global",
		Type:             "leaderboard",
	}
	data, _ := json.Marshal(globalEntry)
	pk := azcosmos.NewPartitionKeyString("global")
	_, _ = leaderboardCC.UpsertItem(ctx, pk, data, nil)

	// Upsert regional leaderboard entry
	regionalScope := "regional_" + updatedPlayer.Region
	regionalEntry := LeaderboardEntry{
		ID:               player.PlayerID,
		PlayerID:         player.PlayerID,
		DisplayName:      updatedPlayer.DisplayName,
		Score:            bestScore,
		Region:           updatedPlayer.Region,
		LeaderboardScope: regionalScope,
		Type:             "leaderboard",
	}
	data, _ = json.Marshal(regionalEntry)
	pk = azcosmos.NewPartitionKeyString(regionalScope)
	_, _ = leaderboardCC.UpsertItem(ctx, pk, data, nil)
}

// updateLeaderboardEntries updates leaderboard entries when player info changes.
func updateLeaderboardEntries(ctx context.Context, player *Player, oldRegion string, regionChanged bool) {
	// Update global entry
	globalEntry := LeaderboardEntry{
		ID:               player.PlayerID,
		PlayerID:         player.PlayerID,
		DisplayName:      player.DisplayName,
		Score:            player.BestScore,
		Region:           player.Region,
		LeaderboardScope: "global",
		Type:             "leaderboard",
	}
	data, _ := json.Marshal(globalEntry)
	pk := azcosmos.NewPartitionKeyString("global")
	_, _ = leaderboardCC.UpsertItem(ctx, pk, data, nil)

	if regionChanged && oldRegion != player.Region {
		// Delete old regional entry
		oldScope := "regional_" + oldRegion
		oldPK := azcosmos.NewPartitionKeyString(oldScope)
		_, _ = leaderboardCC.DeleteItem(ctx, oldPK, player.PlayerID, nil)

		// Create new regional entry
		newScope := "regional_" + player.Region
		regionalEntry := LeaderboardEntry{
			ID:               player.PlayerID,
			PlayerID:         player.PlayerID,
			DisplayName:      player.DisplayName,
			Score:            player.BestScore,
			Region:           player.Region,
			LeaderboardScope: newScope,
			Type:             "leaderboard",
		}
		data, _ = json.Marshal(regionalEntry)
		pk = azcosmos.NewPartitionKeyString(newScope)
		_, _ = leaderboardCC.UpsertItem(ctx, pk, data, nil)
	} else {
		// Update same-region entry
		scope := "regional_" + player.Region
		regionalEntry := LeaderboardEntry{
			ID:               player.PlayerID,
			PlayerID:         player.PlayerID,
			DisplayName:      player.DisplayName,
			Score:            player.BestScore,
			Region:           player.Region,
			LeaderboardScope: scope,
			Type:             "leaderboard",
		}
		data, _ = json.Marshal(regionalEntry)
		pk = azcosmos.NewPartitionKeyString(scope)
		_, _ = leaderboardCC.UpsertItem(ctx, pk, data, nil)
	}
}

// deleteLeaderboardEntriesForPlayer removes all leaderboard entries for a player.
func deleteLeaderboardEntriesForPlayer(ctx context.Context, playerID string, region string) {
	// Delete global entry
	globalPK := azcosmos.NewPartitionKeyString("global")
	_, _ = leaderboardCC.DeleteItem(ctx, globalPK, playerID, nil)

	// Delete regional entry using the player's known region
	if region != "" {
		scope := "regional_" + region
		pk := azcosmos.NewPartitionKeyString(scope)
		_, _ = leaderboardCC.DeleteItem(ctx, pk, playerID, nil)
	}
}

// isConflict checks if the error is a 409 Conflict.
func isConflict(err error) bool {
	if err == nil {
		return false
	}
	return strings.Contains(err.Error(), "409") || strings.Contains(err.Error(), "Conflict")
}

// isNotFound checks if the error is a 404 Not Found.
func isNotFound(err error) bool {
	if err == nil {
		return false
	}
	return strings.Contains(err.Error(), "404") || strings.Contains(err.Error(), "NotFound")
}

// isPreconditionFailed checks if the error is a 412 Precondition Failed.
func isPreconditionFailed(err error) bool {
	if err == nil {
		return false
	}
	return strings.Contains(err.Error(), "412") || strings.Contains(err.Error(), "Precondition")
}
