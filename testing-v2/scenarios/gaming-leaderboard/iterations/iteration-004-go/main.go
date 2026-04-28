package main

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"math"
	"net/http"
	"os"
	"sort"
	"strconv"
	"time"

	"github.com/Azure/azure-sdk-for-go/sdk/azcore"
	"github.com/Azure/azure-sdk-for-go/sdk/data/azcosmos"
	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
)

// ---------------------------------------------------------------------------
// Models – camelCase JSON via struct tags
// ---------------------------------------------------------------------------

// Type discriminator values (best practice 1.11)
const (
	TypePlayer      = "player"
	TypeScore       = "score"
	TypeLeaderboard = "leaderboard"
)

// Player represents a player profile with cumulative stats.
type Player struct {
	ID           string  `json:"id"`
	PlayerID     string  `json:"playerId"`
	DisplayName  string  `json:"displayName"`
	Region       string  `json:"region"`
	TotalGames   int     `json:"totalGames"`
	BestScore    int     `json:"bestScore"`
	AverageScore float64 `json:"averageScore"`
	TotalScore   int64   `json:"totalScore"`
	Type         string  `json:"type"`
	SchemaVer    int     `json:"_schemaVersion"`
}

// Score represents an individual game score.
type Score struct {
	ID        string `json:"id"`
	ScoreID   string `json:"scoreId"`
	PlayerID  string `json:"playerId"`
	Score     int    `json:"score"`
	GameMode  string `json:"gameMode,omitempty"`
	Timestamp string `json:"timestamp"`
	Type      string `json:"type"`
	SchemaVer int    `json:"_schemaVersion"`
}

// LeaderboardEntry is a materialized view entry for fast leaderboard queries.
type LeaderboardEntry struct {
	ID          string `json:"id"`
	PlayerID    string `json:"playerId"`
	DisplayName string `json:"displayName"`
	Score       int    `json:"score"`
	Region      string `json:"region"`
	EntryType   string `json:"entryType"` // "global" or region code
	Type        string `json:"type"`
	SchemaVer   int    `json:"_schemaVersion"`
}

// LeaderboardResponse is what the API returns for leaderboard queries.
type LeaderboardResponse struct {
	Rank        int    `json:"rank"`
	PlayerID    string `json:"playerId"`
	DisplayName string `json:"displayName"`
	Score       int    `json:"score"`
}

// PlayerRankResponse is the response for GET /api/players/{playerId}/rank.
type PlayerRankResponse struct {
	PlayerID  string                `json:"playerId"`
	Rank      int                   `json:"rank"`
	Score     int                   `json:"score"`
	Neighbors []LeaderboardResponse `json:"neighbors"`
}

// ---------------------------------------------------------------------------
// Cosmos DB client & containers (singleton – best practice 4.22)
// ---------------------------------------------------------------------------

var (
	cosmosClient         *azcosmos.Client
	playersContainer     *azcosmos.ContainerClient
	scoresContainer      *azcosmos.ContainerClient
	leaderboardContainer *azcosmos.ContainerClient
	databaseName         = "gaming-leaderboard"
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

	// Best practice 4.8: Gateway mode + disable TLS verification for emulator
	cred, err := azcosmos.NewKeyCredential(key)
	if err != nil {
		return fmt.Errorf("create credential: %w", err)
	}

	opts := &azcosmos.ClientOptions{}
	// Use custom HTTP transport for emulator TLS
	opts.ClientOptions.Transport = &http.Client{
		Transport: &http.Transport{
			TLSClientConfig: &tls.Config{InsecureSkipVerify: true},
		},
	}

	cosmosClient, err = azcosmos.NewClientWithKey(endpoint, cred, opts)
	if err != nil {
		return fmt.Errorf("create cosmos client: %w", err)
	}

	ctx := context.Background()

	// Create database
	dbProps := azcosmos.DatabaseProperties{ID: databaseName}
	_, err = cosmosClient.CreateDatabase(ctx, dbProps, nil)
	if err != nil && !isConflict(err) {
		return fmt.Errorf("create database: %w", err)
	}

	db, err := cosmosClient.NewDatabase(databaseName)
	if err != nil {
		return fmt.Errorf("get database: %w", err)
	}

	// Players container – partition key: /playerId (best practice 2.4, 2.7)
	if err := createContainerIfNotExists(ctx, db, "players", "/playerId"); err != nil {
		return err
	}
	playersContainer, err = db.NewContainer("players")
	if err != nil {
		return err
	}

	// Scores container – partition key: /playerId
	if err := createContainerIfNotExists(ctx, db, "scores", "/playerId"); err != nil {
		return err
	}
	scoresContainer, err = db.NewContainer("scores")
	if err != nil {
		return err
	}

	// Leaderboard container – partition key: /entryType for efficient leaderboard queries
	if err := createContainerIfNotExists(ctx, db, "leaderboard", "/entryType"); err != nil {
		return err
	}
	leaderboardContainer, err = db.NewContainer("leaderboard")
	if err != nil {
		return err
	}

	return nil
}

