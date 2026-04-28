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
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/Azure/azure-sdk-for-go/sdk/azcore"
	"github.com/Azure/azure-sdk-for-go/sdk/data/azcosmos"
	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
)

// ---------------------------------------------------------------------------
// Models
// ---------------------------------------------------------------------------

// Player represents a player document stored in Cosmos DB.
type Player struct {
	ID           string  `json:"id"`
	PlayerID     string  `json:"playerId"`
	DisplayName  string  `json:"displayName"`
	Region       string  `json:"region"`
	TotalGames   int     `json:"totalGames"`
	BestScore    int     `json:"bestScore"`
	TotalScore   int64   `json:"totalScore"`
	AverageScore float64 `json:"averageScore"`
	Type         string  `json:"type"`
	SchemaVer    int     `json:"schemaVersion"`
}

// PlayerResponse is the API response for player data.
type PlayerResponse struct {
	PlayerID     string  `json:"playerId"`
	DisplayName  string  `json:"displayName"`
	Region       string  `json:"region"`
	TotalGames   int     `json:"totalGames"`
	BestScore    int     `json:"bestScore"`
	AverageScore float64 `json:"averageScore"`
}

// Score represents a score document stored in Cosmos DB.
type Score struct {
	ID        string `json:"id"`
	ScoreID   string `json:"scoreId"`
	PlayerID  string `json:"playerId"`
	Score     int    `json:"score"`
	GameMode  string `json:"gameMode,omitempty"`
	Timestamp string `json:"timestamp"`
	Type      string `json:"type"`
}

// ScoreResponse is the API response for a submitted score.
type ScoreResponse struct {
	ScoreID  string `json:"scoreId"`
	PlayerID string `json:"playerId"`
	Score    int    `json:"score"`
}

// ScoreHistoryResponse is a single entry in score history.
type ScoreHistoryResponse struct {
	ScoreID   string `json:"scoreId"`
	PlayerID  string `json:"playerId"`
	Score     int    `json:"score"`
	GameMode  string `json:"gameMode,omitempty"`
	Timestamp string `json:"timestamp"`
}

// LeaderboardEntry represents a materialized leaderboard entry in Cosmos DB.
type LeaderboardEntry struct {
	ID          string `json:"id"`
	PlayerID    string `json:"playerId"`
	DisplayName string `json:"displayName"`
	Score       int    `json:"score"`
	Region      string `json:"region"`
	Type        string `json:"type"`
	BoardType   string `json:"boardType"` // "global" or region code
}

// LeaderboardResponse is a single leaderboard entry returned by the API.
type LeaderboardResponse struct {
	Rank        int    `json:"rank"`
	PlayerID    string `json:"playerId"`
	DisplayName string `json:"displayName"`
	Score       int    `json:"score"`
}

// PlayerRankResponse is the response for the player rank endpoint.
type PlayerRankResponse struct {
	PlayerID  string                `json:"playerId"`
	Rank      int                   `json:"rank"`
	Score     int                   `json:"score"`
	Neighbors []LeaderboardResponse `json:"neighbors"`
}

// ---------------------------------------------------------------------------
// Cosmos DB Service
// ---------------------------------------------------------------------------

// CosmosService wraps the Cosmos DB client and containers.
type CosmosService struct {
	client             *azcosmos.Client
	playersContainer   *azcosmos.ContainerClient
	scoresContainer    *azcosmos.ContainerClient
	leaderboardContainer *azcosmos.ContainerClient
	mu                 sync.Mutex
	initialized        bool
}

const (
	databaseName         = "gaming-leaderboard"
	playersContainerName = "players"
	scoresContainerName  = "scores"
	leaderboardContainerName = "leaderboard"
)

func newCosmosService() (*CosmosService, error) {
	endpoint := os.Getenv("COSMOS_ENDPOINT")
	if endpoint == "" {
		endpoint = "https://localhost:8081"
	}
	key := os.Getenv("COSMOS_KEY")
	if key == "" {
		key = "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw=="
	}

	cred, err := azcosmos.NewKeyCredential(key)
	if err != nil {
		return nil, fmt.Errorf("create key credential: %w", err)
	}

	// Configure for emulator: skip TLS verification
	httpClient := &http.Client{
		Transport: &http.Transport{
			TLSClientConfig: &tls.Config{InsecureSkipVerify: true},
		},
	}

	opts := &azcosmos.ClientOptions{}
	opts.Transport = &httpClientTransport{client: httpClient}

	client, err := azcosmos.NewClientWithKey(endpoint, cred, opts)
	if err != nil {
		return nil, fmt.Errorf("create cosmos client: %w", err)
	}

	return &CosmosService{client: client}, nil
}

