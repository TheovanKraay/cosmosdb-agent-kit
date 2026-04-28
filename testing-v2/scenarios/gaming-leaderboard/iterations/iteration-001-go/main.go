package main

import (
	"context"
	"crypto/tls"
	"log"
	"net/http"
	"os"

	"github.com/gin-gonic/gin"
)

func tlsConfig() *tls.Config {
	return &tls.Config{InsecureSkipVerify: true} //nolint:gosec // emulator uses self-signed cert
}

func main() {
	endpoint := os.Getenv("COSMOS_ENDPOINT")
	key := os.Getenv("COSMOS_KEY")
	if endpoint == "" || key == "" {
		log.Fatal("COSMOS_ENDPOINT and COSMOS_KEY environment variables are required")
	}

	db, err := NewCosmosDB(endpoint, key)
	if err != nil {
		log.Fatalf("Failed to create Cosmos DB client: %v", err)
	}

	if err := db.EnsureDatabase(context.Background()); err != nil {
		log.Fatalf("Failed to initialize database: %v", err)
	}

	h := &Handlers{db: db}

	r := gin.Default()

	r.GET("/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"status": "ok"})
	})

	r.POST("/api/players", h.CreatePlayer)
	r.GET("/api/players/:playerId", h.GetPlayer)
	r.PATCH("/api/players/:playerId", h.UpdatePlayer)
	r.DELETE("/api/players/:playerId", h.DeletePlayer)

	r.POST("/api/scores", h.SubmitScore)
	r.GET("/api/players/:playerId/scores", h.GetScores)
	r.GET("/api/players/:playerId/rank", h.GetPlayerRank)

	r.GET("/api/leaderboards/global", h.GetGlobalLeaderboard)
	r.GET("/api/leaderboards/regional/:region", h.GetRegionalLeaderboard)

	if err := r.Run(":8080"); err != nil {
		log.Fatalf("Failed to start server: %v", err)
	}
}
