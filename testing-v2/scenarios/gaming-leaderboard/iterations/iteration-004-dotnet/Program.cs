using System.Net;
using Microsoft.Azure.Cosmos;
using GamingLeaderboard.Models;
using GamingLeaderboard.Services;

var builder = WebApplication.CreateBuilder(args);

// Configure JSON serialization to use camelCase
builder.Services.ConfigureHttpJsonOptions(options =>
{
    options.SerializerOptions.PropertyNamingPolicy = System.Text.Json.JsonNamingPolicy.CamelCase;
});

// Read Cosmos DB configuration from environment variables
var cosmosEndpoint = Environment.GetEnvironmentVariable("COSMOS_ENDPOINT")
    ?? "https://localhost:8081";
var cosmosKey = Environment.GetEnvironmentVariable("COSMOS_KEY")
    ?? "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==";

// Create singleton CosmosClient with best practices:
// - Gateway mode for emulator compatibility
// - SSL bypass for emulator self-signed certificate
var cosmosClientOptions = new CosmosClientOptions
{
    ConnectionMode = ConnectionMode.Gateway,
    HttpClientFactory = () => new HttpClient(
        new HttpClientHandler
        {
            ServerCertificateCustomValidationCallback =
                HttpClientHandler.DangerousAcceptAnyServerCertificateValidator
        }),
    Serializer = new CosmosJsonNetSerializer()
};

var cosmosClient = new CosmosClient(cosmosEndpoint, cosmosKey, cosmosClientOptions);

// Register CosmosDbService as singleton (reuse CosmosClient)
var cosmosDbService = new CosmosDbService(cosmosClient, "gaming-leaderboard");
builder.Services.AddSingleton(cosmosDbService);
builder.Services.AddSingleton<PlayerRepository>();
builder.Services.AddSingleton<ScoreRepository>();
builder.Services.AddSingleton<LeaderboardRepository>();

var app = builder.Build();

// Initialize database and containers in the background
// Health check will work immediately; DB operations will wait for init
_ = Task.Run(async () =>
{
    try
    {
        await cosmosDbService.EnsureInitializedAsync();
    }
    catch (Exception ex)
    {
        Console.Error.WriteLine($"Background Cosmos DB init failed: {ex.Message}");
    }
});

// Middleware to ensure Cosmos DB is initialized before API requests (not health)
app.Use(async (context, next) =>
{
    if (context.Request.Path.StartsWithSegments("/api"))
    {
        await cosmosDbService.EnsureInitializedAsync();
    }
    await next();
});

// =====================
// Health Check
// =====================
app.MapGet("/health", () => Results.Ok(new { status = "healthy" }));

// =====================
// Player Management
// =====================

// POST /api/players - Create a new player
app.MapPost("/api/players", async (
    CreatePlayerRequest? request,
    PlayerRepository playerRepo) =>
{
    if (request == null || string.IsNullOrWhiteSpace(request.PlayerId)
        || string.IsNullOrWhiteSpace(request.DisplayName)
        || string.IsNullOrWhiteSpace(request.Region))
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
        TotalScore = 0,
        AverageScore = 0
    };

    try
    {
        var created = await playerRepo.CreatePlayerAsync(player);
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
    catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.Conflict)
    {
        return Results.Conflict(new { error = $"Player {request.PlayerId} already exists" });
    }
});

