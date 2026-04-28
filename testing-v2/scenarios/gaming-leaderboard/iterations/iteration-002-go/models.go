package main

// Player represents a player document in the players container.
type Player struct {
	ID           string  `json:"id"`
	PlayerID     string  `json:"playerId"`
	Type         string  `json:"type"`
	DisplayName  string  `json:"displayName"`
	Region       string  `json:"region"`
	TotalGames   int     `json:"totalGames"`
	BestScore    int     `json:"bestScore"`
	AverageScore float64 `json:"averageScore"`
	ETag         string  `json:"_etag,omitempty"`
}

// Score represents a score document in the players container.
type Score struct {
	ID        string `json:"id"`
	PlayerID  string `json:"playerId"`
	Type      string `json:"type"`
	Score     int    `json:"score"`
	GameMode  string `json:"gameMode"`
	Timestamp string `json:"timestamp"`
}

// LeaderboardEntry represents an entry in the leaderboard container.
type LeaderboardEntry struct {
	ID              string `json:"id"`
	LeaderboardType string `json:"leaderboardType"`
	PlayerID        string `json:"playerId"`
	DisplayName     string `json:"displayName"`
	Score           int    `json:"score"`
	Region          string `json:"region"`
}

// RankedEntry is a leaderboard entry with a rank assigned.
type RankedEntry struct {
	Rank        int    `json:"rank"`
	PlayerID    string `json:"playerId"`
	DisplayName string `json:"displayName"`
	Score       int    `json:"score"`
}

// PlayerRankResponse is the response for the player rank endpoint.
type PlayerRankResponse struct {
	PlayerID  string        `json:"playerId"`
	Rank      int           `json:"rank"`
	Score     int           `json:"score"`
	Neighbors []RankedEntry `json:"neighbors"`
}

// CreatePlayerRequest is the request body for creating a player.
type CreatePlayerRequest struct {
	PlayerID    string `json:"playerId"`
	DisplayName string `json:"displayName"`
	Region      string `json:"region"`
}

// UpdatePlayerRequest is the request body for updating a player.
type UpdatePlayerRequest struct {
	DisplayName *string `json:"displayName,omitempty"`
	Region      *string `json:"region,omitempty"`
}

// CreateScoreRequest is the request body for submitting a score.
type CreateScoreRequest struct {
	PlayerID string `json:"playerId"`
	Score    *int   `json:"score"`
	GameMode string `json:"gameMode,omitempty"`
}

// CreatePlayerResponse is the response for creating a player.
type CreatePlayerResponse struct {
	PlayerID     string  `json:"playerId"`
	DisplayName  string  `json:"displayName"`
	Region       string  `json:"region"`
	TotalGames   int     `json:"totalGames"`
	BestScore    int     `json:"bestScore"`
	AverageScore float64 `json:"averageScore"`
}

// PlayerResponse is the response for getting a player.
type PlayerResponse struct {
	PlayerID     string  `json:"playerId"`
	DisplayName  string  `json:"displayName"`
	Region       string  `json:"region"`
	TotalGames   int     `json:"totalGames"`
	BestScore    int     `json:"bestScore"`
	AverageScore float64 `json:"averageScore"`
}

// ScoreResponse is the response for a score.
type ScoreResponse struct {
	ScoreID   string `json:"scoreId"`
	PlayerID  string `json:"playerId"`
	Score     int    `json:"score"`
	GameMode  string `json:"gameMode"`
	Timestamp string `json:"timestamp"`
}

// CreateScoreResponse is the response for submitting a score.
type CreateScoreResponse struct {
	ScoreID  string `json:"scoreId"`
	PlayerID string `json:"playerId"`
	Score    int    `json:"score"`
}