// httpClientTransport adapts *http.Client to azcore's policy.Transporter.
type httpClientTransport struct {
	client *http.Client
}

func (t *httpClientTransport) Do(req *http.Request) (*http.Response, error) {
	return t.client.Do(req)
}

// Initialize creates the database and containers if they don't exist.
func (s *CosmosService) Initialize(ctx context.Context) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.initialized {
		return nil
	}

	dbProps := azcosmos.DatabaseProperties{ID: databaseName}
	_, err := s.client.CreateDatabase(ctx, dbProps, nil)
	if err != nil && !isConflict(err) {
		return fmt.Errorf("create database: %w", err)
	}

	db, err := s.client.NewDatabase(databaseName)
	if err != nil {
		return fmt.Errorf("get database: %w", err)
	}

	// Players container: partition key = /playerId
	playersProps := azcosmos.ContainerProperties{
		ID: playersContainerName,
		PartitionKeyDefinition: azcosmos.PartitionKeyDefinition{
			Paths: []string{"/playerId"},
		},
	}
	_, err = db.CreateContainer(ctx, playersProps, nil)
	if err != nil && !isConflict(err) {
		return fmt.Errorf("create players container: %w", err)
	}

	// Scores container: partition key = /playerId
	scoresProps := azcosmos.ContainerProperties{
		ID: scoresContainerName,
		PartitionKeyDefinition: azcosmos.PartitionKeyDefinition{
			Paths: []string{"/playerId"},
		},
	}
	_, err = db.CreateContainer(ctx, scoresProps, nil)
	if err != nil && !isConflict(err) {
		return fmt.Errorf("create scores container: %w", err)
	}

	// Leaderboard container: partition key = /boardType
	leaderboardProps := azcosmos.ContainerProperties{
		ID: leaderboardContainerName,
		PartitionKeyDefinition: azcosmos.PartitionKeyDefinition{
			Paths: []string{"/boardType"},
		},
	}
	_, err = db.CreateContainer(ctx, leaderboardProps, nil)
	if err != nil && !isConflict(err) {
		return fmt.Errorf("create leaderboard container: %w", err)
	}

	s.playersContainer, err = s.client.NewContainer(databaseName, playersContainerName)
	if err != nil {
		return fmt.Errorf("get players container: %w", err)
	}
	s.scoresContainer, err = s.client.NewContainer(databaseName, scoresContainerName)
	if err != nil {
		return fmt.Errorf("get scores container: %w", err)
	}
	s.leaderboardContainer, err = s.client.NewContainer(databaseName, leaderboardContainerName)
	if err != nil {
		return fmt.Errorf("get leaderboard container: %w", err)
	}

	s.initialized = true
	return nil
}

func isConflict(err error) bool {
	var respErr *azcore.ResponseError
	if ok := asResponseError(err, &respErr); ok {
		return respErr.StatusCode == http.StatusConflict
	}
	return false
}

func isPreconditionFailed(err error) bool {
	var respErr *azcore.ResponseError
	if ok := asResponseError(err, &respErr); ok {
		return respErr.StatusCode == http.StatusPreconditionFailed
	}
	return false
}

func isNotFound(err error) bool {
	var respErr *azcore.ResponseError
	if ok := asResponseError(err, &respErr); ok {
		return respErr.StatusCode == http.StatusNotFound
	}
	return false
}

func asResponseError(err error, target **azcore.ResponseError) bool {
	if err == nil {
		return false
	}
	// Walk the error chain
	for {
		if re, ok := err.(*azcore.ResponseError); ok {
			*target = re
			return true
		}
		unwrapped := unwrapErr(err)
		if unwrapped == nil {
			return false
		}
		err = unwrapped
	}
}

