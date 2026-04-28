package main

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"time"

	"github.com/Azure/azure-sdk-for-go/sdk/azcore"
	"github.com/Azure/azure-sdk-for-go/sdk/data/azcosmos"
	"github.com/google/uuid"
)

var (
	ErrNotFound            = errors.New("not found")
	ErrConflict            = errors.New("conflict")
	ErrPreconditionFailed  = errors.New("precondition failed")
)

// CosmosRepository handles all Cosmos DB operations.
type CosmosRepository struct {
	client              *azcosmos.Client
	playersContainer    *azcosmos.ContainerClient
	leaderboardContainer *azcosmos.ContainerClient
}

func NewCosmosRepository(endpoint, key string) (*CosmosRepository, error) {
	cred, err := azcosmos.NewKeyCredential(key)
	if err != nil {
		return nil, fmt.Errorf("failed to create credential: %w", err)
	}

	// Configure for emulator: Gateway mode + skip TLS verification
	opts := &azcosmos.ClientOptions{}
	customTransport := &http.Transport{
		TLSClientConfig: &tls.Config{
			InsecureSkipVerify: true,
		},
	}
	opts.Transport = &policyClient{transport: customTransport}

	client, err := azcosmos.NewClientWithKey(endpoint, cred, opts)
	if err != nil {
		return nil, fmt.Errorf("failed to create cosmos client: %w", err)
	}

	return &CosmosRepository{client: client}, nil
}

// policyClient wraps http.Transport to satisfy the azcore policy.Transporter interface.
type policyClient struct {
	transport *http.Transport
}

func (p *policyClient) Do(req *http.Request) (*http.Response, error) {
	return p.transport.RoundTrip(req)
}

// InitializeDatabase creates the database and containers if they don't exist.
func (r *CosmosRepository) InitializeDatabase(ctx context.Context, dbName string) error {
	dbProps := azcosmos.DatabaseProperties{ID: dbName}
	_, err := r.client.CreateDatabase(ctx, dbProps, nil)
	if err != nil && !isConflictError(err) {
		return fmt.Errorf("failed to create database: %w", err)
	}

	db, err := r.client.NewDatabase(dbName)
	if err != nil {
		return fmt.Errorf("failed to get database client: %w", err)
	}

	// Players container with /playerId partition key
	playersProps := azcosmos.ContainerProperties{
		ID: "players",
		PartitionKeyDefinition: azcosmos.PartitionKeyDefinition{
			Paths: []string{"/playerId"},
		},
	}
	_, err = db.CreateContainer(ctx, playersProps, nil)
	if err != nil && !isConflictError(err) {
		return fmt.Errorf("failed to create players container: %w", err)
	}

	// Leaderboard container with /leaderboardType partition key
	lbProps := azcosmos.ContainerProperties{
		ID: "leaderboard",
		PartitionKeyDefinition: azcosmos.PartitionKeyDefinition{
			Paths: []string{"/leaderboardType"},
		},
	}
	_, err = db.CreateContainer(ctx, lbProps, nil)
	if err != nil && !isConflictError(err) {
		return fmt.Errorf("failed to create leaderboard container: %w", err)
	}

	playersContainer, err := db.NewContainer("players")
	if err != nil {
		return fmt.Errorf("failed to get players container client: %w", err)
	}

	lbContainer, err := db.NewContainer("leaderboard")
	if err != nil {
		return fmt.Errorf("failed to get leaderboard container client: %w", err)
	}

	r.playersContainer = playersContainer
	r.leaderboardContainer = lbContainer
	return nil
}

func isConflictError(err error) bool {
	var respErr *azcore.ResponseError
	if errors.As(err, &respErr) {
		return respErr.StatusCode == http.StatusConflict
	}
	return false
}

func isNotFoundError(err error) bool {
	var respErr *azcore.ResponseError
	if errors.As(err, &respErr) {
		return respErr.StatusCode == http.StatusNotFound
	}
	return false
}

func isPreconditionFailedError(err error) bool {
	var respErr *azcore.ResponseError
	if errors.As(err, &respErr) {
		return respErr.StatusCode == http.StatusPreconditionFailed
	}
	return false
}

