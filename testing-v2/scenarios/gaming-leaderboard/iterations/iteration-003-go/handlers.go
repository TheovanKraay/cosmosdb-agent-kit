package main

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"sort"
	"strconv"
	"time"

	"github.com/Azure/azure-sdk-for-go/sdk/data/azcosmos"
	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
)

func healthHandler(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{"status": "ok"})
}

func createPlayerHandler(c *gin.Context) {
	var req CreatePlayerRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid request body"})
		return
	}

	if req.PlayerID == "" || req.DisplayName == "" || req.Region == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "playerId, displayName, and region are required"})
		return
	}

	player := Player{
		ID:            req.PlayerID,
		PlayerID:      req.PlayerID,
		DisplayName:   req.DisplayName,
		Region:        req.Region,
		TotalGames:    0,
		BestScore:     0,
		TotalScore:    0,
		AverageScore:  0,
		Type:          "player",
		SchemaVersion: "1.0",
	}

	data, err := json.Marshal(player)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to marshal player"})
		return
	}

	pk := azcosmos.NewPartitionKeyString(req.PlayerID)

	// CreateItem returns 409 Conflict if the document already exists
	_, err = playersCC.CreateItem(context.Background(), pk, data, nil)
	if err != nil {
		if isConflict(err) {
			c.JSON(http.StatusConflict, gin.H{"error": "Player already exists"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("Failed to create player: %v", err)})
		return
	}

	c.JSON(http.StatusCreated, PlayerResponse{
		PlayerID:     player.PlayerID,
		DisplayName:  player.DisplayName,
		Region:       player.Region,
		TotalGames:   player.TotalGames,
		BestScore:    player.BestScore,
		AverageScore: player.AverageScore,
	})
}

func getPlayerHandler(c *gin.Context) {
	playerID := c.Param("playerId")

	player, err := readPlayer(context.Background(), playerID)
	if err != nil {
		if isNotFound(err) {
			c.JSON(http.StatusNotFound, gin.H{"error": "Player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("Failed to get player: %v", err)})
		return
	}

	c.JSON(http.StatusOK, PlayerResponse{
		PlayerID:     player.PlayerID,
		DisplayName:  player.DisplayName,
		Region:       player.Region,
		TotalGames:   player.TotalGames,
		BestScore:    player.BestScore,
		AverageScore: player.AverageScore,
	})
}

func updatePlayerHandler(c *gin.Context) {
	playerID := c.Param("playerId")

	var req UpdatePlayerRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid request body"})
		return
	}

	ctx := context.Background()

	// Read-modify-write with ETag for optimistic concurrency
	for retries := 0; retries < 10; retries++ {
		player, etag, err := readPlayerWithETag(ctx, playerID)
		if err != nil {
			if isNotFound(err) {
				c.JSON(http.StatusNotFound, gin.H{"error": "Player not found"})
				return
			}
			c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("Failed to read player: %v", err)})
			return
		}

		oldRegion := player.Region

		if req.DisplayName != nil {
			player.DisplayName = *req.DisplayName
		}
		if req.Region != nil {
			player.Region = *req.Region
		}

		data, _ := json.Marshal(player)
		pk := azcosmos.NewPartitionKeyString(playerID)
		opts := &azcosmos.ItemOptions{
			IfMatchEtag: &etag,
		}

		_, err = playersCC.ReplaceItem(ctx, pk, player.ID, data, opts)
		if err != nil {
			if isPreconditionFailed(err) {
				continue // Retry with new ETag
			}
			c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("Failed to update player: %v", err)})
			return
		}

		// Update leaderboard entries if display name or region changed
		if req.DisplayName != nil || req.Region != nil {
			updateLeaderboardEntries(ctx, player, oldRegion, req.Region != nil)
		}

		c.JSON(http.StatusOK, PlayerResponse{
			PlayerID:     player.PlayerID,
			DisplayName:  player.DisplayName,
			Region:       player.Region,
			TotalGames:   player.TotalGames,
			BestScore:    player.BestScore,
			AverageScore: player.AverageScore,
		})
		return
	}

	c.JSON(http.StatusConflict, gin.H{"error": "Failed to update player after retries"})
}

func deletePlayerHandler(c *gin.Context) {
	playerID := c.Param("playerId")
	ctx := context.Background()

	// Check player exists and get their region for leaderboard cleanup
	player, err := readPlayer(ctx, playerID)
	if err != nil {
		if isNotFound(err) {
			c.JSON(http.StatusNotFound, gin.H{"error": "Player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("Failed to read player: %v", err)})
		return
	}

	pk := azcosmos.NewPartitionKeyString(playerID)

	// Delete all scores for this player
	scores, err := queryScoresByPlayer(ctx, playerID, 1000)
	if err == nil {
		for _, s := range scores {
			_, _ = scoresCC.DeleteItem(ctx, pk, s.ID, nil)
		}
	}

	// Delete player document
	_, err = playersCC.DeleteItem(ctx, pk, playerID, nil)
	if err != nil && !isNotFound(err) {
		c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("Failed to delete player: %v", err)})
		return
	}

	// Delete leaderboard entries for this player
	deleteLeaderboardEntriesForPlayer(ctx, playerID, player.Region)

	c.Status(http.StatusNoContent)
}

func submitScoreHandler(c *gin.Context) {
	var req SubmitScoreRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid request body"})
		return
	}

	if req.PlayerID == "" {
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

	// Verify player exists
	player, err := readPlayer(ctx, req.PlayerID)
	if err != nil {
		if isNotFound(err) {
			c.JSON(http.StatusBadRequest, gin.H{"error": "Player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("Failed to read player: %v", err)})
		return
	}

	// Create score document
	scoreID := uuid.New().String()
	score := Score{
		ID:        scoreID,
		ScoreID:   scoreID,
		PlayerID:  req.PlayerID,
		Score:     *req.Score,
		GameMode:  req.GameMode,
		Timestamp: time.Now().UTC().Format(time.RFC3339),
		Type:      "score",
	}

	data, _ := json.Marshal(score)
	pk := azcosmos.NewPartitionKeyString(req.PlayerID)

	_, err = scoresCC.CreateItem(ctx, pk, data, nil)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("Failed to create score: %v", err)})
		return
	}

	// Update player stats with ETag-based optimistic concurrency
	if err := updatePlayerStats(ctx, req.PlayerID, *req.Score); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("Failed to update stats: %v", err)})
		return
	}

	// Update leaderboard entries
	upsertLeaderboardEntry(ctx, player, *req.Score)

	c.JSON(http.StatusCreated, ScoreResponse{
		ScoreID:  score.ScoreID,
		PlayerID: score.PlayerID,
		Score:    score.Score,
	})
}