func unwrapErr(err error) error {
	type unwrapper interface {
		Unwrap() error
	}
	if u, ok := err.(unwrapper); ok {
		return u.Unwrap()
	}
	return nil
}

// ---------------------------------------------------------------------------
// Player operations
// ---------------------------------------------------------------------------

func (s *CosmosService) CreatePlayer(ctx context.Context, playerID, displayName, region string) (*Player, error) {
	player := Player{
		ID:           playerID,
		PlayerID:     playerID,
		DisplayName:  displayName,
		Region:       region,
		TotalGames:   0,
		BestScore:    0,
		TotalScore:   0,
		AverageScore: 0,
		Type:         "player",
		SchemaVer:    1,
	}

	data, err := json.Marshal(player)
	if err != nil {
		return nil, err
	}

	pk := azcosmos.NewPartitionKeyString(playerID)
	_, err = s.playersContainer.CreateItem(ctx, pk, data, nil)
	if err != nil {
		if isConflict(err) {
			return nil, fmt.Errorf("conflict: player already exists")
		}
		return nil, err
	}

	return &player, nil
}

func (s *CosmosService) GetPlayer(ctx context.Context, playerID string) (*Player, error) {
	pk := azcosmos.NewPartitionKeyString(playerID)
	resp, err := s.playersContainer.ReadItem(ctx, pk, playerID, nil)
	if err != nil {
		if isNotFound(err) {
			return nil, nil
		}
		return nil, err
	}

	var player Player
	if err := json.Unmarshal(resp.Value, &player); err != nil {
		return nil, err
	}
	return &player, nil
}

func (s *CosmosService) UpdatePlayer(ctx context.Context, playerID string, displayName, region *string) (*Player, error) {
	const maxRetries = 10
	for attempt := 0; attempt < maxRetries; attempt++ {
		pk := azcosmos.NewPartitionKeyString(playerID)
		resp, err := s.playersContainer.ReadItem(ctx, pk, playerID, nil)
		if err != nil {
			if isNotFound(err) {
				return nil, nil
			}
			return nil, err
		}

		var player Player
		if err := json.Unmarshal(resp.Value, &player); err != nil {
			return nil, err
		}
		etag := resp.ETag

		oldRegion := player.Region

		if displayName != nil {
			player.DisplayName = *displayName
		}
		if region != nil {
			player.Region = *region
		}

		data, err := json.Marshal(player)
		if err != nil {
			return nil, err
		}

		replaceOpts := &azcosmos.ItemOptions{
			IfMatchEtag: &etag,
		}
		_, err = s.playersContainer.ReplaceItem(ctx, pk, playerID, data, replaceOpts)
		if err != nil {
			if isPreconditionFailed(err) {
				continue
			}
			return nil, err
		}

		// Update leaderboard entries if displayName or region changed
		if displayName != nil || region != nil {
			s.updateLeaderboardEntries(ctx, &player, oldRegion, region)
		}

		return &player, nil
	}

	return nil, fmt.Errorf("failed to update player after retries")
}

func (s *CosmosService) updateLeaderboardEntries(ctx context.Context, player *Player, oldRegion string, newRegion *string) {
	// Update global leaderboard entry
	globalPK := azcosmos.NewPartitionKeyString("global")
	globalID := "global-" + player.PlayerID

	var globalScore int
	globalExists := false
	resp, err := s.leaderboardContainer.ReadItem(ctx, globalPK, globalID, nil)
	if err == nil {
		var entry LeaderboardEntry
		if json.Unmarshal(resp.Value, &entry) == nil {
			globalScore = entry.Score
			globalExists = true
			entry.DisplayName = player.DisplayName
			entry.Region = player.Region
			data, _ := json.Marshal(entry)
			s.leaderboardContainer.ReplaceItem(ctx, globalPK, globalID, data, nil)
		}
	}

	// If region changed, move regional leaderboard entry
	if newRegion != nil && *newRegion != oldRegion {
		// Delete old regional entry
		oldRegionalPK := azcosmos.NewPartitionKeyString(oldRegion)
		oldRegionalID := oldRegion + "-" + player.PlayerID
		s.leaderboardContainer.DeleteItem(ctx, oldRegionalPK, oldRegionalID, nil)

		// Create new regional entry using score from global
		if globalExists {
			newEntry := LeaderboardEntry{
				ID:          player.Region + "-" + player.PlayerID,
				PlayerID:    player.PlayerID,
				DisplayName: player.DisplayName,
				Score:       globalScore,
				Region:      player.Region,
				Type:        "leaderboard",
				BoardType:   player.Region,
			}
			data, _ := json.Marshal(newEntry)
			newRegionalPK := azcosmos.NewPartitionKeyString(player.Region)
			s.leaderboardContainer.CreateItem(ctx, newRegionalPK, data, nil)
		}
	} else {
		// Just update displayName on regional entry
		regionalPK := azcosmos.NewPartitionKeyString(player.Region)
		regionalID := player.Region + "-" + player.PlayerID
		resp, err := s.leaderboardContainer.ReadItem(ctx, regionalPK, regionalID, nil)
		if err == nil {
			var entry LeaderboardEntry
			if json.Unmarshal(resp.Value, &entry) == nil {
				entry.DisplayName = player.DisplayName
				data, _ := json.Marshal(entry)
				s.leaderboardContainer.ReplaceItem(ctx, regionalPK, regionalID, data, nil)
			}
		}
	}
}