// CreatePlayer creates a new player document.
func (r *CosmosRepository) CreatePlayer(ctx context.Context, player Player) (Player, error) {
	player.ID = player.PlayerID
	player.Type = "player"
	player.TotalGames = 0
	player.BestScore = 0
	player.AverageScore = 0

	data, err := json.Marshal(player)
	if err != nil {
		return Player{}, fmt.Errorf("failed to marshal player: %w", err)
	}

	pk := azcosmos.NewPartitionKeyString(player.PlayerID)
	resp, err := r.playersContainer.CreateItem(ctx, pk, data, nil)
	if err != nil {
		if isConflictError(err) {
			return Player{}, ErrConflict
		}
		return Player{}, fmt.Errorf("failed to create player: %w", err)
	}

	player.ETag = string(resp.ETag)
	return player, nil
}

// GetPlayer reads a player document by playerId.
func (r *CosmosRepository) GetPlayer(ctx context.Context, playerID string) (Player, error) {
	pk := azcosmos.NewPartitionKeyString(playerID)
	resp, err := r.playersContainer.ReadItem(ctx, pk, playerID, nil)
	if err != nil {
		if isNotFoundError(err) {
			return Player{}, ErrNotFound
		}
		return Player{}, fmt.Errorf("failed to read player: %w", err)
	}

	var player Player
	if err := json.Unmarshal(resp.Value, &player); err != nil {
		return Player{}, fmt.Errorf("failed to unmarshal player: %w", err)
	}
	player.ETag = string(resp.ETag)
	return player, nil
}

// ReplacePlayer replaces a player document with ETag check.
func (r *CosmosRepository) ReplacePlayer(ctx context.Context, player Player, etag *azcore.ETag) (Player, error) {
	data, err := json.Marshal(player)
	if err != nil {
		return Player{}, fmt.Errorf("failed to marshal player: %w", err)
	}

	pk := azcosmos.NewPartitionKeyString(player.PlayerID)
	opts := &azcosmos.ItemOptions{}
	if etag != nil {
		opts.IfMatchEtag = etag
	}

	resp, err := r.playersContainer.ReplaceItem(ctx, pk, player.ID, data, opts)
	if err != nil {
		if isPreconditionFailedError(err) {
			return Player{}, ErrPreconditionFailed
		}
		if isNotFoundError(err) {
			return Player{}, ErrNotFound
		}
		return Player{}, fmt.Errorf("failed to replace player: %w", err)
	}

	player.ETag = string(resp.ETag)
	return player, nil
}

// DeletePlayer deletes a player document.
func (r *CosmosRepository) DeletePlayer(ctx context.Context, playerID string) error {
	pk := azcosmos.NewPartitionKeyString(playerID)
	_, err := r.playersContainer.DeleteItem(ctx, pk, playerID, nil)
	if err != nil {
		if isNotFoundError(err) {
			return ErrNotFound
		}
		return fmt.Errorf("failed to delete player: %w", err)
	}
	return nil
}

// CreateScore inserts a score document.
func (r *CosmosRepository) CreateScore(ctx context.Context, playerID string, score int, gameMode string) (Score, error) {
	s := Score{
		ID:        uuid.New().String(),
		PlayerID:  playerID,
		Type:      "score",
		Score:     score,
		GameMode:  gameMode,
		Timestamp: time.Now().UTC().Format(time.RFC3339),
	}

	data, err := json.Marshal(s)
	if err != nil {
		return Score{}, fmt.Errorf("failed to marshal score: %w", err)
	}

	pk := azcosmos.NewPartitionKeyString(playerID)
	_, err = r.playersContainer.CreateItem(ctx, pk, data, nil)
	if err != nil {
		return Score{}, fmt.Errorf("failed to create score: %w", err)
	}

	return s, nil
}

// GetScores queries score documents for a player, ordered by timestamp DESC.
func (r *CosmosRepository) GetScores(ctx context.Context, playerID string, limit int) ([]Score, error) {
	query := fmt.Sprintf(
		"SELECT * FROM c WHERE c.playerId = @playerId AND c.type = 'score' ORDER BY c.timestamp DESC OFFSET 0 LIMIT %d",
		limit,
	)

	pk := azcosmos.NewPartitionKeyString(playerID)
	queryOpts := &azcosmos.QueryOptions{
		QueryParameters: []azcosmos.QueryParameter{
			{Name: "@playerId", Value: playerID},
		},
	}

	pager := r.playersContainer.NewQueryItemsPager(query, pk, queryOpts)

	var scores []Score
	for pager.More() {
		page, err := pager.NextPage(ctx)
		if err != nil {
			return nil, fmt.Errorf("failed to query scores: %w", err)
		}
		for _, item := range page.Items {
			var s Score
			if err := json.Unmarshal(item, &s); err != nil {
				return nil, fmt.Errorf("failed to unmarshal score: %w", err)
			}
			scores = append(scores, s)
		}
	}

	return scores, nil
}

