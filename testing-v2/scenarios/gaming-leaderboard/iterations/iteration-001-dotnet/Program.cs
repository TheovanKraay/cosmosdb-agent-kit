using Microsoft.Azure.Cosmos;
using GamingLeaderboard.Models;
using GamingLeaderboard.Services;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Net;

var builder = WebApplication.CreateBuilder(args);

builder.WebHost.UseUrls("http://0.0.0.0:5000");

// Configure JSON serialization for API responses
builder.Services.ConfigureHttpJsonOptions(options =>
{
    options.SerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.CamelCase;
    options.SerializerOptions.DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull;
});

// Cosmos DB configuration
string cosmosEndpoint = Environment.GetEnvironmentVariable("COSMOS_ENDPOINT") ?? "https://localhost:8081";
string cosmosKey = Environment.GetEnvironmentVariable("COSMOS_KEY") ?? "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==";
const string DatabaseName = "gaming-leaderboard";

bool isEmulator = cosmosEndpoint.Contains("localhost", StringComparison.OrdinalIgnoreCase) ||
                  cosmosEndpoint.Contains("127.0.0.1", StringComparison.OrdinalIgnoreCase);

var cosmosClientOptions = new CosmosClientOptions
{
    ConnectionMode = isEmulator ? ConnectionMode.Gateway : ConnectionMode.Direct,
    HttpClientFactory = isEmulator ? () =>
    {
        var handler = new HttpClientHandler
        {
            ServerCertificateCustomValidationCallback = HttpClientHandler.DangerousAcceptAnyServerCertificateValidator
        };
        return new HttpClient(handler);
    } : null,
    SerializerOptions = new CosmosSerializationOptions
    {
        PropertyNamingPolicy = CosmosPropertyNamingPolicy.CamelCase
    }
};

var cosmosClient = new CosmosClient(cosmosEndpoint, cosmosKey, cosmosClientOptions);
builder.Services.AddSingleton(cosmosClient);

// Initialize database and containers
await InitializeCosmosDbAsync(cosmosClient);

builder.Services.AddSingleton(new CosmosDbService(cosmosClient, DatabaseName));

var app = builder.Build();

// Health endpoint
app.MapGet("/health", () => Results.Ok(new { status = "healthy" }));

// POST /api/players
app.MapPost("/api/players", async (HttpContext context, CosmosDbService db) =>
{
    var body = await context.Request.ReadFromJsonAsync<JsonElement>();

    var player = new Player
    {
        PlayerId = body.GetProperty("playerId").GetString()!,
        DisplayName = body.GetProperty("displayName").GetString()!,
        Region = body.GetProperty("region").GetString()!,
        TotalGames = 0,
        BestScore = 0,
        AverageScore = 0,
        TotalScore = 0
    };

    var created = await db.CreatePlayerAsync(player);

    return Results.Created($"/api/players/{created.PlayerId}", new
    {
        playerId = created.PlayerId,
        displayName = created.DisplayName,
        region = created.Region,
        totalGames = created.TotalGames,
        bestScore = created.BestScore,
        averageScore = created.AverageScore
    });
});

// GET /api/players/{playerId}
app.MapGet("/api/players/{playerId}", async (string playerId, CosmosDbService db) =>
{
    var player = await db.GetPlayerAsync(playerId);
    if (player == null) return Results.NotFound();

    return Results.Ok(new
    {
        playerId = player.PlayerId,
        displayName = player.DisplayName,
        region = player.Region,
        totalGames = player.TotalGames,
        bestScore = player.BestScore,
        averageScore = player.AverageScore
    });
});

// PATCH /api/players/{playerId}
app.MapPatch("/api/players/{playerId}", async (string playerId, HttpContext context, CosmosDbService db) =>
{
    var player = await db.GetPlayerAsync(playerId);
    if (player == null) return Results.NotFound();

    var body = await context.Request.ReadFromJsonAsync<JsonElement>();

    if (body.TryGetProperty("displayName", out var displayNameProp))
        player.DisplayName = displayNameProp.GetString()!;

    if (body.TryGetProperty("region", out var regionProp))
        player.Region = regionProp.GetString()!;

    var updated = await db.UpdatePlayerAsync(player);

    return Results.Ok(new
    {
        playerId = updated.PlayerId,
        displayName = updated.DisplayName,
        region = updated.Region,
        totalGames = updated.TotalGames,
        bestScore = updated.BestScore,
        averageScore = updated.AverageScore
    });
});

// DELETE /api/players/{playerId}
app.MapDelete("/api/players/{playerId}", async (string playerId, CosmosDbService db) =>
{
    var deleted = await db.DeletePlayerAsync(playerId);
    return deleted ? Results.NoContent() : Results.NotFound();
});

// POST /api/scores
app.MapPost("/api/scores", async (HttpContext context, CosmosDbService db) =>
{
    var body = await context.Request.ReadFromJsonAsync<JsonElement>();

    var playerId = body.GetProperty("playerId").GetString()!;
    var scoreValue = body.GetProperty("score").GetInt32();
    string? gameMode = body.TryGetProperty("gameMode", out var gm) ? gm.GetString() : null;

    try
    {
        var score = await db.SubmitScoreAsync(playerId, scoreValue, gameMode);
        return Results.Created($"/api/scores/{score.ScoreId}", new
        {
            scoreId = score.ScoreId,
            playerId = score.PlayerId,
            score = score.ScoreValue
        });
    }
    catch (InvalidOperationException)
    {
        return Results.NotFound();
    }
});