func (s *CosmosService) DeletePlayer(ctx context.Context, playerID string) (bool, error) {
	pk := azcosmos.NewPartitionKeyString(playerID)

	// Check player exists
	player, err := s.GetPlayer(ctx, playerID)
	if err != nil {
		return false, err
	}
	if player == nil {
		return false, nil
	}

	// Delete player document
	_, err = s.playersContainer.DeleteItem(ctx, pk, playerID, nil)
	if err != nil {
		if isNotFound(err) {
			return false, nil
		}
		return false, err
	}

	// Delete all scores for this player
	s.deletePlayerScores(ctx, playerID)

	// Delete leaderboard entries
	s.deleteLeaderboardEntries(ctx, playerID, player.Region)

	return true, nil
}

func (s *CosmosService) deletePlayerScores(ctx context.Context, playerID string) {
	pk := azcosmos.NewPartitionKeyString(playerID)
	query := "SELECT c.id FROM c WHERE c.playerId = @playerId AND c.type = 'score'"
	params := []azcosmos.QueryParameter{
		{Name: "@playerId", Value: playerID},
	}

	pager := s.scoresContainer.NewQueryItemsPager(query, pk, &azcosmos.QueryOptions{
		QueryParameters: params,
	})

	for pager.More() {
		page, err := pager.NextPage(ctx)
		if err != nil {
			log.Printf("Error querying scores for deletion: %v", err)
			break
		}
		for _, item := range page.Items {
			var doc struct {
				ID string `json:"id"`
			}
			if json.Unmarshal(item, &doc) == nil {
				s.scoresContainer.DeleteItem(ctx, pk, doc.ID, nil)
			}
		}
	}
}

func (s *CosmosService) deleteLeaderboardEntries(ctx context.Context, playerID, region string) {
	// Delete global entry
	globalPK := azcosmos.NewPartitionKeyString("global")
	globalID := "global-" + playerID
	s.leaderboardContainer.DeleteItem(ctx, globalPK, globalID, nil)

	// Delete regional entry
	if region != "" {
		regionalPK := azcosmos.NewPartitionKeyString(region)
		regionalID := region + "-" + playerID
		s.leaderboardContainer.DeleteItem(ctx, regionalPK, regionalID, nil)
	}
}

// ---------------------------------------------------------------------------
// Score operations
// ---------------------------------------------------------------------------