func createContainerIfNotExists(ctx context.Context, db *azcosmos.DatabaseClient, name, partitionKey string) error {
	containerProps := azcosmos.ContainerProperties{
		ID: name,
		PartitionKeyDefinition: azcosmos.PartitionKeyDefinition{
			Paths: []string{partitionKey},
		},
	}
	_, err := db.CreateContainer(ctx, containerProps, nil)
	if err != nil && !isConflict(err) {
		return fmt.Errorf("create container %s: %w", name, err)
	}
	return nil
}

func getStatusCode(err error) int {
	var respErr *azcore.ResponseError
	if errors.As(err, &respErr) {
		return respErr.StatusCode
	}
	return 0
}

func isConflict(err error) bool {
	return getStatusCode(err) == 409
}

func isPreconditionFailed(err error) bool {
	return getStatusCode(err) == 412
}

func isNotFound(err error) bool {
	return getStatusCode(err) == 404
}

// ---------------------------------------------------------------------------
// main
// ---------------------------------------------------------------------------

func main() {
	// Initialize Cosmos DB in background so health endpoint responds immediately
	go func() {
		for i := 0; i < 30; i++ {
			if err := initCosmos(); err != nil {
				log.Printf("Cosmos init attempt %d failed: %v", i+1, err)
				time.Sleep(2 * time.Second)
				continue
			}
			log.Println("Cosmos DB initialized successfully")
			return
		}
		log.Fatal("Failed to initialize Cosmos DB after retries")
	}()

	gin.SetMode(gin.ReleaseMode)
	r := gin.Default()

	// Health endpoint
	r.GET("/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"status": "ok"})
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
	r.GET("/api/leaderboards/global", globalLeaderboard)
	r.GET("/api/leaderboards/regional/:region", regionalLeaderboard)

	// Player rank
	r.GET("/api/players/:playerId/rank", getPlayerRank)

	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}
	log.Printf("Server starting on :%s", port)
	if err := r.Run(":" + port); err != nil {
		log.Fatalf("Server failed: %v", err)
	}
}

// ---------------------------------------------------------------------------
// Handlers
// ---------------------------------------------------------------------------