func getPlayerScoresHandler(c *gin.Context) {
	playerID := c.Param("playerId")
	limitStr := c.DefaultQuery("limit", "10")
	limit, err := strconv.Atoi(limitStr)
	if err != nil || limit < 1 {
		limit = 10
	}
	if limit > 100 {
		limit = 100
	}

	ctx := context.Background()

	// Check player exists
	_, err = readPlayer(ctx, playerID)
	if err != nil {
		if isNotFound(err) {
			c.JSON(http.StatusNotFound, gin.H{"error": "Player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("Failed to read player: %v", err)})
		return
	}

	scores, err := queryScoresByPlayer(ctx, playerID, limit)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("Failed to get scores: %v", err)})
		return
	}

	result := make([]ScoreHistoryEntry, len(scores))
	for i, s := range scores {
		result[i] = ScoreHistoryEntry{
			ScoreID:   s.ScoreID,
			PlayerID:  s.PlayerID,
			Score:     s.Score,
			GameMode:  s.GameMode,
			Timestamp: s.Timestamp,
		}
	}

	c.JSON(http.StatusOK, result)
}

func globalLeaderboardHandler(c *gin.Context) {
	topStr := c.DefaultQuery("top", "100")
	top, err := strconv.Atoi(topStr)
	if err != nil || top < 1 {
		top = 100
	}
	if top > 100 {
		top = 100
	}

	ctx := context.Background()
	entries, err := queryLeaderboard(ctx, "global", top)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("Failed to get leaderboard: %v", err)})
		return
	}

	// Sort by score DESC, then displayName ASC for tiebreaking
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

	c.JSON(http.StatusOK, result)
}

func regionalLeaderboardHandler(c *gin.Context) {
	region := c.Param("region")
	topStr := c.DefaultQuery("top", "100")
	top, err := strconv.Atoi(topStr)
	if err != nil || top < 1 {
		top = 100
	}
	if top > 100 {
		top = 100
	}

	ctx := context.Background()
	scope := "regional_" + region
	entries, err := queryLeaderboard(ctx, scope, top)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("Failed to get leaderboard: %v", err)})
		return
	}

	// Sort by score DESC, then displayName ASC for tiebreaking
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

	c.JSON(http.StatusOK, result)
}

func getPlayerRankHandler(c *gin.Context) {
	playerID := c.Param("playerId")
	ctx := context.Background()

	// Check player exists
	player, err := readPlayer(ctx, playerID)
	if err != nil {
		if isNotFound(err) {
			c.JSON(http.StatusNotFound, gin.H{"error": "Player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("Failed to read player: %v", err)})
		return
	}

	if player.BestScore == 0 && player.TotalGames == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "Player has no scores"})
		return
	}

	// Get all global leaderboard entries to compute rank
	entries, err := queryLeaderboard(ctx, "global", 10000)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": fmt.Sprintf("Failed to get leaderboard: %v", err)})
		return
	}

	// Sort by score DESC, then displayName ASC
	sort.Slice(entries, func(i, j int) bool {
		if entries[i].Score != entries[j].Score {
			return entries[i].Score > entries[j].Score
		}
		return entries[i].DisplayName < entries[j].DisplayName
	})

	// Find player's position
	playerRank := -1
	for i, e := range entries {
		if e.PlayerID == playerID {
			playerRank = i + 1
			break
		}
	}

	if playerRank == -1 {
		c.JSON(http.StatusNotFound, gin.H{"error": "Player not found on leaderboard"})
		return
	}

	// Get neighbors (±10 positions)
	startIdx := playerRank - 11
	if startIdx < 0 {
		startIdx = 0
	}
	endIdx := playerRank + 10
	if endIdx > len(entries) {
		endIdx = len(entries)
	}

	neighbors := make([]LeaderboardResponse, 0)
	for i := startIdx; i < endIdx; i++ {
		if i == playerRank-1 {
			continue // Skip the player themselves
		}
		neighbors = append(neighbors, LeaderboardResponse{
			Rank:        i + 1,
			PlayerID:    entries[i].PlayerID,
			DisplayName: entries[i].DisplayName,
			Score:       entries[i].Score,
		})
	}

	c.JSON(http.StatusOK, PlayerRankResponse{
		PlayerID:  playerID,
		Rank:      playerRank,
		Score:     player.BestScore,
		Neighbors: neighbors,
	})
}