func (s *CosmosService) SubmitScore(ctx context.Context, playerID string, score int, gameMode string) (*ScoreResponse, error) {
	// Verify player exists
	player, err := s.GetPlayer(ctx, playerID)
	if err != nil {
		return nil, fmt.Errorf("error checking player: %w", err)
	}
	if player == nil {
		return nil, fmt.Errorf("player not found")
	}

	scoreID := uuid.New().String()
	now := time.Now().UTC().Format(time.RFC3339)

	scoreDoc := Score{
		ID:        scoreID,
		ScoreID:   scoreID,
		PlayerID:  playerID,
		Score:     score,
		GameMode:  gameMode,
		Timestamp: now,
		Type:      "score",
	}

	data, err := json.Marshal(scoreDoc)
	if err != nil {
		return nil, err
	}

	pk := azcosmos.NewPartitionKeyString(playerID)
	_, err = s.scoresContainer.CreateItem(ctx, pk, data, nil)
	if err != nil {
		return nil, fmt.Errorf("create score: %w", err)
	}

	// Update player stats with ETag-based optimistic concurrency
	if err := s.updatePlayerStats(ctx, playerID, score); err != nil {
		log.Printf("Warning: failed to update player stats: %v", err)
	}

	// Update leaderboard entries
	s.updateLeaderboardForScore(ctx, player, score)

	return &ScoreResponse{
		ScoreID:  scoreID,
		PlayerID: playerID,
		Score:    score,
	}, nil
}

func (s *CosmosService) updatePlayerStats(ctx context.Context, playerID string, newScore int) error {
	const maxRetries = 20
	pk := azcosmos.NewPartitionKeyString(playerID)

	for attempt := 0; attempt < maxRetries; attempt++ {
		resp, err := s.playersContainer.ReadItem(ctx, pk, playerID, nil)
		if err != nil {
			return fmt.Errorf("read player for stats update: %w", err)
		}

		var player Player
		if err := json.Unmarshal(resp.Value, &player); err != nil {
			return err
		}
		etag := resp.ETag

		player.TotalGames++
		player.TotalScore += int64(newScore)
		player.AverageScore = float64(player.TotalScore) / float64(player.TotalGames)
		if newScore > player.BestScore {
			player.BestScore = newScore
		}

		data, err := json.Marshal(player)
		if err != nil {
			return err
		}

		replaceOpts := &azcosmos.ItemOptions{
			IfMatchEtag: &etag,
		}
		_, err = s.playersContainer.ReplaceItem(ctx, pk, playerID, data, replaceOpts)
		if err != nil {
			if isPreconditionFailed(err) {
				// Exponential backoff with jitter
				time.Sleep(time.Duration(attempt*5+1) * time.Millisecond)
				continue
			}
			return fmt.Errorf("replace player: %w", err)
		}
		return nil
	}

	return fmt.Errorf("failed to update player stats after %d retries", maxRetries)
}

func (s *CosmosService) updateLeaderboardForScore(ctx context.Context, player *Player, score int) {
	// Get current best score from player (need fresh read since stats were just updated)
	freshPlayer, err := s.GetPlayer(ctx, player.PlayerID)
	if err != nil || freshPlayer == nil {
		return
	}
	bestScore := freshPlayer.BestScore

	// Update/create global leaderboard entry
	globalID := "global-" + player.PlayerID
	globalPK := azcosmos.NewPartitionKeyString("global")

	globalEntry := LeaderboardEntry{
		ID:          globalID,
		PlayerID:    player.PlayerID,
		DisplayName: freshPlayer.DisplayName,
		Score:       bestScore,
		Region:      freshPlayer.Region,
		Type:        "leaderboard",
		BoardType:   "global",
	}
	data, _ := json.Marshal(globalEntry)
	s.leaderboardContainer.UpsertItem(ctx, globalPK, data, nil)

	// Update/create regional leaderboard entry
	region := freshPlayer.Region
	regionalID := region + "-" + player.PlayerID
	regionalPK := azcosmos.NewPartitionKeyString(region)

	regionalEntry := LeaderboardEntry{
		ID:          regionalID,
		PlayerID:    player.PlayerID,
		DisplayName: freshPlayer.DisplayName,
		Score:       bestScore,
		Region:      region,
		Type:        "leaderboard",
		BoardType:   region,
	}
	data, _ = json.Marshal(regionalEntry)
	s.leaderboardContainer.UpsertItem(ctx, regionalPK, data, nil)
}

