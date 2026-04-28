package main

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"log"
	"math"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/Azure/azure-sdk-for-go/sdk/azcore"
	"github.com/Azure/azure-sdk-for-go/sdk/data/azcosmos"
	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
)

// ──────────────────────────────────────────────────────────────────
// Document models  (Best-practice: model-type-discriminator, model-schema-versioning)
// ──────────────────────────────────────────────────────────────────

// PlayerDoc represents a player document in the players container.
type PlayerDoc struct {
	ID            string  `json:"id"`
	PlayerId      string  `json:"playerId"`
	DisplayName   string  `json:"displayName"`
	Region        string  `json:"region"`
	TotalGames    int     `json:"totalGames"`
	BestScore     int     `json:"bestScore"`
	AverageScore  float64 `json:"averageScore"`
	TotalScore    int64   `json:"totalScore"`
	Type          string  `json:"type"`
	SchemaVersion string  `json:"schemaVersion"`
	ETag          string  `json:"_etag,omitempty"`
}

// ScoreDoc represents a score document in the scores container.
type ScoreDoc struct {
	ID            string `json:"id"`
	ScoreId       string `json:"scoreId"`
	PlayerId      string `json:"playerId"`
	Score         int    `json:"score"`
	GameMode      string `json:"gameMode,omitempty"`
	Timestamp     string `json:"timestamp"`
	Type          string `json:"type"`
	SchemaVersion string `json:"schemaVersion"`
}

// LeaderboardDoc represents a leaderboard entry in the leaderboards container.
// Best-practice: model-denormalize-reads, partition-synthetic-keys
type LeaderboardDoc struct {
	ID             string `json:"id"`
	PlayerId       string `json:"playerId"`
	DisplayName    string `json:"displayName"`
	Region         string `json:"region"`
	Score          int    `json:"score"`
	LeaderboardKey string `json:"leaderboardKey"`
	Type           string `json:"type"`
	SchemaVersion  string `json:"schemaVersion"`
}

// API response models

type PlayerResponse struct {
	PlayerId     string  `json:"playerId"`
	DisplayName  string  `json:"displayName"`
	Region       string  `json:"region"`
	TotalGames   int     `json:"totalGames"`
	BestScore    int     `json:"bestScore"`
	AverageScore float64 `json:"averageScore"`
}

type ScoreResponse struct {
	ScoreId  string `json:"scoreId"`
	PlayerId string `json:"playerId"`
	Score    int    `json:"score"`
}

type ScoreHistoryResponse struct {
	ScoreId   string `json:"scoreId"`
	PlayerId  string `json:"playerId"`
	Score     int    `json:"score"`
	GameMode  string `json:"gameMode,omitempty"`
	Timestamp string `json:"timestamp"`
}

type LeaderboardEntry struct {
	Rank        int    `json:"rank"`
	PlayerId    string `json:"playerId"`
	DisplayName string `json:"displayName"`
	Score       int    `json:"score"`
}

type PlayerRankResponse struct {
	PlayerId  string             `json:"playerId"`
	Rank      int                `json:"rank"`
	Score     int                `json:"score"`
	Neighbors []LeaderboardEntry `json:"neighbors"`
}

// ──────────────────────────────────────────────────────────────────
// Cosmos DB setup
// ──────────────────────────────────────────────────────────────────

var (
	cosmosClient          *azcosmos.Client
	playersContainer      *azcosmos.ContainerClient
	scoresContainer       *azcosmos.ContainerClient
	leaderboardsContainer *azcosmos.ContainerClient
	dbName                = "gaming-leaderboard"
)

const (
	playersContainerName      = "players"
	scoresContainerName       = "scores"
	leaderboardsContainerName = "leaderboards"
)

