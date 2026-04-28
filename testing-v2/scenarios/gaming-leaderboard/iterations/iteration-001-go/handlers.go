package main

import (
	"context"
	"fmt"
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
)

// Handlers holds the CosmosDB reference for HTTP handlers.
type Handlers struct {
	db *CosmosDB
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
		ID:            req.PlayerID,
		PlayerID:      req.PlayerID,
		DisplayName:   req.DisplayName,
		Region:        req.Region,
		TotalGames:    0,
		TotalScore:    0,
		BestScore:     0,
		AverageScore:  0,
		Type:          "player",
		SchemaVersion: 1,
	}

	created, err := h.db.CreatePlayer(c.Request.Context(), player)
	if err != nil {
		if isConflict(err) {
			c.JSON(http.StatusConflict, gin.H{"error": "player already exists"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusCreated, toPlayerResponse(created))
}

func (h *Handlers) GetPlayer(c *gin.Context) {
	playerID := c.Param("playerId")
	player, err := h.db.GetPlayer(c.Request.Context(), playerID)
	if err != nil {
		if isNotFound(err) {
			c.JSON(http.StatusNotFound, gin.H{"error": "player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, toPlayerResponse(player))
}

func (h *Handlers) UpdatePlayer(c *gin.Context) {
	playerID := c.Param("playerId")
	var req UpdatePlayerRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	ctx := c.Request.Context()

	player, err := h.db.GetPlayer(ctx, playerID)
	if err != nil {
		if isNotFound(err) {
			c.JSON(http.StatusNotFound, gin.H{"error": "player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	oldRegion := player.Region

	if req.DisplayName != nil {
		player.DisplayName = *req.DisplayName
	}
	if req.Region != nil {
		player.Region = *req.Region
	}

	updated, err := h.db.ReplacePlayer(ctx, player, player.ETag)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// If region changed, update leaderboard entries
	if req.Region != nil && *req.Region != oldRegion {
		h.updateLeaderboardsForRegionChange(ctx, updated, oldRegion)
	} else if req.DisplayName != nil {
		// Update display name in leaderboards
		h.updateLeaderboardDisplayName(ctx, updated)
	}

	c.JSON(http.StatusOK, toPlayerResponse(updated))
}

func (h *Handlers) updateLeaderboardsForRegionChange(ctx context.Context, player Player, oldRegion string) {
	// Delete old regional entry
	oldKey := leaderboardKeyForRegion(oldRegion)
	_ = h.db.DeleteLeaderboardEntry(ctx, oldKey, player.PlayerID)

	// Upsert new regional entry if player has scores
	if player.TotalGames > 0 {
		newKey := leaderboardKeyForRegion(player.Region)
		entry := LeaderboardEntry{
			ID:             player.PlayerID,
			LeaderboardKey: newKey,
			PlayerID:       player.PlayerID,
			DisplayName:    player.DisplayName,
			Score:          player.BestScore,
			Region:         player.Region,
			Type:           "leaderboardEntry",
			SchemaVersion:  1,
		}
		_ = h.db.UpsertLeaderboardEntry(ctx, entry)

		// Update global entry with new region
		globalEntry := LeaderboardEntry{
			ID:             player.PlayerID,
			LeaderboardKey: "global",
			PlayerID:       player.PlayerID,
			DisplayName:    player.DisplayName,
			Score:          player.BestScore,
			Region:         player.Region,
			Type:           "leaderboardEntry",
			SchemaVersion:  1,
		}
		_ = h.db.UpsertLeaderboardEntry(ctx, globalEntry)
	}
}

func (h *Handlers) updateLeaderboardDisplayName(ctx context.Context, player Player) {
	if player.TotalGames == 0 {
		return
	}
	// Update global
	globalEntry := LeaderboardEntry{
		ID:             player.PlayerID,
		LeaderboardKey: "global",
		PlayerID:       player.PlayerID,
		DisplayName:    player.DisplayName,
		Score:          player.BestScore,
		Region:         player.Region,
		Type:           "leaderboardEntry",
		SchemaVersion:  1,
	}
	_ = h.db.UpsertLeaderboardEntry(ctx, globalEntry)

	// Update regional
	regionalKey := leaderboardKeyForRegion(player.Region)
	regionalEntry := LeaderboardEntry{
		ID:             player.PlayerID,
		LeaderboardKey: regionalKey,
		PlayerID:       player.PlayerID,
		DisplayName:    player.DisplayName,
		Score:          player.BestScore,
		Region:         player.Region,
		Type:           "leaderboardEntry",
		SchemaVersion:  1,
	}
	_ = h.db.UpsertLeaderboardEntry(ctx, regionalEntry)
}

func (h *Handlers) DeletePlayer(c *gin.Context) {
	playerID := c.Param("playerId")
	ctx := c.Request.Context()

	player, err := h.db.GetPlayer(ctx, playerID)
	if err != nil {
		if isNotFound(err) {
			c.JSON(http.StatusNotFound, gin.H{"error": "player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// Delete player document
	if err := h.db.DeletePlayer(ctx, playerID); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// Delete all scores
	_ = h.db.DeleteScoresForPlayer(ctx, playerID)

	// Delete leaderboard entries
	_ = h.db.DeleteLeaderboardEntry(ctx, "global", playerID)
	_ = h.db.DeleteLeaderboardEntry(ctx, leaderboardKeyForRegion(player.Region), playerID)

	c.Status(http.StatusNoContent)
}

func (h *Handlers) SubmitScore(c *gin.Context) {
	var req SubmitScoreRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	if req.PlayerID == "" || req.Score == nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "playerId and score are required"})
		return
	}

	if *req.Score < 0 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "score must be >= 0"})
		return
	}

	ctx := c.Request.Context()
	scoreValue := *req.Score

	// Verify player exists
	player, err := h.db.GetPlayer(ctx, req.PlayerID)
	if err != nil {
		if isNotFound(err) {
			c.JSON(http.StatusNotFound, gin.H{"error": "player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// Create score document
	scoreID := uuid.New().String()
	score := Score{
		ID:            scoreID,
		ScoreID:       scoreID,
		PlayerID:      req.PlayerID,
		Score:         scoreValue,
		GameMode:      req.GameMode,
		Timestamp:     nowISO(),
		Type:          "score",
		SchemaVersion: 1,
	}

	if err := h.db.CreateScore(ctx, score); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// Update player stats with optimistic concurrency retry
	var updatedPlayer Player
	for i := 0; i < 20; i++ {
		if i > 0 {
			// Re-read player for fresh ETag
			player, err = h.db.GetPlayer(ctx, req.PlayerID)
			if err != nil {
				c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
				return
			}
		}

		player.TotalGames++
		player.TotalScore += int64(scoreValue)
		if scoreValue > player.BestScore {
			player.BestScore = scoreValue
		}
		if player.TotalGames > 0 {
			player.AverageScore = float64(player.TotalScore) / float64(player.TotalGames)
		}

		updatedPlayer, err = h.db.ReplacePlayer(ctx, player, player.ETag)
		if err != nil {
			if isPreconditionFailed(err) {
				// Re-read and retry — reset player for next iteration
				player, err = h.db.GetPlayer(ctx, req.PlayerID)
				if err != nil {
					c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
					return
				}
				continue
			}
			c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
			return
		}
		break
	}

	// Update leaderboard entries
	globalEntry := LeaderboardEntry{
		ID:             updatedPlayer.PlayerID,
		LeaderboardKey: "global",
		PlayerID:       updatedPlayer.PlayerID,
		DisplayName:    updatedPlayer.DisplayName,
		Score:          updatedPlayer.BestScore,
		Region:         updatedPlayer.Region,
		Type:           "leaderboardEntry",
		SchemaVersion:  1,
	}
	_ = h.db.UpsertLeaderboardEntry(ctx, globalEntry)

	regionalEntry := LeaderboardEntry{
		ID:             updatedPlayer.PlayerID,
		LeaderboardKey: leaderboardKeyForRegion(updatedPlayer.Region),
		PlayerID:       updatedPlayer.PlayerID,
		DisplayName:    updatedPlayer.DisplayName,
		Score:          updatedPlayer.BestScore,
		Region:         updatedPlayer.Region,
		Type:           "leaderboardEntry",
		SchemaVersion:  1,
	}
	_ = h.db.UpsertLeaderboardEntry(ctx, regionalEntry)

	c.JSON(http.StatusCreated, ScoreCreateResponse{
		ScoreID:  scoreID,
		PlayerID: req.PlayerID,
		Score:    scoreValue,
	})
}

func (h *Handlers) GetScores(c *gin.Context) {
	playerID := c.Param("playerId")
	ctx := c.Request.Context()

	// Check player exists
	_, err := h.db.GetPlayer(ctx, playerID)
	if err != nil {
		if isNotFound(err) {
			c.JSON(http.StatusNotFound, gin.H{"error": "player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	limit := 10
	if l := c.Query("limit"); l != "" {
		if parsed, err := strconv.Atoi(l); err == nil && parsed > 0 {
			limit = parsed
		}
	}
	if limit > 100 {
		limit = 100
	}

	scores, err := h.db.GetScores(ctx, playerID, limit)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	result := make([]ScoreResponse, 0, len(scores))
	for _, s := range scores {
		result = append(result, ScoreResponse{
			ScoreID:   s.ScoreID,
			PlayerID:  s.PlayerID,
			Score:     s.Score,
			GameMode:  s.GameMode,
			Timestamp: s.Timestamp,
		})
	}

	c.JSON(http.StatusOK, result)
}

func (h *Handlers) GetGlobalLeaderboard(c *gin.Context) {
	top := parseTop(c)
	if top == 0 {
		c.JSON(http.StatusOK, []LeaderboardResponse{})
		return
	}

	entries, err := h.db.GetLeaderboard(c.Request.Context(), "global", top)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, toLeaderboardResponses(entries))
}

func (h *Handlers) GetRegionalLeaderboard(c *gin.Context) {
	region := c.Param("region")
	if region == "" {
		c.JSON(http.StatusOK, []LeaderboardResponse{})
		return
	}

	top := parseTop(c)
	if top == 0 {
		c.JSON(http.StatusOK, []LeaderboardResponse{})
		return
	}

	key := leaderboardKeyForRegion(region)
	entries, err := h.db.GetLeaderboard(c.Request.Context(), key, top)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, toLeaderboardResponses(entries))
}

func (h *Handlers) GetPlayerRank(c *gin.Context) {
	playerID := c.Param("playerId")
	ctx := c.Request.Context()

	// Check player exists
	_, err := h.db.GetPlayer(ctx, playerID)
	if err != nil {
		if isNotFound(err) {
			c.JSON(http.StatusNotFound, gin.H{"error": "player not found"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// Get global leaderboard entry
	entry, err := h.db.GetLeaderboardEntry(ctx, "global", playerID)
	if err != nil {
		if isNotFound(err) {
			c.JSON(http.StatusNotFound, gin.H{"error": "player has no scores"})
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// Calculate rank
	above, err := h.db.CountPlayersAbove(ctx, "global", entry.Score)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	sameScoreLowerName, err := h.db.CountPlayersWithSameScoreAndLowerName(ctx, "global", entry.Score, entry.DisplayName)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	rank := above + sameScoreLowerName + 1

	// Get neighbors ±10
	neighbors, err := h.db.GetNeighbors(ctx, "global", rank, 10)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	neighborResponses := make([]LeaderboardResponse, 0, len(neighbors))
	for i, n := range neighbors {
		// Determine rank of each neighbor based on their position in the sorted list
		nAbove, _ := h.db.CountPlayersAbove(ctx, "global", n.Score)
		nSame, _ := h.db.CountPlayersWithSameScoreAndLowerName(ctx, "global", n.Score, n.DisplayName)
		nRank := nAbove + nSame + 1
		_ = i
		neighborResponses = append(neighborResponses, LeaderboardResponse{
			Rank:        nRank,
			PlayerID:    n.PlayerID,
			DisplayName: n.DisplayName,
			Score:       n.Score,
		})
	}

	c.JSON(http.StatusOK, RankResponse{
		PlayerID:  playerID,
		Rank:      rank,
		Score:     entry.Score,
		Neighbors: neighborResponses,
	})
}

func parseTop(c *gin.Context) int {
	top := 100
	if t := c.Query("top"); t != "" {
		if parsed, err := strconv.Atoi(t); err == nil {
			if parsed <= 0 {
				return 0
			}
			top = parsed
		}
	}
	if top > 100 {
		top = 100
	}
	return top
}

func toPlayerResponse(p Player) PlayerResponse {
	avg := p.AverageScore
	if p.TotalGames > 0 {
		avg = float64(p.TotalScore) / float64(p.TotalGames)
	}
	return PlayerResponse{
		PlayerID:     p.PlayerID,
		DisplayName:  p.DisplayName,
		Region:       p.Region,
		TotalGames:   p.TotalGames,
		BestScore:    p.BestScore,
		AverageScore: avg,
	}
}

func toLeaderboardResponses(entries []LeaderboardEntry) []LeaderboardResponse {
	result := make([]LeaderboardResponse, 0, len(entries))
	for i, e := range entries {
		result = append(result, LeaderboardResponse{
			Rank:        i + 1,
			PlayerID:    e.PlayerID,
			DisplayName: e.DisplayName,
			Score:       e.Score,
		})
	}
	return result
}

func init() {
	// Ensure fmt is used
	_ = fmt.Sprintf
}