// GET /api/players/{playerId}/scores
app.MapGet("/api/players/{playerId}/scores", async (string playerId, int? limit, CosmosDbService db) =>
{
    var player = await db.GetPlayerAsync(playerId);
    if (player == null) return Results.NotFound();

    var effectiveLimit = limit ?? 10;
    if (effectiveLimit < 1) effectiveLimit = 1;
    if (effectiveLimit > 100) effectiveLimit = 100;

    var scores = await db.GetPlayerScoresAsync(playerId, effectiveLimit);

    var result = scores.Select(s => new
    {
        scoreId = s.ScoreId,
        playerId = s.PlayerId,
        score = s.ScoreValue,
        gameMode = s.GameMode,
        timestamp = s.Timestamp
    });

    return Results.Ok(result);
});

// GET /api/leaderboards/global
app.MapGet("/api/leaderboards/global", async (int? top, CosmosDbService db) =>
{
    var effectiveTop = Math.Min(top ?? 100, 100);
    if (effectiveTop < 1) effectiveTop = 1;

    var entries = await db.GetGlobalLeaderboardAsync(effectiveTop);

    var result = entries.Select((e, i) => new
    {
        rank = i + 1,
        playerId = e.PlayerId,
        displayName = e.DisplayName,
        score = e.Score
    });

    return Results.Ok(result);
});

// GET /api/leaderboards/regional/{region}
app.MapGet("/api/leaderboards/regional/{region}", async (string region, int? top, CosmosDbService db) =>
{
    var effectiveTop = Math.Min(top ?? 100, 100);
    if (effectiveTop < 1) effectiveTop = 1;

    var entries = await db.GetRegionalLeaderboardAsync(region, effectiveTop);

    var result = entries.Select((e, i) => new
    {
        rank = i + 1,
        playerId = e.PlayerId,
        displayName = e.DisplayName,
        score = e.Score
    });

    return Results.Ok(result);
});

// GET /api/players/{playerId}/rank
app.MapGet("/api/players/{playerId}/rank", async (string playerId, CosmosDbService db) =>
{
    // Check player exists
    var player = await db.GetPlayerAsync(playerId);
    if (player == null) return Results.NotFound();

    var (playerEntry, allEntries) = await db.GetPlayerRankDataAsync(playerId);
    if (playerEntry == null) return Results.NotFound();

    // Find player's index
    int playerIndex = -1;
    for (int i = 0; i < allEntries.Count; i++)
    {
        if (allEntries[i].PlayerId == playerId)
        {
            playerIndex = i;
            break;
        }
    }

    if (playerIndex < 0) return Results.NotFound();

    int playerRank = playerIndex + 1;

    // Get ±10 neighbors
    int startIndex = Math.Max(0, playerIndex - 10);
    int endIndex = Math.Min(allEntries.Count - 1, playerIndex + 10);

    var neighbors = new List<object>();
    for (int i = startIndex; i <= endIndex; i++)
    {
        if (i == playerIndex) continue;
        neighbors.Add(new
        {
            rank = i + 1,
            playerId = allEntries[i].PlayerId,
            displayName = allEntries[i].DisplayName,
            score = allEntries[i].Score
        });
    }

    return Results.Ok(new
    {
        playerId = playerId,
        rank = playerRank,
        score = playerEntry.Score,
        neighbors = neighbors
    });
});

app.Run();

async Task InitializeCosmosDbAsync(CosmosClient client)
{
    // Create database with autoscale throughput (400 RU/s max)
    var databaseResponse = await client.CreateDatabaseIfNotExistsAsync(
        DatabaseName,
        ThroughputProperties.CreateAutoscaleThroughput(400));

    var database = databaseResponse.Database;

    // Players container with partition key /playerId
    var playersContainerProperties = new ContainerProperties("players", "/playerId")
    {
        IndexingPolicy = new IndexingPolicy
        {
            Automatic = true,
            IndexingMode = IndexingMode.Consistent,
            IncludedPaths = { new IncludedPath { Path = "/*" } },
            ExcludedPaths =
            {
                new ExcludedPath { Path = "/totalScore/?" },
                new ExcludedPath { Path = "/\"_etag\"/?" }
            }
        }
    };
    await database.CreateContainerIfNotExistsAsync(playersContainerProperties);

    // Scores container with partition key /playerId
    var scoresContainerProperties = new ContainerProperties("scores", "/playerId")
    {
        IndexingPolicy = new IndexingPolicy
        {
            Automatic = true,
            IndexingMode = IndexingMode.Consistent,
            IncludedPaths = { new IncludedPath { Path = "/*" } },
            ExcludedPaths =
            {
                new ExcludedPath { Path = "/gameMode/?" },
                new ExcludedPath { Path = "/\"_etag\"/?" }
            }
        }
    };
    await database.CreateContainerIfNotExistsAsync(scoresContainerProperties);

    // Leaderboards container with partition key /leaderboardKey
    var leaderboardsIndexingPolicy = new IndexingPolicy
    {
        Automatic = true,
        IndexingMode = IndexingMode.Consistent,
        IncludedPaths = { new IncludedPath { Path = "/*" } },
        ExcludedPaths =
        {
            new ExcludedPath { Path = "/region/?" },
            new ExcludedPath { Path = "/\"_etag\"/?" }
        }
    };

    // Composite index for tiebreaking: score DESC, displayName ASC
    leaderboardsIndexingPolicy.CompositeIndexes.Add(new System.Collections.ObjectModel.Collection<CompositePath>
    {
        new CompositePath { Path = "/score", Order = CompositePathSortOrder.Descending },
        new CompositePath { Path = "/displayName", Order = CompositePathSortOrder.Ascending }
    });

    var leaderboardsContainerProperties = new ContainerProperties("leaderboards", "/leaderboardKey")
    {
        IndexingPolicy = leaderboardsIndexingPolicy
    };
    await database.CreateContainerIfNotExistsAsync(leaderboardsContainerProperties);
}
