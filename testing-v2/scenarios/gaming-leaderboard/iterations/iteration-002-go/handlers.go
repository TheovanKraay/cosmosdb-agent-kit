package main

import (
	"errors"
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
)

// Handlers holds the Cosmos repository and provides HTTP handler methods.
type Handlers struct {
	repo *CosmosRepository
}

func NewHandlers(repo *CosmosRepository) *Handlers {
	return &Handlers{repo: repo}
}

func (h *Handlers) Health(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{"status": "ok"})
}

func (h *Handlers) CreatePlayer(c *gin.Context) {
	var req CreatePlayerRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	if req.PlayerID == "" || req.DisplayName == "" || req.Region == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "playerId, displayName, and region are required"})
		return
	}

	player := Player{
		PlayerID:    req.PlayerID,
		DisplayName: req.DisplayName,
		Region:      req.Region,
	}

	created, err := h.repo.CreatePlayer(c.Request.Context(), player)
	if err != nil {
		if errors.Is(err, ErrConflict) {
			c.JSON(http.StatusConflict, gin.H{"error": "player already exists"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusCreated, CreatePlayerResponse{
		PlayerID:     created.PlayerID,
		DisplayName:  created.DisplayName,
		Region:       created.Region,
		TotalGames:   created.TotalGames,
		BestScore:    created.BestScore,
		AverageScore: created.AverageScore,
	})
}

func (h *Handlers) GetPlayer(c *gin.Context) {
	playerID := c.Param("playerId")

	player, err := h.repo.GetPlayer(c.Request.Context(), playerID)
	if err != nil {
		if errors.Is(err, ErrNotFound) {
			c.JSON(http.StatusNotFound, gin.H{"error": "player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
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

func (h *Handlers) UpdatePlayer(c *gin.Context) {
	playerID := c.Param("playerId")

	var req UpdatePlayerRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	ctx := c.Request.Context()

	player, err := h.repo.GetPlayer(ctx, playerID)
	if err != nil {
		if errors.Is(err, ErrNotFound) {
			c.JSON(http.StatusNotFound, gin.H{"error": "player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	oldRegion := player.Region
	displayNameChanged := false

	if req.DisplayName != nil {
		player.DisplayName = *req.DisplayName
		displayNameChanged = true
	}
	if req.Region != nil {
		player.Region = *req.Region
	}

	updated, err := h.repo.ReplacePlayer(ctx, player, nil)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// Handle region change: delete old regional entry, create new one
	regionChanged := req.Region != nil && *req.Region != oldRegion
	if regionChanged {
		_ = h.repo.DeleteLeaderboardEntry(ctx, "regional-"+oldRegion, playerID)

		if updated.BestScore > 0 {
			regionalEntry := LeaderboardEntry{
				ID:              playerID,
				LeaderboardType: "regional-" + updated.Region,
				PlayerID:        playerID,
				DisplayName:     updated.DisplayName,
				Score:           updated.BestScore,
				Region:          updated.Region,
			}
			_ = h.repo.UpsertLeaderboardEntry(ctx, regionalEntry)
		}
	}

	// Update global leaderboard entry if displayName or region changed
	if displayNameChanged || regionChanged {
		if updated.BestScore > 0 {
			globalEntry := LeaderboardEntry{
				ID:              playerID,
				LeaderboardType: "global",
				PlayerID:        playerID,
				DisplayName:     updated.DisplayName,
				Score:           updated.BestScore,
				Region:          updated.Region,
			}
			_ = h.repo.UpsertLeaderboardEntry(ctx, globalEntry)
		}
	}

	c.JSON(http.StatusOK, PlayerResponse{
		PlayerID:     updated.PlayerID,
		DisplayName:  updated.DisplayName,
		Region:       updated.Region,
		TotalGames:   updated.TotalGames,
		BestScore:    updated.BestScore,
		AverageScore: updated.AverageScore,
	})
}

func (h *Handlers) DeletePlayer(c *gin.Context) {
	playerID := c.Param("playerId")
	ctx := c.Request.Context()

	// Get player first to know the region
	player, err := h.repo.GetPlayer(ctx, playerID)
	if err != nil {
		if errors.Is(err, ErrNotFound) {
			c.JSON(http.StatusNotFound, gin.H{"error": "player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// Delete all scores for the player
	if err := h.repo.DeleteScoresForPlayer(ctx, playerID); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// Delete leaderboard entries
	if err := h.repo.DeleteAllLeaderboardEntriesForPlayer(ctx, playerID, player.Region); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// Delete the player document
	if err := h.repo.DeletePlayer(ctx, playerID); err != nil {
		if errors.Is(err, ErrNotFound) {
			c.JSON(http.StatusNotFound, gin.H{"error": "player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.Status(http.StatusNoContent)
}

func (h *Handlers) SubmitScore(c *gin.Context) {
	var req CreateScoreRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	if req.PlayerID == "" || req.Score == nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "playerId and score are required"})
		return
	}

	if *req.Score < 0 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "score must be non-negative"})
		return
	}

	ctx := c.Request.Context()

	// Verify player exists
	_, err := h.repo.GetPlayer(ctx, req.PlayerID)
	if err != nil {
		if errors.Is(err, ErrNotFound) {
			c.JSON(http.StatusNotFound, gin.H{"error": "player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// Create score document
	score, err := h.repo.CreateScore(ctx, req.PlayerID, *req.Score, req.GameMode)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// Update player stats with ETag-based optimistic concurrency
	if err := h.repo.UpdatePlayerStatsWithRetry(ctx, req.PlayerID, *req.Score); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusCreated, CreateScoreResponse{
		ScoreID:  score.ID,
		PlayerID: score.PlayerID,
		Score:    score.Score,
	})
}

func (h *Handlers) GetPlayerScores(c *gin.Context) {
	playerID := c.Param("playerId")
	ctx := c.Request.Context()

	// Verify player exists
	_, err := h.repo.GetPlayer(ctx, playerID)
	if err != nil {
		if errors.Is(err, ErrNotFound) {
			c.JSON(http.StatusNotFound, gin.H{"error": "player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	limit := 10
	if l := c.Query("limit"); l != "" {
		parsed, err := strconv.Atoi(l)
		if err == nil && parsed > 0 {
			limit = parsed
		}
	}
	if limit > 100 {
		limit = 100
	}

	scores, err := h.repo.GetScores(ctx, playerID, limit)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	result := make([]ScoreResponse, 0, len(scores))
	for _, s := range scores {
		result = append(result, ScoreResponse{
			ScoreID:   s.ID,
			PlayerID:  s.PlayerID,
			Score:     s.Score,
			GameMode:  s.GameMode,
			Timestamp: s.Timestamp,
		})
	}

	c.JSON(http.StatusOK, result)
}

func (h *Handlers) GetGlobalLeaderboard(c *gin.Context) {
	top := 100
	if t := c.Query("top"); t != "" {
		parsed, err := strconv.Atoi(t)
		if err == nil && parsed > 0 {
			top = parsed
		}
	}
	if top > 100 {
		top = 100
	}

	entries, err := h.repo.GetLeaderboard(c.Request.Context(), "global", top)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	result := make([]RankedEntry, 0, len(entries))
	for i, e := range entries {
		result = append(result, RankedEntry{
			Rank:        i + 1,
			PlayerID:    e.PlayerID,
			DisplayName: e.DisplayName,
			Score:       e.Score,
		})
	}

	c.JSON(http.StatusOK, result)
}

func (h *Handlers) GetRegionalLeaderboard(c *gin.Context) {
	region := c.Param("region")

	top := 100
	if t := c.Query("top"); t != "" {
		parsed, err := strconv.Atoi(t)
		if err == nil && parsed > 0 {
			top = parsed
		}
	}
	if top > 100 {
		top = 100
	}

	entries, err := h.repo.GetLeaderboard(c.Request.Context(), "regional-"+region, top)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	result := make([]RankedEntry, 0, len(entries))
	for i, e := range entries {
		result = append(result, RankedEntry{
			Rank:        i + 1,
			PlayerID:    e.PlayerID,
			DisplayName: e.DisplayName,
			Score:       e.Score,
		})
	}

	c.JSON(http.StatusOK, result)
}

func (h *Handlers) GetPlayerRank(c *gin.Context) {
	playerID := c.Param("playerId")
	ctx := c.Request.Context()

	// Verify player exists
	_, err := h.repo.GetPlayer(ctx, playerID)
	if err != nil {
		if errors.Is(err, ErrNotFound) {
			c.JSON(http.StatusNotFound, gin.H{"error": "player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// Get all global leaderboard entries sorted
	entries, err := h.repo.GetAllLeaderboardEntries(ctx)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// Find the player's position
	playerIndex := -1
	for i, e := range entries {
		if e.PlayerID == playerID {
			playerIndex = i
			break
		}
	}

	if playerIndex == -1 {
		c.JSON(http.StatusNotFound, gin.H{"error": "player has no scores"})
		return
	}

	playerRank := playerIndex + 1
	playerScore := entries[playerIndex].Score

	// Get neighbors ±10 positions
	start := playerIndex - 10
	if start < 0 {
		start = 0
	}
	end := playerIndex + 11
	if end > len(entries) {
		end = len(entries)
	}

	neighbors := make([]RankedEntry, 0, end-start)
	for i := start; i < end; i++ {
		if i == playerIndex {
			continue
		}
		neighbors = append(neighbors, RankedEntry{
			Rank:        i + 1,
			PlayerID:    entries[i].PlayerID,
			DisplayName: entries[i].DisplayName,
			Score:       entries[i].Score,
		})
	}

	c.JSON(http.StatusOK, PlayerRankResponse{
		PlayerID:  playerID,
		Rank:      playerRank,
		Score:     playerScore,
		Neighbors: neighbors,
	})
}
