package main

import (
	"context"
	"log"
	"os"
	"time"

	"github.com/gin-gonic/gin"
)

const (
	defaultEndpoint = "https://localhost:8081"
	defaultKey      = "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw=="
	databaseName    = "gaming-leaderboard"
)

func main() {
	endpoint := os.Getenv("COSMOS_ENDPOINT")
	if endpoint == "" {
		endpoint = defaultEndpoint
	}
	key := os.Getenv("COSMOS_KEY")
	if key == "" {
		key = defaultKey
	}

	repo, err := NewCosmosRepository(endpoint, key)
	if err != nil {
		log.Fatalf("Failed to create Cosmos repository: %v", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	if err := repo.InitializeDatabase(ctx, databaseName); err != nil {
		log.Fatalf("Failed to initialize database: %v", err)
	}

	log.Println("Database and containers initialized successfully")

	handlers := NewHandlers(repo)

	r := gin.Default()

	r.GET("/health", handlers.Health)
	r.POST("/api/players", handlers.CreatePlayer)
	r.GET("/api/players/:playerId", handlers.GetPlayer)
	r.PATCH("/api/players/:playerId", handlers.UpdatePlayer)
	r.DELETE("/api/players/:playerId", handlers.DeletePlayer)
	r.POST("/api/scores", handlers.SubmitScore)
	r.GET("/api/players/:playerId/scores", handlers.GetPlayerScores)
	r.GET("/api/leaderboards/global", handlers.GetGlobalLeaderboard)
	r.GET("/api/leaderboards/regional/:region", handlers.GetRegionalLeaderboard)
	r.GET("/api/players/:playerId/rank", handlers.GetPlayerRank)

	log.Println("Starting server on :8080")
	if err := r.Run(":8080"); err != nil {
		log.Fatalf("Failed to start server: %v", err)
	}
}