func initCosmos() error {
	endpoint := os.Getenv("COSMOS_ENDPOINT")
	if endpoint == "" {
		endpoint = "https://localhost:8081"
	}
	key := os.Getenv("COSMOS_KEY")
	if key == "" {
		key = "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw=="
	}

	// Trust self-signed certs for emulator
	http.DefaultTransport = &http.Transport{
		TLSClientConfig: &tls.Config{InsecureSkipVerify: true},
	}

	cred, err := azcosmos.NewKeyCredential(key)
	if err != nil {
		return fmt.Errorf("failed to create credential: %w", err)
	}

	client, err := azcosmos.NewClientWithKey(endpoint, cred, nil)
	if err != nil {
		return fmt.Errorf("failed to create cosmos client: %w", err)
	}
	cosmosClient = client

	ctx := context.Background()

	// Create database
	dbProps := azcosmos.DatabaseProperties{ID: dbName}
	_, err = cosmosClient.CreateDatabase(ctx, dbProps, nil)
	if err != nil && !isConflict(err) {
		return fmt.Errorf("failed to create database: %w", err)
	}

	dbClient, err := cosmosClient.NewDatabase(dbName)
	if err != nil {
		return fmt.Errorf("failed to get database client: %w", err)
	}

	// Create players container (partition key: /playerId)
	// Best-practice: partition-high-cardinality, partition-query-patterns
	if err := createContainer(ctx, dbClient, playersContainerName, "/playerId",
		nil,
		[]string{"/displayName/?", "/totalScore/?"},
	); err != nil {
		return err
	}

	// Create scores container (partition key: /playerId)
	if err := createContainer(ctx, dbClient, scoresContainerName, "/playerId",
		[][]azcosmos.CompositeIndex{
			{
				{Path: "/timestamp", Order: azcosmos.CompositeIndexDescending},
				{Path: "/score", Order: azcosmos.CompositeIndexDescending},
			},
		},
		[]string{"/gameMode/?"},
	); err != nil {
		return err
	}

	// Create leaderboards container (partition key: /leaderboardKey)
	// Best-practice: partition-synthetic-keys for efficient top-N queries
	if err := createContainer(ctx, dbClient, leaderboardsContainerName, "/leaderboardKey",
		[][]azcosmos.CompositeIndex{
			{
				{Path: "/score", Order: azcosmos.CompositeIndexDescending},
				{Path: "/displayName", Order: azcosmos.CompositeIndexAscending},
			},
		},
		[]string{},
	); err != nil {
		return err
	}

	playersContainer, _ = cosmosClient.NewContainer(dbName, playersContainerName)
	scoresContainer, _ = cosmosClient.NewContainer(dbName, scoresContainerName)
	leaderboardsContainer, _ = cosmosClient.NewContainer(dbName, leaderboardsContainerName)

	return nil
}

func createContainer(ctx context.Context, db *azcosmos.DatabaseClient, name, pkPath string,
	compositeIdx [][]azcosmos.CompositeIndex, excludePaths []string) error {

	// Best-practice: index-exclude-unused, index-composite
	indexingPolicy := azcosmos.IndexingPolicy{
		Automatic:     true,
		IncludedPaths: []azcosmos.IncludedPath{{Path: "/*"}},
		ExcludedPaths: []azcosmos.ExcludedPath{
			{Path: "/_etag/?"},
		},
	}

	// Add custom excluded paths
	for _, p := range excludePaths {
		indexingPolicy.ExcludedPaths = append(indexingPolicy.ExcludedPaths, azcosmos.ExcludedPath{Path: p})
	}

	if len(compositeIdx) > 0 {
		indexingPolicy.CompositeIndexes = compositeIdx
	}

	props := azcosmos.ContainerProperties{
		ID: name,
		PartitionKeyDefinition: azcosmos.PartitionKeyDefinition{
			Paths: []string{pkPath},
		},
		IndexingPolicy: &indexingPolicy,
	}

	// Best-practice: throughput-autoscale
	throughput := azcosmos.NewAutoscaleThroughputProperties(1000)
	opts := &azcosmos.CreateContainerOptions{ThroughputProperties: &throughput}

	_, err := db.CreateContainer(ctx, props, opts)
	if err != nil && !isConflict(err) {
		return fmt.Errorf("failed to create container %s: %w", name, err)
	}
	return nil
}

