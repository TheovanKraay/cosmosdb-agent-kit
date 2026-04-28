using Microsoft.AspNetCore.Mvc;
using Microsoft.Azure.Cosmos;
using GamingLeaderboard.Models;
using GamingLeaderboard.Services;

namespace GamingLeaderboard.Controllers;

[ApiController]
[Route("api/scores")]
public class ScoresController : ControllerBase
{
    private readonly CosmosDbService _cosmos;
    private readonly ILogger<ScoresController> _logger;
    private const int MaxRetries = 10;

    public ScoresController(CosmosDbService cosmos, ILogger<ScoresController> logger)
    {
        _cosmos = cosmos;
        _logger = logger;
    }

    [HttpPost]
    public async Task<IActionResult> SubmitScore([FromBody] SubmitScoreRequest? request)
    {
        if (request == null ||
            string.IsNullOrWhiteSpace(request.PlayerId) ||
            !request.Score.HasValue)
        {
            return BadRequest(new { error = "playerId and score are required" });
        }

        if (request.Score.Value < 0)
        {
            return BadRequest(new { error = "score must be a positive integer" });
        }

        await _cosmos.EnsureInitializedAsync();

        // Verify player exists
        Player player;
        string etag;
        try
        {
            var readResponse = await _cosmos.PlayersContainer.ReadItemAsync<Player>(
                request.PlayerId, new PartitionKey(request.PlayerId));
            player = readResponse.Resource;
            etag = readResponse.ETag;
        }
        catch (CosmosException ex) when (ex.StatusCode == System.Net.HttpStatusCode.NotFound)
        {
            return NotFound(new { error = $"Player '{request.PlayerId}' not found" });
        }

        // Create score document
        var scoreId = Guid.NewGuid().ToString();
        var score = new Score
        {
            Id = scoreId,
            ScoreId = scoreId,
            PlayerId = request.PlayerId,
            Value = request.Score.Value,
            GameMode = request.GameMode,
            Timestamp = DateTime.UtcNow.ToString("o")
        };

        await _cosmos.ScoresContainer.CreateItemAsync(
            score, new PartitionKey(request.PlayerId));

        // Update player stats with ETag-based optimistic concurrency and retry
        await UpdatePlayerStatsWithRetry(request.PlayerId, request.Score.Value);

        // Update leaderboard entries
        await UpdateLeaderboardEntries(request.PlayerId);

        return StatusCode(201, new ScoreResponse
        {
            ScoreId = scoreId,
            PlayerId = request.PlayerId,
            Score = request.Score.Value
        });
    }

    private async Task UpdatePlayerStatsWithRetry(string playerId, int newScore)
    {
        for (int attempt = 0; attempt < MaxRetries; attempt++)
        {
            try
            {
                var readResponse = await _cosmos.PlayersContainer.ReadItemAsync<Player>(
                    playerId, new PartitionKey(playerId));
                var player = readResponse.Resource;
                var etag = readResponse.ETag;

                player.TotalGames += 1;
                player.TotalScore += newScore;
                player.AverageScore = Math.Round((double)player.TotalScore / player.TotalGames, 2);
                if (newScore > player.BestScore)
                {
                    player.BestScore = newScore;
                }
                player.UpdatedAt = DateTime.UtcNow.ToString("o");

                await _cosmos.PlayersContainer.ReplaceItemAsync(
                    player,
                    playerId,
                    new PartitionKey(playerId),
                    new ItemRequestOptions { IfMatchEtag = etag });

                return;
            }
            catch (CosmosException ex) when (ex.StatusCode == System.Net.HttpStatusCode.PreconditionFailed)
            {
                _logger.LogDebug("ETag conflict on player {PlayerId}, retry {Attempt}", playerId, attempt + 1);
                if (attempt == MaxRetries - 1)
                {
                    _logger.LogWarning("Max retries for ETag conflict on player {PlayerId}", playerId);
                    throw;
                }
                await Task.Delay(Random.Shared.Next(10, 50 * (attempt + 1)));
            }
        }
    }

    private async Task UpdateLeaderboardEntries(string playerId)
    {
        try
        {
            // Read current player stats
            var playerResponse = await _cosmos.PlayersContainer.ReadItemAsync<Player>(
                playerId, new PartitionKey(playerId));
            var player = playerResponse.Resource;

            // Upsert global leaderboard entry
            var globalEntry = new LeaderboardEntry
            {
                Id = $"{playerId}_global",
                LeaderboardKey = "global_all-time",
                PlayerId = player.PlayerId,
                DisplayName = player.DisplayName,
                Region = player.Region,
                BestScore = player.BestScore,
                UpdatedAt = DateTime.UtcNow.ToString("o")
            };

            await _cosmos.LeaderboardsContainer.UpsertItemAsync(
                globalEntry, new PartitionKey("global_all-time"));

            // Upsert regional leaderboard entry
            var regionalKey = $"{player.Region}_all-time";
            var regionalEntry = new LeaderboardEntry
            {
                Id = $"{playerId}_regional",
                LeaderboardKey = regionalKey,
                PlayerId = player.PlayerId,
                DisplayName = player.DisplayName,
                Region = player.Region,
                BestScore = player.BestScore,
                UpdatedAt = DateTime.UtcNow.ToString("o")
            };

            await _cosmos.LeaderboardsContainer.UpsertItemAsync(
                regionalEntry, new PartitionKey(regionalKey));
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to update leaderboard entries for player {PlayerId}", playerId);
        }
    }
}