func (s *CosmosService) GetPlayerScores(ctx context.Context, playerID string, limit int) ([]ScoreHistoryResponse, error) {
	// Check player exists first
	player, err := s.GetPlayer(ctx, playerID)
	if err != nil {
		return nil, err
	}
	if player == nil {
		return nil, fmt.Errorf("player not found")
	}

	pk := azcosmos.NewPartitionKeyString(playerID)
	query := fmt.Sprintf("SELECT TOP %d c.scoreId, c.playerId, c.score, c.gameMode, c.timestamp FROM c WHERE c.playerId = @playerId AND c.type = 'score' ORDER BY c.timestamp DESC", limit)
	params := []azcosmos.QueryParameter{
		{Name: "@playerId", Value: playerID},
	}

	pager := s.scoresContainer.NewQueryItemsPager(query, pk, &azcosmos.QueryOptions{
		QueryParameters: params,
	})

	var scores []ScoreHistoryResponse
	for pager.More() {
		page, err := pager.NextPage(ctx)
		if err != nil {
			return nil, fmt.Errorf("query scores: %w", err)
		}
		for _, item := range page.Items {
			var score ScoreHistoryResponse
			if err := json.Unmarshal(item, &score); err != nil {
				continue
			}
			scores = append(scores, score)
		}
	}

	if scores == nil {
		scores = []ScoreHistoryResponse{}
	}
	return scores, nil
}

// ---------------------------------------------------------------------------
// Leaderboard operations
// ---------------------------------------------------------------------------

func (s *CosmosService) GetGlobalLeaderboard(ctx context.Context, top int) ([]LeaderboardResponse, error) {
	return s.getLeaderboard(ctx, "global", top)
}

func (s *CosmosService) GetRegionalLeaderboard(ctx context.Context, region string, top int) ([]LeaderboardResponse, error) {
	return s.getLeaderboard(ctx, region, top)
}

func (s *CosmosService) getLeaderboard(ctx context.Context, boardType string, top int) ([]LeaderboardResponse, error) {
	pk := azcosmos.NewPartitionKeyString(boardType)
	query := "SELECT c.playerId, c.displayName, c.score FROM c WHERE c.boardType = @boardType AND c.type = 'leaderboard'"
	params := []azcosmos.QueryParameter{
		{Name: "@boardType", Value: boardType},
	}

	pager := s.leaderboardContainer.NewQueryItemsPager(query, pk, &azcosmos.QueryOptions{
		QueryParameters: params,
	})

	type entry struct {
		PlayerID    string `json:"playerId"`
		DisplayName string `json:"displayName"`
		Score       int    `json:"score"`
	}

	var entries []entry
	for pager.More() {
		page, err := pager.NextPage(ctx)
		if err != nil {
			return nil, fmt.Errorf("query leaderboard: %w", err)
		}
		for _, item := range page.Items {
			var e entry
			if err := json.Unmarshal(item, &e); err != nil {
				continue
			}
			entries = append(entries, e)
		}
	}

	// Sort by score DESC, then displayName ASC for tiebreaking
	sort.Slice(entries, func(i, j int) bool {
		if entries[i].Score != entries[j].Score {
			return entries[i].Score > entries[j].Score
		}
		return entries[i].DisplayName < entries[j].DisplayName
	})

	// Limit
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

	return result, nil
}

func (s *CosmosService) GetPlayerRank(ctx context.Context, playerID string) (*PlayerRankResponse, error) {
	// Check player exists
	player, err := s.GetPlayer(ctx, playerID)
	if err != nil {
		return nil, err
	}
	if player == nil {
		return nil, fmt.Errorf("player not found")
	}

	// Get entire global leaderboard to find rank
	entries, err := s.GetGlobalLeaderboard(ctx, 10000)
	if err != nil {
		return nil, err
	}

	// Find the player's position
	playerRank := -1
	playerScore := 0
	for _, e := range entries {
		if e.PlayerID == playerID {
			playerRank = e.Rank
			playerScore = e.Score
			break
		}
	}

	if playerRank == -1 {
		return nil, fmt.Errorf("player not on leaderboard")
	}

	// Get neighbors ±10 positions
	startIdx := playerRank - 11 // 10 above
	if startIdx < 0 {
		startIdx = 0
	}
	endIdx := playerRank + 10 // 10 below
	if endIdx > len(entries) {
		endIdx = len(entries)
	}

	var neighbors []LeaderboardResponse
	for i := startIdx; i < endIdx; i++ {
		if entries[i].PlayerID != playerID {
			neighbors = append(neighbors, entries[i])
		}
	}

	if neighbors == nil {
		neighbors = []LeaderboardResponse{}
	}

	return &PlayerRankResponse{
		PlayerID:  playerID,
		Rank:      playerRank,
		Score:     playerScore,
		Neighbors: neighbors,
	}, nil
}

