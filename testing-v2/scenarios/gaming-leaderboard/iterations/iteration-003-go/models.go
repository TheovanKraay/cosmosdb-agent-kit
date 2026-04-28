package main

// Player represents a player profile document in Cosmos DB.
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
	SchemaVersion string `json:"schemaVersion"`
	ETag         string  `json:"_etag,omitempty"`
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

// Score represents a score document in Cosmos DB.
type Score struct {
	ID        string `json:"id"`
	ScoreID   string `json:"scoreId"`
	PlayerID  string `json:"playerId"`
	Score     int    `json:"score"`
	GameMode  string `json:"gameMode,omitempty"`
	Timestamp string `json:"timestamp"`
	Type      string `json:"type"`
}

// ScoreResponse is the API response for score submission.
type ScoreResponse struct {
	ScoreID  string `json:"scoreId"`
	PlayerID string `json:"playerId"`
	Score    int    `json:"score"`
}

// ScoreHistoryEntry is the API response for score history.
type ScoreHistoryEntry struct {
	ScoreID   string `json:"scoreId"`
	PlayerID  string `json:"playerId"`
	Score     int    `json:"score"`
	GameMode  string `json:"gameMode,omitempty"`
	Timestamp string `json:"timestamp"`
}

// LeaderboardEntry represents a leaderboard document in Cosmos DB.
type LeaderboardEntry struct {
	ID              string `json:"id"`
	PlayerID        string `json:"playerId"`
	DisplayName     string `json:"displayName"`
	Score           int    `json:"score"`
	Region          string `json:"region"`
	LeaderboardScope string `json:"leaderboardScope"`
	Type            string `json:"type"`
}

// LeaderboardResponse is the API response for leaderboard entries.
type LeaderboardResponse struct {
	Rank        int    `json:"rank"`
	PlayerID    string `json:"playerId"`
	DisplayName string `json:"displayName"`
	Score       int    `json:"score"`
}

// PlayerRankResponse is the API response for player rank.
type PlayerRankResponse struct {
	PlayerID  string                `json:"playerId"`
	Rank      int                   `json:"rank"`
	Score     int                   `json:"score"`
	Neighbors []LeaderboardResponse `json:"neighbors"`
}

// CreatePlayerRequest represents the request body for creating a player.
type CreatePlayerRequest struct {
	PlayerID    string `json:"playerId"`
	DisplayName string `json:"displayName"`
	Region      string `json:"region"`
}

// UpdatePlayerRequest represents the request body for updating a player.
type UpdatePlayerRequest struct {
	DisplayName *string `json:"displayName,omitempty"`
	Region      *string `json:"region,omitempty"`
}

// SubmitScoreRequest represents the request body for submitting a score.
type SubmitScoreRequest struct {
	PlayerID string `json:"playerId"`
	Score    *int   `json:"score"`
	GameMode string `json:"gameMode,omitempty"`
}
