package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"

	"github.com/Azure/azure-sdk-for-go/sdk/azcore"
	"github.com/Azure/azure-sdk-for-go/sdk/data/azcosmos"
)

// CosmosDB wraps the azcosmos client and container references.
type CosmosDB struct {
	client       *azcosmos.Client
	players      *azcosmos.ContainerClient
	scores       *azcosmos.ContainerClient
	leaderboards *azcosmos.ContainerClient
}

func NewCosmosDB(endpoint, key string) (*CosmosDB, error) {
	cred, err := azcosmos.NewKeyCredential(key)
	if err != nil {
		return nil, fmt.Errorf("create credential: %w", err)
	}

	opts := &azcosmos.ClientOptions{}

	// Custom HTTP transport for emulator TLS (self-signed cert)
	customTransport := &http.Transport{
		TLSClientConfig: tlsConfig(),
	}
	opts.ClientOptions.Transport = &policyHTTPClient{client: &http.Client{Transport: customTransport}}

	client, err := azcosmos.NewClientWithKey(endpoint, cred, opts)
	if err != nil {
		return nil, fmt.Errorf("create client: %w", err)
	}

	return &CosmosDB{client: client}, nil
}

// policyHTTPClient adapts *http.Client to azcore's policy.Transporter interface.
type policyHTTPClient struct {
	client *http.Client
}

func (p *policyHTTPClient) Do(req *http.Request) (*http.Response, error) {
	return p.client.Do(req)
}

// EnsureDatabase creates the database and containers if they don't exist.
func (db *CosmosDB) EnsureDatabase(ctx context.Context) error {
	dbProps := azcosmos.DatabaseProperties{ID: "gaming-leaderboard"}
	_, err := db.client.CreateDatabase(ctx, dbProps, nil)
	if err != nil && !isConflict(err) {
		return fmt.Errorf("create database: %w", err)
	}

	dbClient, err := db.client.NewDatabase("gaming-leaderboard")
	if err != nil {
		return fmt.Errorf("get database: %w", err)
	}

	// Create players container
	if err := db.createContainer(ctx, dbClient, "players", "/playerId", nil); err != nil {
		return err
	}

	// Create scores container
	if err := db.createContainer(ctx, dbClient, "scores", "/playerId", nil); err != nil {
		return err
	}

	// Create leaderboards container with composite index
	compositeIndexes := [][]azcosmos.CompositeIndex{
		{
			{Path: "/score", Order: azcosmos.CompositeIndexDescending},
			{Path: "/displayName", Order: azcosmos.CompositeIndexAscending},
		},
	}
	if err := db.createContainer(ctx, dbClient, "leaderboards", "/leaderboardKey", compositeIndexes); err != nil {
		return err
	}

	// Get container clients
	db.players, err = dbClient.NewContainer("players")
	if err != nil {
		return err
	}
	db.scores, err = dbClient.NewContainer("scores")
	if err != nil {
		return err
	}
	db.leaderboards, err = dbClient.NewContainer("leaderboards")
	if err != nil {
		return err
	}

	return nil
}

func (db *CosmosDB) createContainer(ctx context.Context, dbClient *azcosmos.DatabaseClient, name, partitionKey string, compositeIndexes [][]azcosmos.CompositeIndex) error {
	indexingPolicy := azcosmos.IndexingPolicy{
		Automatic:     true,
		IndexingMode:  azcosmos.IndexingModeConsistent,
		IncludedPaths: []azcosmos.IncludedPath{{Path: "/*"}},
		ExcludedPaths: []azcosmos.ExcludedPath{{Path: "/_etag/?"}},
	}
	if compositeIndexes != nil {
		indexingPolicy.CompositeIndexes = compositeIndexes
	}

	containerProps := azcosmos.ContainerProperties{
		ID: name,
		PartitionKeyDefinition: azcosmos.PartitionKeyDefinition{
			Paths: []string{partitionKey},
		},
		IndexingPolicy: &indexingPolicy,
	}

	maxRU := int32(4000)
	throughput := azcosmos.NewAutoscaleThroughputProperties(maxRU)

	_, err := dbClient.CreateContainer(ctx, containerProps, &azcosmos.CreateContainerOptions{
		ThroughputProperties: &throughput,
	})
	if err != nil && !isConflict(err) {
		return fmt.Errorf("create container %s: %w", name, err)
	}
	return nil
}

// CreatePlayer creates a new player. Returns conflict error if player already exists.
func (db *CosmosDB) CreatePlayer(ctx context.Context, p Player) (Player, error) {
	data, err := json.Marshal(p)
	if err != nil {
		return Player{}, err
	}

	pk := azcosmos.NewPartitionKeyString(p.PlayerID)

	resp, err := db.players.CreateItem(ctx, pk, data, nil)
	if err != nil {
		return Player{}, err
	}

	p.ETag = string(resp.ETag)
	return p, nil
}

