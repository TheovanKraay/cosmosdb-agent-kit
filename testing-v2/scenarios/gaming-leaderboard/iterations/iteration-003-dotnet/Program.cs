using Microsoft.Azure.Cosmos;
using GamingLeaderboard.Models;
using GamingLeaderboard.Services;
using System.Text.Json;

var builder = WebApplication.CreateBuilder(args);

// Configure JSON serialization for camelCase
builder.Services.ConfigureHttpJsonOptions(options =>
{
    options.SerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.CamelCase;
});

// Configure Cosmos DB client as singleton (SDK best practice)
var cosmosEndpoint = Environment.GetEnvironmentVariable("COSMOS_ENDPOINT")
    ?? "https://localhost:8081";
var cosmosKey = Environment.GetEnvironmentVariable("COSMOS_KEY")
    ?? "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==";

var cosmosClientOptions = new CosmosClientOptions
{
    ConnectionMode = ConnectionMode.Gateway,
    HttpClientFactory = () =>
    {
        var handler = new HttpClientHandler
        {
            ServerCertificateCustomValidationCallback =
                HttpClientHandler.DangerousAcceptAnyServerCertificateValidator
        };
        return new HttpClient(handler);
    }
};

var cosmosClient = new CosmosClient(cosmosEndpoint, cosmosKey, cosmosClientOptions);
var cosmosService = new CosmosDbService(cosmosClient, "gaming-leaderboard");

// Initialize database and containers
await cosmosService.InitializeAsync();

builder.Services.AddSingleton(cosmosService);

var app = builder.Build();

// ---- Health Endpoint ----
app.MapGet("/health", () => Results.Ok(new { status = "healthy" }));

// ---- Player Management ----

app.MapPost("/api/players", async (CreatePlayerRequest? request, CosmosDbService db) =>
{
    if (request == null ||
        string.IsNullOrWhiteSpace(request.PlayerId) ||
        string.IsNullOrWhiteSpace(request.DisplayName) ||
        string.IsNullOrWhiteSpace(request.Region))
    {
        return Results.BadRequest(new { error = "playerId, displayName, and region are required" });
    }

    var player = new Player
    {
        Id = request.PlayerId,
        PlayerId = request.PlayerId,
        DisplayName = request.DisplayName,
        Region = request.Region,
        TotalGames = 0,
        BestScore = 0,
        AverageScore = 0,
        TotalScore = 0
    };

    try
    {
        var created = await db.CreatePlayerAsync(player);
        return Results.Created($"/api/players/{created.PlayerId}", new PlayerResponse
        {
            PlayerId = created.PlayerId,
            DisplayName = created.DisplayName,
            Region = created.Region,
            TotalGames = created.TotalGames,
            BestScore = created.BestScore,
            AverageScore = created.AverageScore
        });
    }
    catch (CosmosException ex) when (ex.StatusCode == System.Net.HttpStatusCode.Conflict)
    {
        return Results.Conflict(new { error = $"Player {request.PlayerId} already exists" });
    }
});

app.MapGet("/api/players/{playerId}", async (string playerId, CosmosDbService db) =>
{
    var player = await db.GetPlayerAsync(playerId);
    if (player == null)
    {
        return Results.NotFound(new { error = $"Player {playerId} not found" });
    }

    return Results.Ok(new PlayerResponse
    {
        PlayerId = player.PlayerId,
        DisplayName = player.DisplayName,
        Region = player.Region,
        TotalGames = player.TotalGames,
        BestScore = player.BestScore,
        AverageScore = player.AverageScore
    });
});