func createPlayer(c *gin.Context) {
	if playersContainer == nil {
		c.JSON(http.StatusServiceUnavailable, gin.H{"error": "service initializing"})
		return
	}

	var req struct {
		PlayerID    string `json:"playerId"`
		DisplayName string `json:"displayName"`
		Region      string `json:"region"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	// Validate required fields
	if req.PlayerID == "" || req.DisplayName == "" || req.Region == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "playerId, displayName, and region are required"})
		return
	}

	player := Player{
		ID:           req.PlayerID,
		PlayerID:     req.PlayerID,
		DisplayName:  req.DisplayName,
		Region:       req.Region,
		TotalGames:   0,
		BestScore:    0,
		AverageScore: 0,
		TotalScore:   0,
		Type:         TypePlayer,
		SchemaVer:    1,
	}

	data, err := json.Marshal(player)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to marshal player"})
		return
	}

	pk := azcosmos.NewPartitionKeyString(req.PlayerID)

	_, err = playersContainer.CreateItem(c.Request.Context(), pk, data, nil)
	if err != nil {
		if isConflict(err) {
			c.JSON(http.StatusConflict, gin.H{"error": "player already exists"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("create player: %v", err)})
		return
	}

	c.JSON(http.StatusCreated, gin.H{
		"playerId":     player.PlayerID,
		"displayName":  player.DisplayName,
		"region":       player.Region,
		"totalGames":   player.TotalGames,
		"bestScore":    player.BestScore,
		"averageScore": player.AverageScore,
	})
}

func getPlayer(c *gin.Context) {
	if playersContainer == nil {
		c.JSON(http.StatusServiceUnavailable, gin.H{"error": "service initializing"})
		return
	}

	playerID := c.Param("playerId")
	pk := azcosmos.NewPartitionKeyString(playerID)

	// Best practice 3.9: Point read by ID + partition key
	resp, err := playersContainer.ReadItem(c.Request.Context(), pk, playerID, nil)
	if err != nil {
		if isNotFound(err) {
			c.JSON(http.StatusNotFound, gin.H{"error": "player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("read player: %v", err)})
		return
	}

	var player Player
	if err := json.Unmarshal(resp.Value, &player); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "unmarshal player"})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"playerId":     player.PlayerID,
		"displayName":  player.DisplayName,
		"region":       player.Region,
		"totalGames":   player.TotalGames,
		"bestScore":    player.BestScore,
		"averageScore": player.AverageScore,
	})
}

func updatePlayer(c *gin.Context) {
	if playersContainer == nil {
		c.JSON(http.StatusServiceUnavailable, gin.H{"error": "service initializing"})
		return
	}

	playerID := c.Param("playerId")
	pk := azcosmos.NewPartitionKeyString(playerID)

	var req map[string]interface{}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	// Read current player
	resp, err := playersContainer.ReadItem(c.Request.Context(), pk, playerID, nil)
	if err != nil {
		if isNotFound(err) {
			c.JSON(http.StatusNotFound, gin.H{"error": "player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("read player: %v", err)})
		return
	}

	var player Player
	if err := json.Unmarshal(resp.Value, &player); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "unmarshal player"})
		return
	}

	oldRegion := player.Region

	// Apply updates
	if dn, ok := req["displayName"].(string); ok && dn != "" {
		player.DisplayName = dn
	}
	if rg, ok := req["region"].(string); ok && rg != "" {
		player.Region = rg
	}

	data, err := json.Marshal(player)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "marshal player"})
		return
	}

	_, err = playersContainer.ReplaceItem(c.Request.Context(), pk, playerID, data, nil)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("update player: %v", err)})
		return
	}

	// Update leaderboard entries if player has scores
	if player.BestScore > 0 {
		ctx := c.Request.Context()
		// Update global leaderboard entry
		updateLeaderboardEntry(ctx, player, "global")

		// If region changed, delete old regional entry and create new one
		if oldRegion != player.Region {
			deleteLeaderboardEntry(ctx, player.PlayerID, oldRegion)
		}
		updateLeaderboardEntry(ctx, player, player.Region)
	}

	c.JSON(http.StatusOK, gin.H{
		"playerId":     player.PlayerID,
		"displayName":  player.DisplayName,
		"region":       player.Region,
		"totalGames":   player.TotalGames,
		"bestScore":    player.BestScore,
		"averageScore": player.AverageScore,
	})
}

func deletePlayer(c *gin.Context) {
	if playersContainer == nil {
		c.JSON(http.StatusServiceUnavailable, gin.H{"error": "service initializing"})
		return
	}

	playerID := c.Param("playerId")
	pk := azcosmos.NewPartitionKeyString(playerID)
	ctx := c.Request.Context()

	// Check player exists first
	resp, err := playersContainer.ReadItem(ctx, pk, playerID, nil)
	if err != nil {
		if isNotFound(err) {
			c.JSON(http.StatusNotFound, gin.H{"error": "player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("read player: %v", err)})
		return
	}

	var player Player
	if err := json.Unmarshal(resp.Value, &player); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "unmarshal player"})
		return
	}

	// Delete player
	_, err = playersContainer.DeleteItem(ctx, pk, playerID, nil)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("delete player: %v", err)})
		return
	}

	// Delete all scores for this player
	deleteAllScores(ctx, playerID)

	// Delete leaderboard entries
	deleteLeaderboardEntry(ctx, playerID, "global")
	deleteLeaderboardEntry(ctx, playerID, player.Region)

	c.Status(http.StatusNoContent)
}

func submitScore(c *gin.Context) {
	if playersContainer == nil || scoresContainer == nil {
		c.JSON(http.StatusServiceUnavailable, gin.H{"error": "service initializing"})
		return
	}

	var req struct {
		PlayerID string `json:"playerId"`
		Score    *int   `json:"score"`
		GameMode string `json:"gameMode,omitempty"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	// Validate required fields
	if req.PlayerID == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "playerId is required"})
		return
	}
	if req.Score == nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "score is required"})
		return
	}
	if *req.Score < 0 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "score must be non-negative"})
		return
	}

	ctx := c.Request.Context()
	pk := azcosmos.NewPartitionKeyString(req.PlayerID)

	// Verify player exists
	playerResp, err := playersContainer.ReadItem(ctx, pk, req.PlayerID, nil)
	if err != nil {
		if isNotFound(err) {
			c.JSON(http.StatusBadRequest, gin.H{"error": "player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("read player: %v", err)})
		return
	}

	// Create score record
	scoreID := uuid.New().String()
	score := Score{
		ID:        scoreID,
		ScoreID:   scoreID,
		PlayerID:  req.PlayerID,
		Score:     *req.Score,
		GameMode:  req.GameMode,
		Timestamp: time.Now().UTC().Format(time.RFC3339),
		Type:      TypeScore,
		SchemaVer: 1,
	}

	scoreData, err := json.Marshal(score)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "marshal score"})
		return
	}

	_, err = scoresContainer.CreateItem(ctx, pk, scoreData, nil)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("create score: %v", err)})
		return
	}

	// Update player stats with ETag-based optimistic concurrency (best practice 4.9)
	if err := updatePlayerStatsWithRetry(ctx, req.PlayerID, *req.Score, playerResp); err != nil {
		log.Printf("Warning: failed to update player stats: %v", err)
	}

	c.JSON(http.StatusCreated, gin.H{
		"scoreId":  score.ScoreID,
		"playerId": score.PlayerID,
		"score":    score.Score,
	})
}

