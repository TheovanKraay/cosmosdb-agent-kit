using Microsoft.AspNetCore.Mvc;
using Microsoft.Azure.Cosmos;
using GamingLeaderboard.Models;
using GamingLeaderboard.Services;

namespace GamingLeaderboard.Controllers;

[ApiController]
[Route("api/players")]
public class PlayersController : ControllerBase
{
    private readonly CosmosDbService _cosmos;
    private readonly ILogger<PlayersController> _logger;

    public PlayersController(CosmosDbService cosmos, ILogger<PlayersController> logger)
    {
        _cosmos = cosmos;
        _logger = logger;
    }

    [HttpPost]
    public async Task<IActionResult> CreatePlayer([FromBody] CreatePlayerRequest? request)
    {
        if (request == null ||
            string.IsNullOrWhiteSpace(request.PlayerId) ||
            string.IsNullOrWhiteSpace(request.DisplayName) ||
            string.IsNullOrWhiteSpace(request.Region))
        {
            return BadRequest(new { error = "playerId, displayName, and region are required" });
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
            await _cosmos.PlayersContainer.CreateItemAsync(
                player,
                new PartitionKey(player.PlayerId),
                new ItemRequestOptions { EnableContentResponseOnWrite = true });

            return StatusCode(201, PlayerResponse.FromPlayer(player));
        }
        catch (CosmosException ex) when (ex.StatusCode == System.Net.HttpStatusCode.Conflict)
        {
            return Conflict(new { error = $"Player '{request.PlayerId}' already exists" });
        }
    }

    [HttpGet("{playerId}")]
    public async Task<IActionResult> GetPlayer(string playerId)
    {
        try
        {
            var response = await _cosmos.PlayersContainer.ReadItemAsync<Player>(
                playerId, new PartitionKey(playerId));

            return Ok(PlayerResponse.FromPlayer(response.Resource));
        }
        catch (CosmosException ex) when (ex.StatusCode == System.Net.HttpStatusCode.NotFound)
        {
            return NotFound(new { error = $"Player '{playerId}' not found" });
        }
    }

    [HttpPatch("{playerId}")]
    public async Task<IActionResult> UpdatePlayer(string playerId, [FromBody] UpdatePlayerRequest? request)
    {
        if (request == null)
        {
            return BadRequest(new { error = "Request body is required" });
        }

        try
        {
            // Read the current player with ETag
            var readResponse = await _cosmos.PlayersContainer.ReadItemAsync<Player>(
                playerId, new PartitionKey(playerId));
            var player = readResponse.Resource;
            var etag = readResponse.ETag;

            bool regionChanged = false;
            string? oldRegion = player.Region;

            if (!string.IsNullOrWhiteSpace(request.DisplayName))
            {
                player.DisplayName = request.DisplayName;
            }
            if (!string.IsNullOrWhiteSpace(request.Region))
            {
                if (player.Region != request.Region)
                {
                    regionChanged = true;
                }
                player.Region = request.Region;
            }

            player.UpdatedAt = DateTime.UtcNow.ToString("o");

            // Use ETag for optimistic concurrency
            var replaceResponse = await _cosmos.PlayersContainer.ReplaceItemAsync(
                player,
                playerId,
                new PartitionKey(playerId),
                new ItemRequestOptions
                {
                    IfMatchEtag = etag,
                    EnableContentResponseOnWrite = true
                });

            // Update leaderboard entries if display name or region changed
            if (request.DisplayName != null || regionChanged)
            {
                await UpdateLeaderboardEntries(player, regionChanged, oldRegion);
            }

            return Ok(PlayerResponse.FromPlayer(replaceResponse.Resource));
        }
        catch (CosmosException ex) when (ex.StatusCode == System.Net.HttpStatusCode.NotFound)
        {
            return NotFound(new { error = $"Player '{playerId}' not found" });
        }
        catch (CosmosException ex) when (ex.StatusCode == System.Net.HttpStatusCode.PreconditionFailed)
        {
            // Retry on ETag conflict
            return await UpdatePlayer(playerId, request);
        }
    }