// GET /api/players/{playerId} - Get player profile
app.MapGet("/api/players/{playerId}", async (
    string playerId,
    PlayerRepository playerRepo) =>
{
    var player = await playerRepo.GetPlayerAsync(playerId);
    if (player == null) return Results.NotFound(new { error = "Player not found" });

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

// PATCH /api/players/{playerId} - Update player
app.MapPatch("/api/players/{playerId}", async (
    string playerId,
    UpdatePlayerRequest? request,
    PlayerRepository playerRepo,
    LeaderboardRepository leaderboardRepo) =>
{
    if (request == null)
        return Results.BadRequest(new { error = "Request body is required" });

    try
    {
        // Get existing player to check old region
        var existing = await playerRepo.GetPlayerAsync(playerId);
        if (existing == null) return Results.NotFound(new { error = "Player not found" });

        var oldRegion = existing.Region;
        var updated = await playerRepo.UpdatePlayerAsync(playerId, request.DisplayName, request.Region);

        // Update leaderboard entries for display name changes
        if (request.DisplayName != null)
        {
            await leaderboardRepo.UpdatePlayerDisplayNameAsync(playerId, request.DisplayName);
        }

        // Handle region changes - move leaderboard entry between partitions
        if (request.Region != null && request.Region != oldRegion)
        {
            await leaderboardRepo.UpdatePlayerRegionAsync(
                playerId, oldRegion, request.Region, updated.DisplayName, updated.BestScore);
        }

        return Results.Ok(new PlayerResponse
        {
            PlayerId = updated.PlayerId,
            DisplayName = updated.DisplayName,
            Region = updated.Region,
            TotalGames = updated.TotalGames,
            BestScore = updated.BestScore,
            AverageScore = updated.AverageScore
        });
    }
    catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.NotFound)
    {
        return Results.NotFound(new { error = "Player not found" });
    }
});

// DELETE /api/players/{playerId} - Delete player and all data
app.MapDelete("/api/players/{playerId}", async (
    string playerId,
    PlayerRepository playerRepo,
    ScoreRepository scoreRepo,
    LeaderboardRepository leaderboardRepo) =>
{
    var deleted = await playerRepo.DeletePlayerAsync(playerId);
    if (!deleted) return Results.NotFound(new { error = "Player not found" });

    // Delete associated scores and leaderboard entries
    await scoreRepo.DeletePlayerScoresAsync(playerId);
    await leaderboardRepo.DeletePlayerEntriesAsync(playerId);

    return Results.NoContent();
});

// =====================
// Score Submission
// =====================

// POST /api/scores - Submit a score
app.MapPost("/api/scores", async (
    SubmitScoreRequest? request,
    PlayerRepository playerRepo,
    ScoreRepository scoreRepo,
    LeaderboardRepository leaderboardRepo) =>
{
    if (request == null || string.IsNullOrWhiteSpace(request.PlayerId) || request.Score == null)
    {
        return Results.BadRequest(new { error = "playerId and score are required" });
    }

    if (request.Score < 0)
    {
        return Results.BadRequest(new { error = "Score must be a positive integer" });
    }

    // Verify player exists before recording score
    var player = await playerRepo.GetPlayerAsync(request.PlayerId);
    if (player == null)
    {
        return Results.BadRequest(new { error = $"Player {request.PlayerId} does not exist" });
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

    await scoreRepo.CreateScoreAsync(score);

    // Update player stats with ETag-based optimistic concurrency
    await playerRepo.UpdatePlayerStatsAsync(request.PlayerId, request.Score.Value);

    // Update leaderboard entries if this is a new best score
    var updatedPlayer = await playerRepo.GetPlayerAsync(request.PlayerId);
    if (updatedPlayer != null)
    {
        // Update global leaderboard entry
        var globalEntry = new LeaderboardEntry
        {
            Id = $"{request.PlayerId}_global_all-time",
            PlayerId = request.PlayerId,
            DisplayName = updatedPlayer.DisplayName,
            Region = updatedPlayer.Region,
            Score = updatedPlayer.BestScore,
            LeaderboardKey = "global_all-time"
        };
        await leaderboardRepo.UpsertEntryAsync(globalEntry);

        // Update regional leaderboard entry
        var regionalEntry = new LeaderboardEntry
        {
            Id = $"{request.PlayerId}_regional_{updatedPlayer.Region}",
            PlayerId = request.PlayerId,
            DisplayName = updatedPlayer.DisplayName,
            Region = updatedPlayer.Region,
            Score = updatedPlayer.BestScore,
            LeaderboardKey = $"regional_{updatedPlayer.Region}"
        };
        await leaderboardRepo.UpsertEntryAsync(regionalEntry);
    }

    return Results.Created($"/api/scores/{scoreId}", new ScoreResponse
    {
        ScoreId = scoreId,
        PlayerId = request.PlayerId,
        Score = request.Score.Value
    });
});

// =====================
// Score History
// =====================

// GET /api/players/{playerId}/scores - Get score history
app.MapGet("/api/players/{playerId}/scores", async (
    string playerId,
    int? limit,
    PlayerRepository playerRepo,
    ScoreRepository scoreRepo) =>
{
    // Verify player exists
    var player = await playerRepo.GetPlayerAsync(playerId);
    if (player == null) return Results.NotFound(new { error = "Player not found" });

    var effectiveLimit = Math.Max(0, Math.Min(limit ?? 10, 100));
    var scores = await scoreRepo.GetPlayerScoresAsync(playerId, effectiveLimit);

    var result = scores.Select(s => new ScoreHistoryEntry
    {
        ScoreId = s.ScoreId,
        PlayerId = s.PlayerId,
        Score = s.ScoreValue,
        GameMode = s.GameMode,
        Timestamp = s.Timestamp
    }).ToList();

    return Results.Ok(result);
});

// =====================
// Leaderboards
// =====================

// GET /api/leaderboards/global - Global leaderboard
app.MapGet("/api/leaderboards/global", async (
    int? top,
    LeaderboardRepository leaderboardRepo) =>
{
    var effectiveTop = Math.Max(0, Math.Min(top ?? 100, 100));
    var entries = await leaderboardRepo.GetGlobalLeaderboardAsync(effectiveTop);

    var result = entries.Select((e, idx) => new LeaderboardEntryResponse
    {
        Rank = idx + 1,
        PlayerId = e.PlayerId,
        DisplayName = e.DisplayName,
        Score = e.Score
    }).ToList();

    return Results.Ok(result);
});

// GET /api/leaderboards/regional/{region} - Regional leaderboard
app.MapGet("/api/leaderboards/regional/{region}", async (
    string region,
    int? top,
    LeaderboardRepository leaderboardRepo) =>
{
    var effectiveTop = Math.Max(0, Math.Min(top ?? 100, 100));
    var entries = await leaderboardRepo.GetRegionalLeaderboardAsync(region, effectiveTop);

    var result = entries.Select((e, idx) => new LeaderboardEntryResponse
    {
        Rank = idx + 1,
        PlayerId = e.PlayerId,
        DisplayName = e.DisplayName,
        Score = e.Score
    }).ToList();

    return Results.Ok(result);
});

// =====================
// Player Rank
// =====================

// GET /api/players/{playerId}/rank - Player rank + neighbors
app.MapGet("/api/players/{playerId}/rank", async (
    string playerId,
    PlayerRepository playerRepo,
    LeaderboardRepository leaderboardRepo) =>
{
    var player = await playerRepo.GetPlayerAsync(playerId);
    if (player == null || player.BestScore == 0)
        return Results.NotFound(new { error = "Player not found or has no scores" });

    // Get full global leaderboard to determine rank
    var allEntries = await leaderboardRepo.GetGlobalLeaderboardAsync(10000);
    var playerIndex = allEntries.FindIndex(e => e.PlayerId == playerId);

    if (playerIndex < 0)
        return Results.NotFound(new { error = "Player not found in leaderboard" });

    var rank = playerIndex + 1;

    // Get neighbors (±10 positions)
    var startIdx = Math.Max(0, playerIndex - 10);
    var endIdx = Math.Min(allEntries.Count - 1, playerIndex + 10);
    var neighbors = new List<LeaderboardEntryResponse>();

    for (int i = startIdx; i <= endIdx; i++)
    {
        if (i == playerIndex) continue; // Don't include the player themselves
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
        Score = player.BestScore,
        Neighbors = neighbors
    });
});

app.Run();

// Custom Newtonsoft.Json serializer for Cosmos DB SDK
public class CosmosJsonNetSerializer : Microsoft.Azure.Cosmos.CosmosSerializer
{
    private static readonly Newtonsoft.Json.JsonSerializerSettings Settings = new()
    {
        NullValueHandling = Newtonsoft.Json.NullValueHandling.Ignore,
        Formatting = Newtonsoft.Json.Formatting.None,
        ContractResolver = new Newtonsoft.Json.Serialization.DefaultContractResolver()
    };

    public override T FromStream<T>(Stream stream)
    {
        using var sr = new StreamReader(stream);
        using var jsonReader = new Newtonsoft.Json.JsonTextReader(sr);
        var serializer = Newtonsoft.Json.JsonSerializer.Create(Settings);
        return serializer.Deserialize<T>(jsonReader)!;
    }

    public override Stream ToStream<T>(T input)
    {
        var stream = new MemoryStream();
        using (var sw = new StreamWriter(stream, leaveOpen: true))
        using (var writer = new Newtonsoft.Json.JsonTextWriter(sw))
        {
            var serializer = Newtonsoft.Json.JsonSerializer.Create(Settings);
            serializer.Serialize(writer, input);
            writer.Flush();
            sw.Flush();
        }
        stream.Position = 0;
        return stream;
    }
}