// updatePlayerStatsWithRetry updates player stats using ETag-based optimistic concurrency
func updatePlayerStatsWithRetry(ctx context.Context, playerID string, newScore int, initialResp azcosmos.ItemResponse) error {
	pk := azcosmos.NewPartitionKeyString(playerID)

	resp := initialResp
	for attempt := 0; attempt < 20; attempt++ {
		var player Player
		if err := json.Unmarshal(resp.Value, &player); err != nil {
			return fmt.Errorf("unmarshal player: %w", err)
		}

		// Update stats
		player.TotalGames++
		player.TotalScore += int64(newScore)
		if newScore > player.BestScore {
			player.BestScore = newScore
		}
		player.AverageScore = math.Round(float64(player.TotalScore)/float64(player.TotalGames)*100) / 100

		data, err := json.Marshal(player)
		if err != nil {
			return fmt.Errorf("marshal player: %w", err)
		}

		// Use ETag for optimistic concurrency
		etag := resp.ETag
		opts := &azcosmos.ItemOptions{
			IfMatchEtag: &etag,
		}

		_, err = playersContainer.ReplaceItem(ctx, pk, playerID, data, opts)
		if err != nil {
			if isPreconditionFailed(err) {
				// Re-read and retry
				newResp, readErr := playersContainer.ReadItem(ctx, pk, playerID, nil)
				if readErr != nil {
					return fmt.Errorf("re-read player: %w", readErr)
				}
				resp = newResp
				continue
			}
			return fmt.Errorf("replace player: %w", err)
		}

		// Success – update leaderboard entries
		updatedPlayer := player
		updateLeaderboardEntry(ctx, updatedPlayer, "global")
		updateLeaderboardEntry(ctx, updatedPlayer, updatedPlayer.Region)
		return nil
	}
	return fmt.Errorf("max retries exceeded for player stats update")
}