    [HttpDelete("{playerId}")]
    public async Task<IActionResult> DeletePlayer(string playerId)
    {
        try
        {
            // Verify player exists first
            await _cosmos.PlayersContainer.ReadItemAsync<Player>(
                playerId, new PartitionKey(playerId));

            // Delete scores for this player
            var scoreQuery = new QueryDefinition("SELECT * FROM c WHERE c.playerId = @playerId")
                .WithParameter("@playerId", playerId);

            using var scoreFeed = _cosmos.ScoresContainer.GetItemQueryIterator<Score>(
                scoreQuery, requestOptions: new QueryRequestOptions { PartitionKey = new PartitionKey(playerId) });

            while (scoreFeed.HasMoreResults)
            {
                var batch = await scoreFeed.ReadNextAsync();
                foreach (var score in batch)
                {
                    await _cosmos.ScoresContainer.DeleteItemAsync<Score>(
                        score.Id, new PartitionKey(playerId));
                }
            }

            // Delete leaderboard entries for this player
            await DeleteLeaderboardEntries(playerId);

            // Delete the player
            await _cosmos.PlayersContainer.DeleteItemAsync<Player>(
                playerId, new PartitionKey(playerId));

            return NoContent();
        }
        catch (CosmosException ex) when (ex.StatusCode == System.Net.HttpStatusCode.NotFound)
        {
            return NotFound(new { error = $"Player '{playerId}' not found" });
        }
    }

    [HttpGet("{playerId}/scores")]
    public async Task<IActionResult> GetPlayerScores(string playerId, [FromQuery] int limit = 10)
    {
        // Check player exists
        try
        {
            await _cosmos.PlayersContainer.ReadItemAsync<Player>(
                playerId, new PartitionKey(playerId));
        }
        catch (CosmosException ex) when (ex.StatusCode == System.Net.HttpStatusCode.NotFound)
        {
            return NotFound(new { error = $"Player '{playerId}' not found" });
        }

        if (limit < 1) limit = 1;
        if (limit > 100) limit = 100;

        var query = new QueryDefinition(
            "SELECT * FROM c WHERE c.playerId = @playerId ORDER BY c.timestamp DESC OFFSET 0 LIMIT @limit")
            .WithParameter("@playerId", playerId)
            .WithParameter("@limit", limit);

        var results = new List<ScoreHistoryEntry>();
        using var feed = _cosmos.ScoresContainer.GetItemQueryIterator<Score>(
            query, requestOptions: new QueryRequestOptions { PartitionKey = new PartitionKey(playerId) });

        while (feed.HasMoreResults)
        {
            var batch = await feed.ReadNextAsync();
            foreach (var score in batch)
            {
                results.Add(new ScoreHistoryEntry
                {
                    ScoreId = score.ScoreId,
                    PlayerId = score.PlayerId,
                    Score = score.Value,
                    GameMode = score.GameMode,
                    Timestamp = score.Timestamp
                });
            }
        }

        return Ok(results);
    }

    [HttpGet("{playerId}/rank")]
    public async Task<IActionResult> GetPlayerRank(string playerId)
    {
        // Check player exists and has scores
        Player player;
        try
        {
            var response = await _cosmos.PlayersContainer.ReadItemAsync<Player>(
                playerId, new PartitionKey(playerId));
            player = response.Resource;
        }
        catch (CosmosException ex) when (ex.StatusCode == System.Net.HttpStatusCode.NotFound)
        {
            return NotFound(new { error = $"Player '{playerId}' not found" });
        }

        if (player.TotalGames == 0)
        {
            return NotFound(new { error = $"Player '{playerId}' has no scores" });
        }

        // Get global leaderboard to determine rank
        var leaderboard = await GetGlobalLeaderboardEntries(1000);

        var playerEntry = leaderboard.FirstOrDefault(e => e.PlayerId == playerId);
        if (playerEntry == null)
        {
            return NotFound(new { error = $"Player '{playerId}' not found in leaderboard" });
        }

        int playerRank = leaderboard.IndexOf(playerEntry) + 1;

        // Get neighbors (±10 positions)
        int startIndex = Math.Max(0, playerRank - 1 - 10);
        int endIndex = Math.Min(leaderboard.Count - 1, playerRank - 1 + 10);

        var neighbors = new List<LeaderboardEntryResponse>();
        for (int i = startIndex; i <= endIndex; i++)
        {
            if (i == playerRank - 1) continue; // Skip self
            var entry = leaderboard[i];
            neighbors.Add(new LeaderboardEntryResponse
            {
                Rank = i + 1,
                PlayerId = entry.PlayerId,
                DisplayName = entry.DisplayName,
                Score = entry.BestScore
            });
        }

        return Ok(new PlayerRankResponse
        {
            PlayerId = playerId,
            Rank = playerRank,
            Score = playerEntry.BestScore,
            Neighbors = neighbors
        });
    }