// GetPlayer reads a player by playerId using point read.
func (db *CosmosDB) GetPlayer(ctx context.Context, playerID string) (Player, error) {
	pk := azcosmos.NewPartitionKeyString(playerID)
	resp, err := db.players.ReadItem(ctx, pk, playerID, nil)
	if err != nil {
		return Player{}, err
	}

	var p Player
	if err := json.Unmarshal(resp.Value, &p); err != nil {
		return Player{}, err
	}
	p.ETag = string(resp.ETag)
	return p, nil
}

// ReplacePlayer updates a player with ETag-based concurrency control.
func (db *CosmosDB) ReplacePlayer(ctx context.Context, p Player, etag string) (Player, error) {
	data, err := json.Marshal(p)
	if err != nil {
		return Player{}, err
	}

	pk := azcosmos.NewPartitionKeyString(p.PlayerID)
	opts := &azcosmos.ItemOptions{}
	etagVal := azcore.ETag(etag)
	opts.IfMatchEtag = &etagVal

	resp, err := db.players.ReplaceItem(ctx, pk, p.ID, data, opts)
	if err != nil {
		return Player{}, err
	}

	p.ETag = string(resp.ETag)
	return p, nil
}

// DeletePlayer deletes a player document.
func (db *CosmosDB) DeletePlayer(ctx context.Context, playerID string) error {
	pk := azcosmos.NewPartitionKeyString(playerID)
	_, err := db.players.DeleteItem(ctx, pk, playerID, nil)
	return err
}

// CreateScore creates a score document.
func (db *CosmosDB) CreateScore(ctx context.Context, s Score) error {
	data, err := json.Marshal(s)
	if err != nil {
		return err
	}
	pk := azcosmos.NewPartitionKeyString(s.PlayerID)
	_, err = db.scores.CreateItem(ctx, pk, data, nil)
	return err
}

// GetScores queries scores for a player ordered by timestamp desc.
func (db *CosmosDB) GetScores(ctx context.Context, playerID string, limit int) ([]Score, error) {
	pk := azcosmos.NewPartitionKeyString(playerID)
	query := "SELECT * FROM c WHERE c.playerId = @playerId ORDER BY c.timestamp DESC OFFSET 0 LIMIT @limit"
	params := []azcosmos.QueryParameter{
		{Name: "@playerId", Value: playerID},
		{Name: "@limit", Value: limit},
	}

	pager := db.scores.NewQueryItemsPager(query, pk, &azcosmos.QueryOptions{
		QueryParameters: params,
	})

	var scores []Score
	for pager.More() {
		resp, err := pager.NextPage(ctx)
		if err != nil {
			return nil, err
		}
		for _, item := range resp.Items {
			var s Score
			if err := json.Unmarshal(item, &s); err != nil {
				return nil, err
			}
			scores = append(scores, s)
		}
	}
	return scores, nil
}

// UpsertLeaderboardEntry upserts a leaderboard entry.
func (db *CosmosDB) UpsertLeaderboardEntry(ctx context.Context, entry LeaderboardEntry) error {
	data, err := json.Marshal(entry)
	if err != nil {
		return err
	}
	pk := azcosmos.NewPartitionKeyString(entry.LeaderboardKey)
	_, err = db.leaderboards.UpsertItem(ctx, pk, data, nil)
	return err
}

// DeleteLeaderboardEntry deletes a leaderboard entry.
func (db *CosmosDB) DeleteLeaderboardEntry(ctx context.Context, leaderboardKey, id string) error {
	pk := azcosmos.NewPartitionKeyString(leaderboardKey)
	_, err := db.leaderboards.DeleteItem(ctx, pk, id, nil)
	return err
}

// GetLeaderboard queries leaderboard entries ordered by score desc, displayName asc.
func (db *CosmosDB) GetLeaderboard(ctx context.Context, leaderboardKey string, top int) ([]LeaderboardEntry, error) {
	pk := azcosmos.NewPartitionKeyString(leaderboardKey)
	query := "SELECT * FROM c WHERE c.leaderboardKey = @key ORDER BY c.score DESC, c.displayName ASC OFFSET 0 LIMIT @top"
	params := []azcosmos.QueryParameter{
		{Name: "@key", Value: leaderboardKey},
		{Name: "@top", Value: top},
	}

	pager := db.leaderboards.NewQueryItemsPager(query, pk, &azcosmos.QueryOptions{
		QueryParameters: params,
	})

	var entries []LeaderboardEntry
	for pager.More() {
		resp, err := pager.NextPage(ctx)
		if err != nil {
			return nil, err
		}
		for _, item := range resp.Items {
			var e LeaderboardEntry
			if err := json.Unmarshal(item, &e); err != nil {
				return nil, err
			}
			entries = append(entries, e)
		}
	}
	return entries, nil
}