func getPlayerScores(c *gin.Context) {
	if scoresContainer == nil {
		c.JSON(http.StatusServiceUnavailable, gin.H{"error": "service initializing"})
		return
	}

	playerID := c.Param("playerId")
	ctx := c.Request.Context()

	// Check player exists first
	pk := azcosmos.NewPartitionKeyString(playerID)
	_, err := playersContainer.ReadItem(ctx, pk, playerID, nil)
	if err != nil {
		if isNotFound(err) {
			c.JSON(http.StatusNotFound, gin.H{"error": "player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("read player: %v", err)})
		return
	}

	limit := 10
	if l := c.Query("limit"); l != "" {
		if parsed, err := strconv.Atoi(l); err == nil && parsed > 0 && parsed <= 100 {
			limit = parsed
		}
	}

	// Query scores ordered by timestamp descending (best practice 3.8: parameterized)
	query := fmt.Sprintf(
		"SELECT * FROM c WHERE c.type = '%s' ORDER BY c.timestamp DESC OFFSET 0 LIMIT %d",
		TypeScore, limit,
	)

	queryOpts := &azcosmos.QueryOptions{}
	pager := scoresContainer.NewQueryItemsPager(query, pk, queryOpts)

	var scores []gin.H
	for pager.More() {
		page, err := pager.NextPage(ctx)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("query scores: %v", err)})
			return
		}
		for _, item := range page.Items {
			var s Score
			if err := json.Unmarshal(item, &s); err != nil {
				continue
			}
			entry := gin.H{
				"scoreId":   s.ScoreID,
				"playerId":  s.PlayerID,
				"score":     s.Score,
				"timestamp": s.Timestamp,
			}
			if s.GameMode != "" {
				entry["gameMode"] = s.GameMode
			}
			scores = append(scores, entry)
		}
	}

	if scores == nil {
		scores = []gin.H{}
	}

	// Ensure we only return up to 'limit' scores
	if len(scores) > limit {
		scores = scores[:limit]
	}

	c.JSON(http.StatusOK, scores)
}

func globalLeaderboard(c *gin.Context) {
	if leaderboardContainer == nil {
		c.JSON(http.StatusServiceUnavailable, gin.H{"error": "service initializing"})
		return
	}

	top := 100
	if t := c.Query("top"); t != "" {
		if parsed, err := strconv.Atoi(t); err == nil && parsed > 0 && parsed <= 100 {
			top = parsed
		}
	}

	entries := queryLeaderboard(c.Request.Context(), "global", top)
	c.JSON(http.StatusOK, entries)
}

func regionalLeaderboard(c *gin.Context) {
	if leaderboardContainer == nil {
		c.JSON(http.StatusServiceUnavailable, gin.H{"error": "service initializing"})
		return
	}

	region := c.Param("region")
	top := 100
	if t := c.Query("top"); t != "" {
		if parsed, err := strconv.Atoi(t); err == nil && parsed > 0 && parsed <= 100 {
			top = parsed
		}
	}

	entries := queryLeaderboard(c.Request.Context(), region, top)
	c.JSON(http.StatusOK, entries)
}

func queryLeaderboard(ctx context.Context, entryType string, top int) []LeaderboardResponse {
	pk := azcosmos.NewPartitionKeyString(entryType)
	// Query all entries for this partition, sort in-app for correct tiebreaking
	query := "SELECT * FROM c WHERE c.type = 'leaderboard'"
	queryOpts := &azcosmos.QueryOptions{}
	pager := leaderboardContainer.NewQueryItemsPager(query, pk, queryOpts)

	var entries []LeaderboardEntry
	for pager.More() {
		page, err := pager.NextPage(ctx)
		if err != nil {
			log.Printf("Query leaderboard error: %v", err)
			break
		}
		for _, item := range page.Items {
			var e LeaderboardEntry
			if err := json.Unmarshal(item, &e); err != nil {
				continue
			}
			entries = append(entries, e)
		}
	}

	// Sort by score descending, then displayName ascending for tiebreaking
	sort.Slice(entries, func(i, j int) bool {
		if entries[i].Score != entries[j].Score {
			return entries[i].Score > entries[j].Score
		}
		return entries[i].DisplayName < entries[j].DisplayName
	})

	if len(entries) > top {
		entries = entries[:top]
	}

	result := make([]LeaderboardResponse, len(entries))
	for i, e := range entries {
		result[i] = LeaderboardResponse{
			Rank:        i + 1,
			PlayerID:    e.PlayerID,
			DisplayName: e.DisplayName,
			Score:       e.Score,
		}
	}

	return result
}