    private async Task UpdateLeaderboardEntries(Player player, bool regionChanged, string? oldRegion)
    {
        try
        {
            // Query all leaderboard entries for this player
            var query = new QueryDefinition("SELECT * FROM c WHERE c.playerId = @playerId")
                .WithParameter("@playerId", player.PlayerId);

            using var feed = _cosmos.LeaderboardsContainer.GetItemQueryIterator<LeaderboardEntry>(
                query, requestOptions: new QueryRequestOptions { MaxItemCount = 100 });

            // Note: cross-partition query is enabled by default in .NET SDK v3 when no PartitionKey is specified
            var entriesToUpdate = new List<LeaderboardEntry>();
            var entriesToDelete = new List<LeaderboardEntry>();

            while (feed.HasMoreResults)
            {
                var batch = await feed.ReadNextAsync();
                foreach (var entry in batch)
                {
                    if (regionChanged && entry.LeaderboardKey.StartsWith(oldRegion + "_"))
                    {
                        entriesToDelete.Add(entry);
                    }
                    else
                    {
                        entry.DisplayName = player.DisplayName;
                        entry.Region = player.Region;
                        entry.UpdatedAt = DateTime.UtcNow.ToString("o");
                        entriesToUpdate.Add(entry);
                    }
                }
            }

            // Delete old regional entries
            foreach (var entry in entriesToDelete)
            {
                await _cosmos.LeaderboardsContainer.DeleteItemAsync<LeaderboardEntry>(
                    entry.Id, new PartitionKey(entry.LeaderboardKey));
            }

            // Update remaining entries
            foreach (var entry in entriesToUpdate)
            {
                await _cosmos.LeaderboardsContainer.ReplaceItemAsync(
                    entry, entry.Id, new PartitionKey(entry.LeaderboardKey));
            }

            // If region changed, create new regional entries
            if (regionChanged && player.BestScore > 0)
            {
                var newRegionalEntry = new LeaderboardEntry
                {
                    Id = $"{player.PlayerId}_regional",
                    LeaderboardKey = $"{player.Region}_all-time",
                    PlayerId = player.PlayerId,
                    DisplayName = player.DisplayName,
                    Region = player.Region,
                    BestScore = player.BestScore,
                    UpdatedAt = DateTime.UtcNow.ToString("o")
                };

                await _cosmos.LeaderboardsContainer.UpsertItemAsync(
                    newRegionalEntry, new PartitionKey(newRegionalEntry.LeaderboardKey));
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to update leaderboard entries for player {PlayerId}", player.PlayerId);
        }
    }

    private async Task DeleteLeaderboardEntries(string playerId)
    {
        try
        {
            var query = new QueryDefinition("SELECT * FROM c WHERE c.playerId = @playerId")
                .WithParameter("@playerId", playerId);

            using var feed = _cosmos.LeaderboardsContainer.GetItemQueryIterator<LeaderboardEntry>(
                query, requestOptions: new QueryRequestOptions { MaxItemCount = 100 });

            while (feed.HasMoreResults)
            {
                var batch = await feed.ReadNextAsync();
                foreach (var entry in batch)
                {
                    await _cosmos.LeaderboardsContainer.DeleteItemAsync<LeaderboardEntry>(
                        entry.Id, new PartitionKey(entry.LeaderboardKey));
                }
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to delete leaderboard entries for player {PlayerId}", playerId);
        }
    }

    private async Task<List<LeaderboardEntry>> GetGlobalLeaderboardEntries(int top)
    {
        var query = new QueryDefinition(
            "SELECT * FROM c ORDER BY c.bestScore DESC, c.displayName ASC OFFSET 0 LIMIT @top")
            .WithParameter("@top", top);

        var results = new List<LeaderboardEntry>();
        using var feed = _cosmos.LeaderboardsContainer.GetItemQueryIterator<LeaderboardEntry>(
            query, requestOptions: new QueryRequestOptions
            {
                PartitionKey = new PartitionKey("global_all-time")
            });

        while (feed.HasMoreResults)
        {
            var batch = await feed.ReadNextAsync();
            results.AddRange(batch);
        }

        return results;
    }
}