// GetLeaderboardEntry gets a single leaderboard entry by key and player ID.
func (db *CosmosDB) GetLeaderboardEntry(ctx context.Context, leaderboardKey, playerID string) (LeaderboardEntry, error) {
	pk := azcosmos.NewPartitionKeyString(leaderboardKey)
	resp, err := db.leaderboards.ReadItem(ctx, pk, playerID, nil)
	if err != nil {
		return LeaderboardEntry{}, err
	}
	var entry LeaderboardEntry
	if err := json.Unmarshal(resp.Value, &entry); err != nil {
		return LeaderboardEntry{}, err
	}
	return entry, nil
}

// CountPlayersAbove counts players with score > given score in a leaderboard.
func (db *CosmosDB) CountPlayersAbove(ctx context.Context, leaderboardKey string, score int) (int, error) {
	pk := azcosmos.NewPartitionKeyString(leaderboardKey)
	query := "SELECT VALUE COUNT(1) FROM c WHERE c.leaderboardKey = @key AND c.score > @score"
	params := []azcosmos.QueryParameter{
		{Name: "@key", Value: leaderboardKey},
		{Name: "@score", Value: score},
	}

	pager := db.leaderboards.NewQueryItemsPager(query, pk, &azcosmos.QueryOptions{
		QueryParameters: params,
	})

	for pager.More() {
		resp, err := pager.NextPage(ctx)
		if err != nil {
			return 0, err
		}
		if len(resp.Items) > 0 {
			var count int
			if err := json.Unmarshal(resp.Items[0], &count); err != nil {
				return 0, err
			}
			return count, nil
		}
	}
	return 0, nil
}

// CountPlayersWithSameScoreAndLowerName counts players with same score but displayName < given name.
func (db *CosmosDB) CountPlayersWithSameScoreAndLowerName(ctx context.Context, leaderboardKey string, score int, displayName string) (int, error) {
	pk := azcosmos.NewPartitionKeyString(leaderboardKey)
	query := "SELECT VALUE COUNT(1) FROM c WHERE c.leaderboardKey = @key AND c.score = @score AND c.displayName < @name"
	params := []azcosmos.QueryParameter{
		{Name: "@key", Value: leaderboardKey},
		{Name: "@score", Value: score},
		{Name: "@name", Value: displayName},
	}

	pager := db.leaderboards.NewQueryItemsPager(query, pk, &azcosmos.QueryOptions{
		QueryParameters: params,
	})

	for pager.More() {
		resp, err := pager.NextPage(ctx)
		if err != nil {
			return 0, err
		}
		if len(resp.Items) > 0 {
			var count int
			if err := json.Unmarshal(resp.Items[0], &count); err != nil {
				return 0, err
			}
			return count, nil
		}
	}
	return 0, nil
}

// DeleteScoresForPlayer deletes all scores for a given player.
func (db *CosmosDB) DeleteScoresForPlayer(ctx context.Context, playerID string) error {
	scores, err := db.GetScores(ctx, playerID, 10000)
	if err != nil {
		return err
	}
	pk := azcosmos.NewPartitionKeyString(playerID)
	for _, s := range scores {
		if _, err := db.scores.DeleteItem(ctx, pk, s.ID, nil); err != nil {
			return err
		}
	}
	return nil
}

// GetNeighbors gets leaderboard entries around a given rank.
func (db *CosmosDB) GetNeighbors(ctx context.Context, leaderboardKey string, rank, window int) ([]LeaderboardEntry, error) {
	all, err := db.GetLeaderboard(ctx, leaderboardKey, 10000)
	if err != nil {
		return nil, err
	}

	start := rank - 1 - window
	if start < 0 {
		start = 0
	}
	end := rank - 1 + window + 1
	if end > len(all) {
		end = len(all)
	}

	return all[start:end], nil
}

func isConflict(err error) bool {
	var respErr *azcore.ResponseError
	if errors.As(err, &respErr) {
		return respErr.StatusCode == http.StatusConflict
	}
	return false
}

func isNotFound(err error) bool {
	var respErr *azcore.ResponseError
	if errors.As(err, &respErr) {
		return respErr.StatusCode == http.StatusNotFound
	}
	return false
}

func isPreconditionFailed(err error) bool {
	var respErr *azcore.ResponseError
	if errors.As(err, &respErr) {
		return respErr.StatusCode == http.StatusPreconditionFailed
	}
	return false
}

func leaderboardKeyForRegion(region string) string {
	return "regional_" + strings.TrimSpace(region)
}