// ---------------------------------------------------------------------------
// HTTP Handlers
// ---------------------------------------------------------------------------

func setupRouter(svc *CosmosService) *gin.Engine {
	gin.SetMode(gin.ReleaseMode)
	r := gin.Default()

	// Health endpoint
	r.GET("/health", func(c *gin.Context) {
		c.JSON(200, gin.H{"status": "healthy"})
	})

	// Player endpoints
	r.POST("/api/players", func(c *gin.Context) {
		var body struct {
			PlayerID    string `json:"playerId"`
			DisplayName string `json:"displayName"`
			Region      string `json:"region"`
		}

		if err := c.ShouldBindJSON(&body); err != nil {
			c.JSON(400, gin.H{"error": "invalid request body"})
			return
		}

		if body.PlayerID == "" || body.DisplayName == "" || body.Region == "" {
			c.JSON(400, gin.H{"error": "playerId, displayName, and region are required"})
			return
		}

		player, err := svc.CreatePlayer(c.Request.Context(), body.PlayerID, body.DisplayName, body.Region)
		if err != nil {
			if strings.Contains(err.Error(), "conflict") {
				c.JSON(409, gin.H{"error": "player already exists"})
				return
			}
			log.Printf("Error creating player: %v", err)
			c.JSON(500, gin.H{"error": "internal server error"})
			return
		}

		c.JSON(201, toPlayerResponse(player))
	})

	r.GET("/api/players/:playerId", func(c *gin.Context) {
		playerID := c.Param("playerId")
		player, err := svc.GetPlayer(c.Request.Context(), playerID)
		if err != nil {
			log.Printf("Error getting player: %v", err)
			c.JSON(500, gin.H{"error": "internal server error"})
			return
		}
		if player == nil {
			c.JSON(404, gin.H{"error": "player not found"})
			return
		}
		c.JSON(200, toPlayerResponse(player))
	})

	r.PATCH("/api/players/:playerId", func(c *gin.Context) {
		playerID := c.Param("playerId")

		var body map[string]interface{}
		if err := c.ShouldBindJSON(&body); err != nil {
			c.JSON(400, gin.H{"error": "invalid request body"})
			return
		}

		var displayName, region *string
		if v, ok := body["displayName"]; ok {
			if s, ok := v.(string); ok {
				displayName = &s
			}
		}
		if v, ok := body["region"]; ok {
			if s, ok := v.(string); ok {
				region = &s
			}
		}

		player, err := svc.UpdatePlayer(c.Request.Context(), playerID, displayName, region)
		if err != nil {
			log.Printf("Error updating player: %v", err)
			c.JSON(500, gin.H{"error": "internal server error"})
			return
		}
		if player == nil {
			c.JSON(404, gin.H{"error": "player not found"})
			return
		}

		c.JSON(200, toPlayerResponse(player))
	})

	r.DELETE("/api/players/:playerId", func(c *gin.Context) {
		playerID := c.Param("playerId")
		deleted, err := svc.DeletePlayer(c.Request.Context(), playerID)
		if err != nil {
			log.Printf("Error deleting player: %v", err)
			c.JSON(500, gin.H{"error": "internal server error"})
			return
		}
		if !deleted {
			c.JSON(404, gin.H{"error": "player not found"})
			return
		}
		c.Status(204)
	})

	// Score endpoints
	r.POST("/api/scores", func(c *gin.Context) {
		var body struct {
			PlayerID string  `json:"playerId"`
			Score    *int    `json:"score"`
			GameMode string  `json:"gameMode"`
		}

		if err := c.ShouldBindJSON(&body); err != nil {
			c.JSON(400, gin.H{"error": "invalid request body"})
			return
		}

		if body.PlayerID == "" {
			c.JSON(400, gin.H{"error": "playerId is required"})
			return
		}
		if body.Score == nil {
			c.JSON(400, gin.H{"error": "score is required"})
			return
		}
		if *body.Score < 0 {
			c.JSON(400, gin.H{"error": "score must be a positive integer"})
			return
		}

		result, err := svc.SubmitScore(c.Request.Context(), body.PlayerID, *body.Score, body.GameMode)
		if err != nil {
			if strings.Contains(err.Error(), "player not found") {
				c.JSON(404, gin.H{"error": "player not found"})
				return
			}
			log.Printf("Error submitting score: %v", err)
			c.JSON(500, gin.H{"error": "internal server error"})
			return
		}

		c.JSON(201, result)
	})

	r.GET("/api/players/:playerId/scores", func(c *gin.Context) {
		playerID := c.Param("playerId")
		limitStr := c.DefaultQuery("limit", "10")
		limit, err := strconv.Atoi(limitStr)
		if err != nil || limit < 1 {
			limit = 10
		}
		if limit > 100 {
			limit = 100
		}

		scores, err := svc.GetPlayerScores(c.Request.Context(), playerID, limit)
		if err != nil {
			if strings.Contains(err.Error(), "player not found") {
				c.JSON(404, gin.H{"error": "player not found"})
				return
			}
			log.Printf("Error getting scores: %v", err)
			c.JSON(500, gin.H{"error": "internal server error"})
			return
		}

		c.JSON(200, scores)
	})

	// Leaderboard endpoints
	r.GET("/api/leaderboards/global", func(c *gin.Context) {
		topStr := c.DefaultQuery("top", "100")
		top, err := strconv.Atoi(topStr)
		if err != nil || top < 1 {
			top = 100
		}
		if top > 100 {
			top = 100
		}

		entries, err := svc.GetGlobalLeaderboard(c.Request.Context(), top)
		if err != nil {
			log.Printf("Error getting global leaderboard: %v", err)
			c.JSON(500, gin.H{"error": "internal server error"})
			return
		}

		c.JSON(200, entries)
	})

	r.GET("/api/leaderboards/regional/:region", func(c *gin.Context) {
		region := c.Param("region")
		topStr := c.DefaultQuery("top", "100")
		top, err := strconv.Atoi(topStr)
		if err != nil || top < 1 {
			top = 100
		}
		if top > 100 {
			top = 100
		}

		entries, err := svc.GetRegionalLeaderboard(c.Request.Context(), region, top)
		if err != nil {
			log.Printf("Error getting regional leaderboard: %v", err)
			c.JSON(500, gin.H{"error": "internal server error"})
			return
		}

		c.JSON(200, entries)
	})

	r.GET("/api/players/:playerId/rank", func(c *gin.Context) {
		playerID := c.Param("playerId")
		rankResp, err := svc.GetPlayerRank(c.Request.Context(), playerID)
		if err != nil {
			if strings.Contains(err.Error(), "not found") || strings.Contains(err.Error(), "not on leaderboard") {
				c.JSON(404, gin.H{"error": "player not found or has no scores"})
				return
			}
			log.Printf("Error getting player rank: %v", err)
			c.JSON(500, gin.H{"error": "internal server error"})
			return
		}

		c.JSON(200, rankResp)
	})

	return r
}

func toPlayerResponse(p *Player) PlayerResponse {
	avg := p.AverageScore
	// Round to reasonable precision
	avg = math.Round(avg*100) / 100
	return PlayerResponse{
		PlayerID:     p.PlayerID,
		DisplayName:  p.DisplayName,
		Region:       p.Region,
		TotalGames:   p.TotalGames,
		BestScore:    p.BestScore,
		AverageScore: avg,
	}
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

func main() {
	svc, err := newCosmosService()
	if err != nil {
		log.Fatalf("Failed to create Cosmos service: %v", err)
	}

	// Initialize in background so health endpoint responds immediately
	go func() {
		ctx := context.Background()
		for i := 0; i < 30; i++ {
			if err := svc.Initialize(ctx); err != nil {
				log.Printf("Cosmos initialization attempt %d failed: %v", i+1, err)
				time.Sleep(2 * time.Second)
				continue
			}
			log.Println("Cosmos DB initialized successfully")
			return
		}
		log.Fatal("Failed to initialize Cosmos DB after 30 attempts")
	}()

	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	r := setupRouter(svc)
	log.Printf("Starting server on port %s", port)
	if err := r.Run(":" + port); err != nil {
		log.Fatalf("Server failed: %v", err)
	}
}