func getPlayerRank(c *gin.Context) {
	if leaderboardContainer == nil || playersContainer == nil {
		c.JSON(http.StatusServiceUnavailable, gin.H{"error": "service initializing"})
		return
	}

	playerID := c.Param("playerId")
	ctx := c.Request.Context()

	// Check player exists
	pk := azcosmos.NewPartitionKeyString(playerID)
	_, err := playersContainer.ReadItem(ctx, pk, playerID, nil)
	if err != nil {
		if isNotFound(err) {
			c.JSON(http.StatusNotFound, gin.H{"error": "player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("read player: %v", err)})
		return
	}

	// Get global leaderboard
	allEntries := queryLeaderboard(ctx, "global", 10000) // Get all entries
	if len(allEntries) == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "player has no scores"})
		return
	}

	// Find player's position
	playerRank := -1
	playerScore := 0
	for _, e := range allEntries {
		if e.PlayerID == playerID {
			playerRank = e.Rank
			playerScore = e.Score
			break
		}
	}

	if playerRank == -1 {
		c.JSON(http.StatusNotFound, gin.H{"error": "player has no scores"})
		return
	}

	// Get neighbors ±10 positions
	startIdx := playerRank - 11 // 10 above
	if startIdx < 0 {
		startIdx = 0
	}
	endIdx := playerRank + 10 // 10 below
	if endIdx > len(allEntries) {
		endIdx = len(allEntries)
	}

	var neighbors []LeaderboardResponse
	for i := startIdx; i < endIdx; i++ {
		if allEntries[i].PlayerID != playerID {
			neighbors = append(neighbors, allEntries[i])
		}
	}
	if neighbors == nil {
		neighbors = []LeaderboardResponse{}
	}

	c.JSON(http.StatusOK, PlayerRankResponse{
		PlayerID:  playerID,
		Rank:      playerRank,
		Score:     playerScore,
		Neighbors: neighbors,
	})
}

// ---------------------------------------------------------------------------
// Leaderboard maintenance helpers
// ---------------------------------------------------------------------------

func updateLeaderboardEntry(ctx context.Context, player Player, entryType string) {
	if leaderboardContainer == nil {
		return
	}

	pk := azcosmos.NewPartitionKeyString(entryType)
	entryID := fmt.Sprintf("%s_%s", entryType, player.PlayerID)

	entry := LeaderboardEntry{
		ID:          entryID,
		PlayerID:    player.PlayerID,
		DisplayName: player.DisplayName,
		Score:       player.BestScore,
		Region:      player.Region,
		EntryType:   entryType,
		Type:        TypeLeaderboard,
		SchemaVer:   1,
	}

	data, err := json.Marshal(entry)
	if err != nil {
		log.Printf("Marshal leaderboard entry: %v", err)
		return
	}

	_, err = leaderboardContainer.UpsertItem(ctx, pk, data, nil)
	if err != nil {
		log.Printf("Upsert leaderboard entry: %v", err)
	}
}

func deleteLeaderboardEntry(ctx context.Context, playerID, entryType string) {
	if leaderboardContainer == nil {
		return
	}

	pk := azcosmos.NewPartitionKeyString(entryType)
	entryID := fmt.Sprintf("%s_%s", entryType, playerID)

	_, err := leaderboardContainer.DeleteItem(ctx, pk, entryID, nil)
	if err != nil && !isNotFound(err) {
		log.Printf("Delete leaderboard entry: %v", err)
	}
}

func deleteAllScores(ctx context.Context, playerID string) {
	if scoresContainer == nil {
		return
	}

	pk := azcosmos.NewPartitionKeyString(playerID)
	query := "SELECT c.id FROM c WHERE c.type = 'score'"
	queryOpts := &azcosmos.QueryOptions{}
	pager := scoresContainer.NewQueryItemsPager(query, pk, queryOpts)

	for pager.More() {
		page, err := pager.NextPage(ctx)
		if err != nil {
			log.Printf("Query scores for delete: %v", err)
			return
		}
		for _, item := range page.Items {
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