// GetAllScoresForStats queries all scores for a player to compute stats.
func (r *CosmosRepository) GetAllScoresForStats(ctx context.Context, playerID string) ([]Score, error) {
	query := "SELECT * FROM c WHERE c.playerId = @playerId AND c.type = 'score'"

	pk := azcosmos.NewPartitionKeyString(playerID)
	queryOpts := &azcosmos.QueryOptions{
		QueryParameters: []azcosmos.QueryParameter{
			{Name: "@playerId", Value: playerID},
		},
	}

	pager := r.playersContainer.NewQueryItemsPager(query, pk, queryOpts)

	var scores []Score
	for pager.More() {
		page, err := pager.NextPage(ctx)
		if err != nil {
			return nil, fmt.Errorf("failed to query scores: %w", err)
		}
		for _, item := range page.Items {
			var s Score
			if err := json.Unmarshal(item, &s); err != nil {
				return nil, fmt.Errorf("failed to unmarshal score: %w", err)
			}
			scores = append(scores, s)
		}
	}

	return scores, nil
}

// DeleteScoresForPlayer deletes all score documents for a player.
func (r *CosmosRepository) DeleteScoresForPlayer(ctx context.Context, playerID string) error {
	scores, err := r.GetAllScoresForStats(ctx, playerID)
	if err != nil {
		return err
	}

	pk := azcosmos.NewPartitionKeyString(playerID)
	for _, s := range scores {
		_, err := r.playersContainer.DeleteItem(ctx, pk, s.ID, nil)
		if err != nil && !isNotFoundError(err) {
			return fmt.Errorf("failed to delete score %s: %w", s.ID, err)
		}
	}

	return nil
}

// UpsertLeaderboardEntry upserts a leaderboard entry.
func (r *CosmosRepository) UpsertLeaderboardEntry(ctx context.Context, entry LeaderboardEntry) error {
	data, err := json.Marshal(entry)
	if err != nil {
		return fmt.Errorf("failed to marshal leaderboard entry: %w", err)
	}

	pk := azcosmos.NewPartitionKeyString(entry.LeaderboardType)
	_, err = r.leaderboardContainer.UpsertItem(ctx, pk, data, nil)
	if err != nil {
		return fmt.Errorf("failed to upsert leaderboard entry: %w", err)
	}

	return nil
}

// DeleteLeaderboardEntry deletes a leaderboard entry.
func (r *CosmosRepository) DeleteLeaderboardEntry(ctx context.Context, leaderboardType, playerID string) error {
	pk := azcosmos.NewPartitionKeyString(leaderboardType)
	_, err := r.leaderboardContainer.DeleteItem(ctx, pk, playerID, nil)
	if err != nil && !isNotFoundError(err) {
		return fmt.Errorf("failed to delete leaderboard entry: %w", err)
	}
	return nil
}

// GetLeaderboard queries leaderboard entries by type, sorted by score DESC then displayName ASC.
func (r *CosmosRepository) GetLeaderboard(ctx context.Context, leaderboardType string, top int) ([]LeaderboardEntry, error) {
	query := fmt.Sprintf(
		"SELECT * FROM c WHERE c.leaderboardType = @lbType ORDER BY c.score DESC, c.displayName ASC OFFSET 0 LIMIT %d",
		top,
	)

	pk := azcosmos.NewPartitionKeyString(leaderboardType)
	queryOpts := &azcosmos.QueryOptions{
		QueryParameters: []azcosmos.QueryParameter{
			{Name: "@lbType", Value: leaderboardType},
		},
	}

	pager := r.leaderboardContainer.NewQueryItemsPager(query, pk, queryOpts)

	var entries []LeaderboardEntry
	for pager.More() {
		page, err := pager.NextPage(ctx)
		if err != nil {
			return nil, fmt.Errorf("failed to query leaderboard: %w", err)
		}
		for _, item := range page.Items {
			var e LeaderboardEntry
			if err := json.Unmarshal(item, &e); err != nil {
				return nil, fmt.Errorf("failed to unmarshal leaderboard entry: %w", err)
			}
			entries = append(entries, e)
		}
	}

	return entries, nil
}