app.MapPatch("/api/players/{playerId}", async (string playerId, UpdatePlayerRequest? request, CosmosDbService db) =>
{
    var player = await db.GetPlayerAsync(playerId);
    if (player == null)
    {
        return Results.NotFound(new { error = $"Player {playerId} not found" });
    }

    if (request?.DisplayName != null)
    {
        player.DisplayName = request.DisplayName;
    }
    if (request?.Region != null)
    {
        string oldRegion = player.Region;
        player.Region = request.Region;

        // Update leaderboard entries if region changed
        if (oldRegion != request.Region && player.BestScore > 0)
        {
            await db.DeleteRegionalLeaderboardEntryAsync(oldRegion, playerId);
            var regionEntry = new LeaderboardEntry
            {
                Id = $"region_{request.Region}_{playerId}",
                PlayerId = playerId,
                DisplayName = player.DisplayName,
                Region = request.Region,
                Score = player.BestScore,
                LeaderboardKey = $"region_{request.Region}"
            };
            await db.UpsertLeaderboardEntryAsync(regionEntry);
        }
    }

    // Update display name in leaderboard entries if changed
    if (request?.DisplayName != null && player.BestScore > 0)
    {
        var globalEntry = new LeaderboardEntry
        {
            Id = $"global_{playerId}",
            PlayerId = playerId,
            DisplayName = player.DisplayName,
            Region = player.Region,
            Score = player.BestScore,
            LeaderboardKey = "global"
        };
        await db.UpsertLeaderboardEntryAsync(globalEntry);

        var regionalEntry = new LeaderboardEntry
        {
            Id = $"region_{player.Region}_{playerId}",
            PlayerId = playerId,
            DisplayName = player.DisplayName,
            Region = player.Region,
            Score = player.BestScore,
            LeaderboardKey = $"region_{player.Region}"
        };
        await db.UpsertLeaderboardEntryAsync(regionalEntry);
    }

    var updated = await db.UpdatePlayerAsync(player);
    return Results.Ok(new PlayerResponse
    {
        PlayerId = updated.PlayerId,
        DisplayName = updated.DisplayName,
        Region = updated.Region,
        TotalGames = updated.TotalGames,
        BestScore = updated.BestScore,
        AverageScore = updated.AverageScore
    });
});

app.MapDelete("/api/players/{playerId}", async (string playerId, CosmosDbService db) =>
{
    var player = await db.GetPlayerAsync(playerId);
    if (player == null)
    {
        return Results.NotFound(new { error = $"Player {playerId} not found" });
    }

    // Delete leaderboard entries
    await db.DeleteLeaderboardEntriesForPlayerAsync(playerId);
    await db.DeleteRegionalLeaderboardEntryAsync(player.Region, playerId);

    // Delete all scores
    await db.DeletePlayerScoresAsync(playerId);

    // Delete the player
    await db.DeletePlayerAsync(playerId);

    return Results.NoContent();
});

// ---- Score Submission ----

app.MapPost("/api/scores", async (SubmitScoreRequest? request, CosmosDbService db) =>
{
    if (request == null ||
        string.IsNullOrWhiteSpace(request.PlayerId) ||
        request.Score == null)
    {
        return Results.BadRequest(new { error = "playerId and score are required" });
    }

    if (request.Score < 0)
    {
        return Results.BadRequest(new { error = "score must be a positive integer" });
    }

    // Verify player exists
    var player = await db.GetPlayerAsync(request.PlayerId);
    if (player == null)
    {
        return Results.NotFound(new { error = $"Player {request.PlayerId} not found" });
    }

    var scoreId = Guid.NewGuid().ToString();
    var score = new Score
    {
        Id = scoreId,
        ScoreId = scoreId,
        PlayerId = request.PlayerId,
        ScoreValue = request.Score.Value,
        GameMode = request.GameMode,
        Timestamp = DateTime.UtcNow.ToString("o")
    };

    await db.CreateScoreAsync(score);

    // Update player stats with ETag-based optimistic concurrency
    var updatedPlayer = await db.UpdatePlayerStatsWithRetryAsync(request.PlayerId, request.Score.Value);

    // Update leaderboard entries (upsert for both global and regional)
    var globalEntry = new LeaderboardEntry
    {
        Id = $"global_{request.PlayerId}",
        PlayerId = request.PlayerId,
        DisplayName = updatedPlayer.DisplayName,
        Region = updatedPlayer.Region,
        Score = updatedPlayer.BestScore,
        LeaderboardKey = "global"
    };
    await db.UpsertLeaderboardEntryAsync(globalEntry);

    var regionalEntry = new LeaderboardEntry
    {
        Id = $"region_{updatedPlayer.Region}_{request.PlayerId}",
        PlayerId = request.PlayerId,
        DisplayName = updatedPlayer.DisplayName,
        Region = updatedPlayer.Region,
        Score = updatedPlayer.BestScore,
        LeaderboardKey = $"region_{updatedPlayer.Region}"
    };
    await db.UpsertLeaderboardEntryAsync(regionalEntry);

    return Results.Created($"/api/scores/{scoreId}", new ScoreResponse
    {
        ScoreId = scoreId,
        PlayerId = request.PlayerId,
        Score = request.Score.Value
    });
});

