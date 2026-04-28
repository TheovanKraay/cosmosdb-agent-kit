package main

import "time"

// Player represents a player profile stored in the players container.
type Player struct {
	ID            string  `json:"id"`
	PlayerID      string  `json:"playerId"`
	DisplayName   string  `json:"displayName"`
	Region        string  `json:"region"`
	TotalGames    int     `json:"totalGames"`
	TotalScore    int64   `json:"totalScore"`
	BestScore     int     `json:"bestScore"`
	AverageScore  float64 `json:"averageScore"`
	Type          string  `json:"type"`
	SchemaVersion int     `json:"schemaVersion"`
	ETag          string  `json:"_etag,omitempty"`
}

// PlayerResponse is the JSON response for player endpoints.
type PlayerResponse struct {
	PlayerID     string  `json:"playerId"`
	DisplayName  string  `json:"displayName"`
	Region       string  `json:"region"`
	TotalGames   int     `json:"totalGames"`
	BestScore    int     `json:"bestScore"`
	AverageScore float64 `json:"averageScore"`
}

// Score represents an individual score record in the scores container.
type Score struct {
	ID            string `json:"id"`
	ScoreID       string `json:"scoreId"`
	PlayerID      string `json:"playerId"`
	Score         int    `json:"score"`
	GameMode      string `json:"gameMode,omitempty"`
	Timestamp     string `json:"timestamp"`
	Type          string `json:"type"`
	SchemaVersion int    `json:"schemaVersion"`
}

// ScoreResponse is the JSON response for score endpoints.
type ScoreResponse struct {
	ScoreID   string `json:"scoreId"`
	PlayerID  string `json:"playerId"`
	Score     int    `json:"score"`
	GameMode  string `json:"gameMode,omitempty"`
	Timestamp string `json:"timestamp"`
}

// ScoreCreateResponse is returned after creating a score.
type ScoreCreateResponse struct {
	ScoreID  string `json:"scoreId"`
	PlayerID string `json:"playerId"`
	Score    int    `json:"score"`
}

// LeaderboardEntry represents a denormalized leaderboard entry.
type LeaderboardEntry struct {
	ID             string `json:"id"`
	LeaderboardKey string `json:"leaderboardKey"`
	PlayerID       string `json:"playerId"`
	DisplayName    string `json:"displayName"`
	Score          int    `json:"score"`
	Region         string `json:"region"`
	Type           string `json:"type"`
	SchemaVersion  int    `json:"schemaVersion"`
}

// LeaderboardResponse is the JSON response for leaderboard items.
type LeaderboardResponse struct {
	Rank        int    `json:"rank"`
	PlayerID    string `json:"playerId"`
	DisplayName string `json:"displayName"`
	Score       int    `json:"score"`
}

// RankResponse is the response for the player rank endpoint.
type RankResponse struct {
	PlayerID  string                `json:"playerId"`
	Rank      int                   `json:"rank"`
	Score     int                   `json:"score"`
	Neighbors []LeaderboardResponse `json:"neighbors"`
}

// CreatePlayerRequest is the request body for POST /api/players.
type CreatePlayerRequest struct {
	PlayerID    string `json:"playerId"`
	DisplayName string `json:"displayName"`
	Region      string `json:"region"`
}

// UpdatePlayerRequest is the request body for PATCH /api/players/:playerId.
type UpdatePlayerRequest struct {
	DisplayName *string `json:"displayName,omitempty"`
	Region      *string `json:"region,omitempty"`
}

// SubmitScoreRequest is the request body for POST /api/scores.
type SubmitScoreRequest struct {
	PlayerID string `json:"playerId"`
	Score    *int   `json:"score"`
	GameMode string `json:"gameMode,omitempty"`
}

func nowISO() string {
	return time.Now().UTC().Format(time.RFC3339)
}