// GetAllLeaderboardEntries gets all entries in the global leaderboard (for rank calculation).
func (r *CosmosRepository) GetAllLeaderboardEntries(ctx context.Context) ([]LeaderboardEntry, error) {
	query := "SELECT * FROM c WHERE c.leaderboardType = @lbType ORDER BY c.score DESC, c.displayName ASC"

	pk := azcosmos.NewPartitionKeyString("global")
	queryOpts := &azcosmos.QueryOptions{
		QueryParameters: []azcosmos.QueryParameter{
			{Name: "@lbType", Value: "global"},
		},
	}

	pager := r.leaderboardContainer.NewQueryItemsPager(query, pk, queryOpts)

	var entries []LeaderboardEntry
	for pager.More() {
		page, err := pager.NextPage(ctx)
		if err != nil {
			return nil, fmt.Errorf("failed to query all leaderboard entries: %w", err)
		}
		for _, item := range page.Items {
			var e LeaderboardEntry
			if err := json.Unmarshal(item, &e); err != nil {
				return nil, fmt.Errorf("failed to unmarshal leaderboard entry: %w", err)
			}
			entries = append(entries, e)
		}
	}

	return entries, nil
}

// DeleteAllLeaderboardEntriesForPlayer deletes global and regional entries for a player.
func (r *CosmosRepository) DeleteAllLeaderboardEntriesForPlayer(ctx context.Context, playerID, region string) error {
	// Delete global entry
	if err := r.DeleteLeaderboardEntry(ctx, "global", playerID); err != nil {
		return err
	}

	// Delete regional entry
	if region != "" {
		if err := r.DeleteLeaderboardEntry(ctx, "regional-"+region, playerID); err != nil {
			return err
		}
	}

	return nil
}

// UpdatePlayerStatsWithRetry performs a read-modify-write on the player with ETag-based optimistic concurrency.
func (r *CosmosRepository) UpdatePlayerStatsWithRetry(ctx context.Context, playerID string, newScore int) error {
	const maxRetries = 10

	for attempt := 0; attempt < maxRetries; attempt++ {
		player, err := r.GetPlayer(ctx, playerID)
		if err != nil {
			return err
		}

		// Fetch all scores to compute accurate stats
		scores, err := r.GetAllScoresForStats(ctx, playerID)
		if err != nil {
			return err
		}

		totalGames := len(scores)
		bestScore := 0
		sum := 0
		for _, s := range scores {
			sum += s.Score
			if s.Score > bestScore {
				bestScore = s.Score
			}
		}

		var avgScore float64
		if totalGames > 0 {
			avgScore = float64(sum) / float64(totalGames)
		}

		player.TotalGames = totalGames
		player.BestScore = bestScore
		player.AverageScore = avgScore

		etag := azcore.ETag(player.ETag)
		_, err = r.ReplacePlayer(ctx, player, &etag)
		if err != nil {
			if errors.Is(err, ErrPreconditionFailed) {
				continue // Retry
			}
			return err
		}

		// Upsert leaderboard entries if this is the best score
		if bestScore > 0 {
			globalEntry := LeaderboardEntry{
				ID:              playerID,
				LeaderboardType: "global",
				PlayerID:        playerID,
				DisplayName:     player.DisplayName,
				Score:           bestScore,
				Region:          player.Region,
			}
			if err := r.UpsertLeaderboardEntry(ctx, globalEntry); err != nil {
				return err
			}

			if player.Region != "" {
				regionalEntry := LeaderboardEntry{
					ID:              playerID,
					LeaderboardType: "regional-" + player.Region,
					PlayerID:        playerID,
					DisplayName:     player.DisplayName,
					Score:           bestScore,
					Region:          player.Region,
				}
				if err := r.UpsertLeaderboardEntry(ctx, regionalEntry); err != nil {
					return err
				}
			}
		}

		return nil
	}

	return fmt.Errorf("failed to update player stats after %d retries", maxRetries)
}
