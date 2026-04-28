package main

import (
	"context"
	"crypto/tls"
	"fmt"
	"log"
	"net/http"
	"os"

	"github.com/Azure/azure-sdk-for-go/sdk/data/azcosmos"
	"github.com/gin-gonic/gin"
)

const (
	databaseName         = "gaming-leaderboard"
	playersContainer     = "players"
	scoresContainer      = "scores"
	leaderboardContainer = "leaderboards"
)

var (
	cosmosClient    *azcosmos.Client
	playersCC       *azcosmos.ContainerClient
	scoresCC        *azcosmos.ContainerClient
	leaderboardCC   *azcosmos.ContainerClient
)

func main() {
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
		log.Fatalf("Failed to create credential: %v", err)
	}

	// Configure for emulator: skip TLS verification, use Gateway mode
	http.DefaultTransport = &http.Transport{
		TLSClientConfig: &tls.Config{InsecureSkipVerify: true},
	}

	clientOpts := &azcosmos.ClientOptions{}
	cosmosClient, err = azcosmos.NewClientWithKey(endpoint, cred, clientOpts)
	if err != nil {
		log.Fatalf("Failed to create Cosmos client: %v", err)
	}

	ctx := context.Background()
	if err := initDatabase(ctx); err != nil {
		log.Fatalf("Failed to initialize database: %v", err)
	}

	gin.SetMode(gin.ReleaseMode)
	r := gin.Default()
	setupRoutes(r)

	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}
	log.Printf("Server starting on port %s", port)
	if err := r.Run(fmt.Sprintf("0.0.0.0:%s", port)); err != nil {
		log.Fatalf("Failed to start server: %v", err)
	}
}

func initDatabase(ctx context.Context) error {
	dbProps := azcosmos.DatabaseProperties{ID: databaseName}
	_, err := cosmosClient.CreateDatabase(ctx, dbProps, nil)
	if err != nil && !isConflict(err) {
		return fmt.Errorf("create database: %w", err)
	}

	dbClient, err := cosmosClient.NewDatabase(databaseName)
	if err != nil {
		return fmt.Errorf("get database client: %w", err)
	}

	// Players container: partition key /playerId
	playersPK := azcosmos.ContainerProperties{
		ID: playersContainer,
		PartitionKeyDefinition: azcosmos.PartitionKeyDefinition{
			Paths: []string{"/playerId"},
		},
	}
	_, err = dbClient.CreateContainer(ctx, playersPK, nil)
	if err != nil && !isConflict(err) {
		return fmt.Errorf("create players container: %w", err)
	}

	// Scores container: partition key /playerId
	scoresPK := azcosmos.ContainerProperties{
		ID: scoresContainer,
		PartitionKeyDefinition: azcosmos.PartitionKeyDefinition{
			Paths: []string{"/playerId"},
		},
	}
	_, err = dbClient.CreateContainer(ctx, scoresPK, nil)
	if err != nil && !isConflict(err) {
		return fmt.Errorf("create scores container: %w", err)
	}

	// Leaderboard container: partition key /leaderboardScope
	lbPK := azcosmos.ContainerProperties{
		ID: leaderboardContainer,
		PartitionKeyDefinition: azcosmos.PartitionKeyDefinition{
			Paths: []string{"/leaderboardScope"},
		},
	}
	_, err = dbClient.CreateContainer(ctx, lbPK, nil)
	if err != nil && !isConflict(err) {
		return fmt.Errorf("create leaderboard container: %w", err)
	}

	// Get container clients
	playersCC, err = cosmosClient.NewContainer(databaseName, playersContainer)
	if err != nil {
		return fmt.Errorf("get players container client: %w", err)
	}
	scoresCC, err = cosmosClient.NewContainer(databaseName, scoresContainer)
	if err != nil {
		return fmt.Errorf("get scores container client: %w", err)
	}
	leaderboardCC, err = cosmosClient.NewContainer(databaseName, leaderboardContainer)
	if err != nil {
		return fmt.Errorf("get leaderboard container client: %w", err)
	}

	return nil
}

func setupRoutes(r *gin.Engine) {
	r.GET("/health", healthHandler)

	api := r.Group("/api")
	{
		api.POST("/players", createPlayerHandler)
		api.GET("/players/:playerId", getPlayerHandler)
		api.PATCH("/players/:playerId", updatePlayerHandler)
		api.DELETE("/players/:playerId", deletePlayerHandler)
		api.GET("/players/:playerId/scores", getPlayerScoresHandler)
		api.GET("/players/:playerId/rank", getPlayerRankHandler)

		api.POST("/scores", submitScoreHandler)

		api.GET("/leaderboards/global", globalLeaderboardHandler)
		api.GET("/leaderboards/regional/:region", regionalLeaderboardHandler)
	}
}
