using System.Net;
using Microsoft.Azure.Cosmos;
using GamingLeaderboard.Models;

namespace GamingLeaderboard.Services;

public class PlayerRepository
{
    private readonly CosmosDbService _cosmosDb;
    private readonly ILogger<PlayerRepository> _logger;

    public PlayerRepository(CosmosDbService cosmosDb, ILogger<PlayerRepository> logger)
    {
        _cosmosDb = cosmosDb;
        _logger = logger;
    }

    public async Task<Player> CreatePlayerAsync(Player player)
    {
        // Use IfNoneMatchETag("*") for conditional create to prevent duplicates
        var options = new ItemRequestOptions { IfNoneMatchEtag = "*" };
        var response = await _cosmosDb.PlayersContainer.CreateItemAsync(
            player, new PartitionKey(player.PlayerId), options);
        return response.Resource;
    }

    public async Task<Player?> GetPlayerAsync(string playerId)
    {
        try
        {
            // Point read by id + partition key (most efficient)
            var response = await _cosmosDb.PlayersContainer.ReadItemAsync<Player>(
                playerId, new PartitionKey(playerId));
            return response.Resource;
        }
        catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.NotFound)
        {
            return null;
        }
    }

    public async Task<Player> UpdatePlayerAsync(string playerId, string? displayName, string? region)
    {
        const int maxRetries = 5;

        for (int attempt = 0; attempt < maxRetries; attempt++)
        {
            try
            {
                // Read current state with ETag for optimistic concurrency
                var response = await _cosmosDb.PlayersContainer.ReadItemAsync<Player>(
                    playerId, new PartitionKey(playerId));
                var player = response.Resource;
                var etag = response.ETag;

                if (displayName != null) player.DisplayName = displayName;
                if (region != null) player.Region = region;

                var options = new ItemRequestOptions { IfMatchEtag = etag };
                var updated = await _cosmosDb.PlayersContainer.ReplaceItemAsync(
                    player, playerId, new PartitionKey(playerId), options);
                return updated.Resource;
            }
            catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.PreconditionFailed)
            {
                if (attempt == maxRetries - 1)
                    throw new InvalidOperationException(
                        $"Failed to update player {playerId} after {maxRetries} attempts due to concurrent modifications.", ex);
            }
        }

        throw new InvalidOperationException("Unreachable");
    }

    public async Task<bool> DeletePlayerAsync(string playerId)
    {
        try
        {
            await _cosmosDb.PlayersContainer.DeleteItemAsync<Player>(
                playerId, new PartitionKey(playerId));
            return true;
        }
        catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.NotFound)
        {
            return false;
        }
    }

    public async Task UpdatePlayerStatsAsync(string playerId, int newScore)
    {
        const int maxRetries = 10;

        for (int attempt = 0; attempt < maxRetries; attempt++)
        {
            try
            {
                var response = await _cosmosDb.PlayersContainer.ReadItemAsync<Player>(
                    playerId, new PartitionKey(playerId));
                var player = response.Resource;
                var etag = response.ETag;

                player.TotalGames++;
                player.TotalScore += newScore;
                player.BestScore = Math.Max(player.BestScore, newScore);
                player.AverageScore = (double)player.TotalScore / player.TotalGames;

                var options = new ItemRequestOptions { IfMatchEtag = etag };
                await _cosmosDb.PlayersContainer.ReplaceItemAsync(
                    player, playerId, new PartitionKey(playerId), options);
                return;
            }
            catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.PreconditionFailed)
            {
                if (attempt == maxRetries - 1)
                {
                    _logger.LogError(ex, "Failed to update stats for player after {MaxRetries} attempts", maxRetries);
                    throw;
                }
                // Brief delay before retry to reduce contention
                await Task.Delay(Random.Shared.Next(10, 50));
            }
        }
    }
}