func isConflict(err error) bool {
	if err == nil {
		return false
	}
	return strings.Contains(err.Error(), "409") || strings.Contains(err.Error(), "Conflict")
}

// ──────────────────────────────────────────────────────────────────
// Helpers
// ──────────────────────────────────────────────────────────────────

func toJSON(v interface{}) []byte {
	b, _ := json.Marshal(v)
	return b
}

func playerDocToResponse(p *PlayerDoc) PlayerResponse {
	return PlayerResponse{
		PlayerId:     p.PlayerId,
		DisplayName:  p.DisplayName,
		Region:       p.Region,
		TotalGames:   p.TotalGames,
		BestScore:    p.BestScore,
		AverageScore: p.AverageScore,
	}
}

// ──────────────────────────────────────────────────────────────────
// Player handlers
// ──────────────────────────────────────────────────────────────────

func createPlayer(c *gin.Context) {
	var req struct {
		PlayerId    string `json:"playerId"`
		DisplayName string `json:"displayName"`
		Region      string `json:"region"`
	}

	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	if req.PlayerId == "" || req.DisplayName == "" || req.Region == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "playerId, displayName, and region are required"})
		return
	}

	doc := PlayerDoc{
		ID:            req.PlayerId,
		PlayerId:      req.PlayerId,
		DisplayName:   req.DisplayName,
		Region:        req.Region,
		TotalGames:    0,
		BestScore:     0,
		AverageScore:  0,
		TotalScore:    0,
		Type:          "player",
		SchemaVersion: "1.0",
	}

	pk := azcosmos.NewPartitionKeyString(req.PlayerId)
	_, err := playersContainer.CreateItem(context.Background(), pk, toJSON(doc), nil)
	if err != nil {
		if strings.Contains(err.Error(), "409") || strings.Contains(err.Error(), "Conflict") {
			c.JSON(http.StatusConflict, gin.H{"error": "player already exists"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusCreated, playerDocToResponse(&doc))
}

func getPlayer(c *gin.Context) {
	playerId := c.Param("playerId")
	pk := azcosmos.NewPartitionKeyString(playerId)

	resp, err := playersContainer.ReadItem(context.Background(), pk, playerId, nil)
	if err != nil {
		if strings.Contains(err.Error(), "404") || strings.Contains(err.Error(), "NotFound") {
			c.JSON(http.StatusNotFound, gin.H{"error": "player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	var doc PlayerDoc
	if err := json.Unmarshal(resp.Value, &doc); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to parse player"})
		return
	}

	c.JSON(http.StatusOK, playerDocToResponse(&doc))
}

func updatePlayer(c *gin.Context) {
	playerId := c.Param("playerId")
	pk := azcosmos.NewPartitionKeyString(playerId)

	var req struct {
		DisplayName *string `json:"displayName"`
		Region      *string `json:"region"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	// Read current player
	resp, err := playersContainer.ReadItem(context.Background(), pk, playerId, nil)
	if err != nil {
		if strings.Contains(err.Error(), "404") || strings.Contains(err.Error(), "NotFound") {
			c.JSON(http.StatusNotFound, gin.H{"error": "player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	var doc PlayerDoc
	if err := json.Unmarshal(resp.Value, &doc); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to parse player"})
		return
	}

	oldRegion := doc.Region

	if req.DisplayName != nil {
		doc.DisplayName = *req.DisplayName
	}
	if req.Region != nil {
		doc.Region = *req.Region
	}

	// Replace player document
	_, err = playersContainer.ReplaceItem(context.Background(), pk, playerId, toJSON(doc), nil)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// Update leaderboard entries if displayName or region changed
	updateLeaderboardEntries(context.Background(), playerId, doc.DisplayName, doc.Region, doc.BestScore, oldRegion)

	c.JSON(http.StatusOK, playerDocToResponse(&doc))
}

func deletePlayer(c *gin.Context) {
	playerId := c.Param("playerId")
	pk := azcosmos.NewPartitionKeyString(playerId)

	// Read player first to get region info for leaderboard cleanup
	resp, err := playersContainer.ReadItem(context.Background(), pk, playerId, nil)
	if err != nil {
		if strings.Contains(err.Error(), "404") || strings.Contains(err.Error(), "NotFound") {
			c.JSON(http.StatusNotFound, gin.H{"error": "player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	var doc PlayerDoc
	if err := json.Unmarshal(resp.Value, &doc); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to parse player"})
		return
	}

	// Delete player
	_, err = playersContainer.DeleteItem(context.Background(), pk, playerId, nil)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// Delete all scores for this player
	deletePlayerScores(context.Background(), playerId)

	// Delete leaderboard entries using the known region
	deletePlayerLeaderboardEntries(context.Background(), playerId, doc.Region)

	c.Status(http.StatusNoContent)
}

// ──────────────────────────────────────────────────────────────────
// Score handlers
// ──────────────────────────────────────────────────────────────────

func submitScore(c *gin.Context) {
	var req struct {
		PlayerId string  `json:"playerId"`
		Score    *int    `json:"score"`
		GameMode *string `json:"gameMode"`
	}

	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	if req.PlayerId == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "playerId is required"})
		return
	}
	if req.Score == nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "score is required"})
		return
	}
	if *req.Score < 0 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "score must be a positive integer"})
		return
	}

	ctx := context.Background()
	pk := azcosmos.NewPartitionKeyString(req.PlayerId)

	// Check player exists
	playerResp, err := playersContainer.ReadItem(ctx, pk, req.PlayerId, nil)
	if err != nil {
		if strings.Contains(err.Error(), "404") || strings.Contains(err.Error(), "NotFound") {
			c.JSON(http.StatusBadRequest, gin.H{"error": "player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// Create score document
	scoreId := uuid.New().String()
	gameMode := ""
	if req.GameMode != nil {
		gameMode = *req.GameMode
	}

	scoreDoc := ScoreDoc{
		ID:            scoreId,
		ScoreId:       scoreId,
		PlayerId:      req.PlayerId,
		Score:         *req.Score,
		GameMode:      gameMode,
		Timestamp:     time.Now().UTC().Format(time.RFC3339),
		Type:          "score",
		SchemaVersion: "1.0",
	}

	_, err = scoresContainer.CreateItem(ctx, pk, toJSON(scoreDoc), nil)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// Update player stats with ETag-based optimistic concurrency
	// Best-practice: sdk-etag-concurrency
	if err := updatePlayerStatsWithRetry(ctx, req.PlayerId, *req.Score, playerResp); err != nil {
		log.Printf("Warning: failed to update player stats: %v", err)
	}

	c.JSON(http.StatusCreated, ScoreResponse{
		ScoreId:  scoreId,
		PlayerId: req.PlayerId,
		Score:    *req.Score,
	})
}

// updatePlayerStatsWithRetry uses ETag-based optimistic concurrency to update player stats.
// This prevents lost updates during concurrent score submissions.
func updatePlayerStatsWithRetry(ctx context.Context, playerId string, newScore int, initialResp azcosmos.ItemResponse) error {
	pk := azcosmos.NewPartitionKeyString(playerId)
	maxRetries := 20
	currentValue := initialResp.Value
	currentETag := initialResp.ETag

	for attempt := 0; attempt < maxRetries; attempt++ {
		var doc PlayerDoc
		if err := json.Unmarshal(currentValue, &doc); err != nil {
			return fmt.Errorf("failed to unmarshal player: %w", err)
		}

		doc.TotalGames++
		doc.TotalScore += int64(newScore)
		if newScore > doc.BestScore {
			doc.BestScore = newScore
		}
		doc.AverageScore = math.Round(float64(doc.TotalScore)/float64(doc.TotalGames)*100) / 100

		etag := azcore.ETag(currentETag)
		opts := &azcosmos.ItemOptions{
			IfMatchEtag: &etag,
		}

		_, err := playersContainer.ReplaceItem(ctx, pk, playerId, toJSON(doc), opts)
		if err == nil {
			// Also update leaderboard entry
			updateLeaderboardEntry(ctx, playerId, doc.DisplayName, doc.Region, doc.BestScore)
			return nil
		}

		if !strings.Contains(err.Error(), "412") && !strings.Contains(err.Error(), "PreconditionFailed") {
			return fmt.Errorf("failed to update player: %w", err)
		}

		// ETag mismatch — re-read and retry
		readResp, readErr := playersContainer.ReadItem(ctx, pk, playerId, nil)
		if readErr != nil {
			return fmt.Errorf("failed to re-read player: %w", readErr)
		}
		currentValue = readResp.Value
		currentETag = readResp.ETag
	}

	return fmt.Errorf("failed to update player stats after %d retries", maxRetries)
}

func getPlayerScores(c *gin.Context) {
	playerId := c.Param("playerId")
	pk := azcosmos.NewPartitionKeyString(playerId)
	ctx := context.Background()

	// Check player exists
	_, err := playersContainer.ReadItem(ctx, pk, playerId, nil)
	if err != nil {
		if strings.Contains(err.Error(), "404") || strings.Contains(err.Error(), "NotFound") {
			c.JSON(http.StatusNotFound, gin.H{"error": "player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	limitStr := c.DefaultQuery("limit", "10")
	limit, _ := strconv.Atoi(limitStr)
	if limit <= 0 {
		limit = 10
	}
	if limit > 100 {
		limit = 100
	}

	query := fmt.Sprintf("SELECT * FROM c WHERE c.playerId = @playerId AND c.type = 'score' ORDER BY c.timestamp DESC OFFSET 0 LIMIT %d", limit)
	queryOpts := azcosmos.QueryOptions{
		QueryParameters: []azcosmos.QueryParameter{
			{Name: "@playerId", Value: playerId},
		},
	}

	pager := scoresContainer.NewQueryItemsPager(query, pk, &queryOpts)
	var scores []ScoreHistoryResponse

	for pager.More() {
		resp, err := pager.NextPage(ctx)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
			return
		}
		for _, item := range resp.Items {
			var doc ScoreDoc
			if err := json.Unmarshal(item, &doc); err != nil {
				continue
			}
			scores = append(scores, ScoreHistoryResponse{
				ScoreId:   doc.ScoreId,
				PlayerId:  doc.PlayerId,
				Score:     doc.Score,
				GameMode:  doc.GameMode,
				Timestamp: doc.Timestamp,
			})
		}
	}

	if scores == nil {
		scores = []ScoreHistoryResponse{}
	}
	c.JSON(http.StatusOK, scores)
}

// ──────────────────────────────────────────────────────────────────
// Leaderboard handlers
// ──────────────────────────────────────────────────────────────────

func getGlobalLeaderboard(c *gin.Context) {
	topStr := c.DefaultQuery("top", "100")
	top, _ := strconv.Atoi(topStr)
	if top <= 0 {
		top = 100
	}
	if top > 100 {
		top = 100
	}

	ctx := context.Background()
	lbKey := "global"
	pk := azcosmos.NewPartitionKeyString(lbKey)

	query := "SELECT * FROM c WHERE c.leaderboardKey = @lbKey AND c.type = 'leaderboardEntry' ORDER BY c.score DESC, c.displayName ASC"
	queryOpts := azcosmos.QueryOptions{
		QueryParameters: []azcosmos.QueryParameter{
			{Name: "@lbKey", Value: lbKey},
		},
	}

	pager := leaderboardsContainer.NewQueryItemsPager(query, pk, &queryOpts)
	var entries []LeaderboardEntry
	rank := 1

	for pager.More() {
		resp, err := pager.NextPage(ctx)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
			return
		}
		for _, item := range resp.Items {
			if rank > top {
				break
			}
			var doc LeaderboardDoc
			if err := json.Unmarshal(item, &doc); err != nil {
				continue
			}
			entries = append(entries, LeaderboardEntry{
				Rank:        rank,
				PlayerId:    doc.PlayerId,
				DisplayName: doc.DisplayName,
				Score:       doc.Score,
			})
			rank++
		}
		if rank > top {
			break
		}
	}

	if entries == nil {
		entries = []LeaderboardEntry{}
	}
	c.JSON(http.StatusOK, entries)
}

func getRegionalLeaderboard(c *gin.Context) {
	region := c.Param("region")
	topStr := c.DefaultQuery("top", "100")
	top, _ := strconv.Atoi(topStr)
	if top <= 0 {
		top = 100
	}
	if top > 100 {
		top = 100
	}

	ctx := context.Background()
	lbKey := fmt.Sprintf("regional_%s", region)
	pk := azcosmos.NewPartitionKeyString(lbKey)

	query := "SELECT * FROM c WHERE c.leaderboardKey = @lbKey AND c.type = 'leaderboardEntry' ORDER BY c.score DESC, c.displayName ASC"
	queryOpts := azcosmos.QueryOptions{
		QueryParameters: []azcosmos.QueryParameter{
			{Name: "@lbKey", Value: lbKey},
		},
	}

	pager := leaderboardsContainer.NewQueryItemsPager(query, pk, &queryOpts)
	var entries []LeaderboardEntry
	rank := 1

	for pager.More() {
		resp, err := pager.NextPage(ctx)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
			return
		}
		for _, item := range resp.Items {
			if rank > top {
				break
			}
			var doc LeaderboardDoc
			if err := json.Unmarshal(item, &doc); err != nil {
				continue
			}
			entries = append(entries, LeaderboardEntry{
				Rank:        rank,
				PlayerId:    doc.PlayerId,
				DisplayName: doc.DisplayName,
				Score:       doc.Score,
			})
			rank++
		}
		if rank > top {
			break
		}
	}

	if entries == nil {
		entries = []LeaderboardEntry{}
	}
	c.JSON(http.StatusOK, entries)
}

func getPlayerRank(c *gin.Context) {
	playerId := c.Param("playerId")
	ctx := context.Background()

	// Check player exists
	pk := azcosmos.NewPartitionKeyString(playerId)
	resp, err := playersContainer.ReadItem(ctx, pk, playerId, nil)
	if err != nil {
		if strings.Contains(err.Error(), "404") || strings.Contains(err.Error(), "NotFound") {
			c.JSON(http.StatusNotFound, gin.H{"error": "player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	var playerDoc PlayerDoc
	if err := json.Unmarshal(resp.Value, &playerDoc); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to parse player"})
		return
	}

	if playerDoc.BestScore == 0 && playerDoc.TotalGames == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "player has no scores"})
		return
	}

	// Get all global leaderboard entries to calculate rank
	lbKey := "global"
	lbPk := azcosmos.NewPartitionKeyString(lbKey)
	query := "SELECT * FROM c WHERE c.leaderboardKey = @lbKey AND c.type = 'leaderboardEntry' ORDER BY c.score DESC, c.displayName ASC"
	queryOpts := azcosmos.QueryOptions{
		QueryParameters: []azcosmos.QueryParameter{
			{Name: "@lbKey", Value: lbKey},
		},
	}

	pager := leaderboardsContainer.NewQueryItemsPager(query, lbPk, &queryOpts)
	var allEntries []LeaderboardEntry
	rank := 1

	for pager.More() {
		page, err := pager.NextPage(ctx)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
			return
		}
		for _, item := range page.Items {
			var doc LeaderboardDoc
			if err := json.Unmarshal(item, &doc); err != nil {
				continue
			}
			allEntries = append(allEntries, LeaderboardEntry{
				Rank:        rank,
				PlayerId:    doc.PlayerId,
				DisplayName: doc.DisplayName,
				Score:       doc.Score,
			})
			rank++
		}
	}

	// Find player's rank
	playerRank := -1
	playerScore := 0
	for _, e := range allEntries {
		if e.PlayerId == playerId {
			playerRank = e.Rank
			playerScore = e.Score
			break
		}
	}

	if playerRank == -1 {
		c.JSON(http.StatusNotFound, gin.H{"error": "player not found in leaderboard"})
		return
	}

	// Get neighbors (±10 positions)
	var neighbors []LeaderboardEntry
	for _, e := range allEntries {
		if e.Rank >= playerRank-10 && e.Rank <= playerRank+10 && e.PlayerId != playerId {
			neighbors = append(neighbors, e)
		}
	}

	if neighbors == nil {
		neighbors = []LeaderboardEntry{}
	}

	c.JSON(http.StatusOK, PlayerRankResponse{
		PlayerId:  playerId,
		Rank:      playerRank,
		Score:     playerScore,
		Neighbors: neighbors,
	})
}

// ──────────────────────────────────────────────────────────────────
// Leaderboard maintenance
// ──────────────────────────────────────────────────────────────────

func updateLeaderboardEntry(ctx context.Context, playerId, displayName, region string, bestScore int) {
	if bestScore <= 0 {
		return
	}

	// Update global leaderboard entry
	globalKey := "global"
	globalDocId := fmt.Sprintf("global_%s", playerId)
	globalDoc := LeaderboardDoc{
		ID:             globalDocId,
		PlayerId:       playerId,
		DisplayName:    displayName,
		Region:         region,
		Score:          bestScore,
		LeaderboardKey: globalKey,
		Type:           "leaderboardEntry",
		SchemaVersion:  "1.0",
	}

	globalPk := azcosmos.NewPartitionKeyString(globalKey)
	_, err := leaderboardsContainer.UpsertItem(ctx, globalPk, toJSON(globalDoc), nil)
	if err != nil {
		log.Printf("Warning: failed to upsert global leaderboard entry: %v", err)
	}

	// Update regional leaderboard entry
	regionalKey := fmt.Sprintf("regional_%s", region)
	regionalDocId := fmt.Sprintf("regional_%s_%s", region, playerId)
	regionalDoc := LeaderboardDoc{
		ID:             regionalDocId,
		PlayerId:       playerId,
		DisplayName:    displayName,
		Region:         region,
		Score:          bestScore,
		LeaderboardKey: regionalKey,
		Type:           "leaderboardEntry",
		SchemaVersion:  "1.0",
	}

	regionalPk := azcosmos.NewPartitionKeyString(regionalKey)
	_, err = leaderboardsContainer.UpsertItem(ctx, regionalPk, toJSON(regionalDoc), nil)
	if err != nil {
		log.Printf("Warning: failed to upsert regional leaderboard entry: %v", err)
	}
}

func updateLeaderboardEntries(ctx context.Context, playerId, displayName, newRegion string, bestScore int, oldRegion string) {
	// Update global leaderboard entry with new display name
	globalKey := "global"
	globalDocId := fmt.Sprintf("global_%s", playerId)
	globalDoc := LeaderboardDoc{
		ID:             globalDocId,
		PlayerId:       playerId,
		DisplayName:    displayName,
		Region:         newRegion,
		Score:          bestScore,
		LeaderboardKey: globalKey,
		Type:           "leaderboardEntry",
		SchemaVersion:  "1.0",
	}

	globalPk := azcosmos.NewPartitionKeyString(globalKey)
	if bestScore > 0 {
		_, err := leaderboardsContainer.UpsertItem(ctx, globalPk, toJSON(globalDoc), nil)
		if err != nil {
			log.Printf("Warning: failed to update global leaderboard entry: %v", err)
		}
	}

	// If region changed, remove from old regional leaderboard and add to new
	if oldRegion != newRegion {
		// Delete old regional entry
		oldRegionalKey := fmt.Sprintf("regional_%s", oldRegion)
		oldRegionalDocId := fmt.Sprintf("regional_%s_%s", oldRegion, playerId)
		oldRegionalPk := azcosmos.NewPartitionKeyString(oldRegionalKey)
		_, _ = leaderboardsContainer.DeleteItem(ctx, oldRegionalPk, oldRegionalDocId, nil)
	}

	// Upsert new regional entry
	if bestScore > 0 {
		regionalKey := fmt.Sprintf("regional_%s", newRegion)
		regionalDocId := fmt.Sprintf("regional_%s_%s", newRegion, playerId)
		regionalDoc := LeaderboardDoc{
			ID:             regionalDocId,
			PlayerId:       playerId,
			DisplayName:    displayName,
			Region:         newRegion,
			Score:          bestScore,
			LeaderboardKey: regionalKey,
			Type:           "leaderboardEntry",
			SchemaVersion:  "1.0",
		}
		regionalPk := azcosmos.NewPartitionKeyString(regionalKey)
		_, err := leaderboardsContainer.UpsertItem(ctx, regionalPk, toJSON(regionalDoc), nil)
		if err != nil {
			log.Printf("Warning: failed to upsert regional leaderboard entry: %v", err)
		}
	}
}

func deletePlayerScores(ctx context.Context, playerId string) {
	pk := azcosmos.NewPartitionKeyString(playerId)
	query := "SELECT c.id FROM c WHERE c.playerId = @playerId AND c.type = 'score'"
	queryOpts := azcosmos.QueryOptions{
		QueryParameters: []azcosmos.QueryParameter{
			{Name: "@playerId", Value: playerId},
		},
	}

	pager := scoresContainer.NewQueryItemsPager(query, pk, &queryOpts)
	for pager.More() {
		resp, err := pager.NextPage(ctx)
		if err != nil {
			log.Printf("Warning: failed to query scores for deletion: %v", err)
			return
		}
		for _, item := range resp.Items {
			var doc struct {
				ID string `json:"id"`
			}
			if err := json.Unmarshal(item, &doc); err != nil {
				continue
			}
			_, _ = scoresContainer.DeleteItem(ctx, pk, doc.ID, nil)
		}
	}
}

func deletePlayerLeaderboardEntries(ctx context.Context, playerId string, region string) {
	// Delete global entry
	globalKey := "global"
	globalDocId := fmt.Sprintf("global_%s", playerId)
	globalPk := azcosmos.NewPartitionKeyString(globalKey)
	_, _ = leaderboardsContainer.DeleteItem(ctx, globalPk, globalDocId, nil)

	// Delete regional entry using the known region
	if region != "" {
		regionalKey := fmt.Sprintf("regional_%s", region)
		regionalDocId := fmt.Sprintf("regional_%s_%s", region, playerId)
		regionalPk := azcosmos.NewPartitionKeyString(regionalKey)
		_, _ = leaderboardsContainer.DeleteItem(ctx, regionalPk, regionalDocId, nil)
	}
}

// ──────────────────────────────────────────────────────────────────
// Main & Router
// ──────────────────────────────────────────────────────────────────

func main() {
	// Initialize Cosmos DB
	if err := initCosmos(); err != nil {
		log.Printf("Warning: Cosmos DB initialization failed: %v", err)
		log.Println("Server will start, but database operations may fail")
	}

	gin.SetMode(gin.ReleaseMode)
	r := gin.Default()

	// Health endpoint
	r.GET("/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"status": "healthy"})
	})

	// Player endpoints
	r.POST("/api/players", createPlayer)
	r.GET("/api/players/:playerId", getPlayer)
	r.PATCH("/api/players/:playerId", updatePlayer)
	r.DELETE("/api/players/:playerId", deletePlayer)

	// Score endpoints
	r.POST("/api/scores", submitScore)
	r.GET("/api/players/:playerId/scores", getPlayerScores)

	// Leaderboard endpoints
	r.GET("/api/leaderboards/global", getGlobalLeaderboard)
	r.GET("/api/leaderboards/regional/:region", getRegionalLeaderboard)

	// Player rank endpoint
	r.GET("/api/players/:playerId/rank", getPlayerRank)

	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	log.Printf("Starting server on port %s", port)
	if err := r.Run(fmt.Sprintf("0.0.0.0:%s", port)); err != nil {
		log.Fatalf("Failed to start server: %v", err)
	}
}