// ---- Score History ----

app.MapGet("/api/players/{playerId}/scores", async (string playerId, int? limit, CosmosDbService db) =>
{
    // Verify player exists
    var player = await db.GetPlayerAsync(playerId);
    if (player == null)
    {
        return Results.NotFound(new { error = $"Player {playerId} not found" });
    }

    int effectiveLimit = Math.Clamp(limit ?? 10, 1, 100);
    var scores = await db.GetPlayerScoresAsync(playerId, effectiveLimit);

    var result = scores.Select(s => new ScoreHistoryItem
    {
        ScoreId = s.ScoreId,
        PlayerId = s.PlayerId,
        Score = s.ScoreValue,
        GameMode = s.GameMode,
        Timestamp = s.Timestamp
    }).ToList();

    return Results.Ok(result);
});

// ---- Leaderboards ----

app.MapGet("/api/leaderboards/global", async (int? top, CosmosDbService db) =>
{
    int effectiveTop = Math.Clamp(top ?? 100, 0, 100);
    if (effectiveTop == 0)
    {
        return Results.Ok(new List<LeaderboardEntryResponse>());
    }

    var entries = await db.GetLeaderboardAsync("global", effectiveTop);

    var result = entries.Select((e, idx) => new LeaderboardEntryResponse
    {
        Rank = idx + 1,
        PlayerId = e.PlayerId,
        DisplayName = e.DisplayName,
        Score = e.Score
    }).ToList();

    return Results.Ok(result);
});

app.MapGet("/api/leaderboards/regional/{region}", async (string region, int? top, CosmosDbService db) =>
{
    int effectiveTop = Math.Clamp(top ?? 100, 0, 100);
    if (effectiveTop == 0)
    {
        return Results.Ok(new List<LeaderboardEntryResponse>());
    }

    string leaderboardKey = $"region_{region}";
    var entries = await db.GetLeaderboardAsync(leaderboardKey, effectiveTop);

    var result = entries.Select((e, idx) => new LeaderboardEntryResponse
    {
        Rank = idx + 1,
        PlayerId = e.PlayerId,
        DisplayName = e.DisplayName,
        Score = e.Score
    }).ToList();

    return Results.Ok(result);
});

// ---- Player Rank ----

app.MapGet("/api/players/{playerId}/rank", async (string playerId, CosmosDbService db) =>
{
    var player = await db.GetPlayerAsync(playerId);
    if (player == null || player.BestScore == 0 && player.TotalGames == 0)
    {
        return Results.NotFound(new { error = $"Player {playerId} not found or has no scores" });
    }

    // Get full global leaderboard to determine rank
    var allEntries = await db.GetFullLeaderboardAsync("global");
    var playerIndex = allEntries.FindIndex(e => e.PlayerId == playerId);

    if (playerIndex < 0)
    {
        return Results.NotFound(new { error = $"Player {playerId} not found in leaderboard" });
    }

    int rank = playerIndex + 1;
    int startIdx = Math.Max(0, playerIndex - 10);
    int endIdx = Math.Min(allEntries.Count - 1, playerIndex + 10);

    var neighbors = new List<LeaderboardEntryResponse>();
    for (int i = startIdx; i <= endIdx; i++)
    {
        if (i == playerIndex) continue;
        neighbors.Add(new LeaderboardEntryResponse
        {
            Rank = i + 1,
            PlayerId = allEntries[i].PlayerId,
            DisplayName = allEntries[i].DisplayName,
            Score = allEntries[i].Score
        });
    }

    return Results.Ok(new PlayerRankResponse
    {
        PlayerId = playerId,
        Rank = rank,
        Score = allEntries[playerIndex].Score,
        Neighbors = neighbors
    });
});

// Configure the port
var port = Environment.GetEnvironmentVariable("PORT") ?? "5000";
app.Urls.Add($"http://0.0.0.0:{port}");

app.Run();
