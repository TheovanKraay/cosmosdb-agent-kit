using System.Net;
using Microsoft.Azure.Cosmos;
using GamingLeaderboard.Models;

namespace GamingLeaderboard.Services;

public class LeaderboardRepository
{
    private readonly CosmosDbService _cosmosDb;

    public LeaderboardRepository(CosmosDbService cosmosDb)
    {
        _cosmosDb = cosmosDb;
    }

    public async Task UpsertEntryAsync(LeaderboardEntry entry)
    {
        await _cosmosDb.LeaderboardContainer.UpsertItemAsync(
            entry, new PartitionKey(entry.LeaderboardKey));
    }

    public async Task<List<LeaderboardEntry>> GetGlobalLeaderboardAsync(int top)
    {
        // Query within the "global_all-time" partition for efficient single-partition query
        // Uses composite index (score DESC, displayName ASC) for sorting
        var query = new QueryDefinition(
            "SELECT * FROM c WHERE c.leaderboardKey = @key ORDER BY c.score DESC, c.displayName ASC OFFSET 0 LIMIT @top")
            .WithParameter("@key", "global_all-time")
            .WithParameter("@top", top);

        var options = new QueryRequestOptions
        {
            PartitionKey = new PartitionKey("global_all-time")
        };

        return await ExecuteQueryAsync(query, options);
    }

    public async Task<List<LeaderboardEntry>> GetRegionalLeaderboardAsync(string region, int top)
    {
        var partitionKey = $"regional_{region}";
        var query = new QueryDefinition(
            "SELECT * FROM c WHERE c.leaderboardKey = @key ORDER BY c.score DESC, c.displayName ASC OFFSET 0 LIMIT @top")
            .WithParameter("@key", partitionKey)
            .WithParameter("@top", top);

        var options = new QueryRequestOptions
        {
            PartitionKey = new PartitionKey(partitionKey)
        };

        return await ExecuteQueryAsync(query, options);
    }

    public async Task DeletePlayerEntriesAsync(string playerId)
    {
        // Find and delete all leaderboard entries for this player (cross-partition)
        var query = new QueryDefinition(
            "SELECT * FROM c WHERE c.playerId = @playerId")
            .WithParameter("@playerId", playerId);

        var options = new QueryRequestOptions
        {
            // Cross-partition query needed since entries are in different leaderboard partitions
        };

        using var iterator = _cosmosDb.LeaderboardContainer.GetItemQueryIterator<LeaderboardEntry>(
            query, requestOptions: options);
        while (iterator.HasMoreResults)
        {
            var response = await iterator.ReadNextAsync();
            foreach (var entry in response)
            {
                try
                {
                    await _cosmosDb.LeaderboardContainer.DeleteItemAsync<LeaderboardEntry>(
                        entry.Id, new PartitionKey(entry.LeaderboardKey));
                }
                catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.NotFound)
                {
                    // Already deleted
                }
            }
        }
    }

    public async Task UpdatePlayerDisplayNameAsync(string playerId, string newDisplayName)
    {
        var query = new QueryDefinition(
            "SELECT * FROM c WHERE c.playerId = @playerId")
            .WithParameter("@playerId", playerId);

        using var iterator = _cosmosDb.LeaderboardContainer.GetItemQueryIterator<LeaderboardEntry>(query);
        while (iterator.HasMoreResults)
        {
            var response = await iterator.ReadNextAsync();
            foreach (var entry in response)
            {
                entry.DisplayName = newDisplayName;
                await _cosmosDb.LeaderboardContainer.UpsertItemAsync(
                    entry, new PartitionKey(entry.LeaderboardKey));
            }
        }
    }

    public async Task UpdatePlayerRegionAsync(string playerId, string oldRegion, string newRegion, string displayName, int bestScore)
    {
        // Delete from old regional partition
        var oldKey = $"regional_{oldRegion}";
        var oldId = $"{playerId}_regional_{oldRegion}";
        try
        {
            await _cosmosDb.LeaderboardContainer.DeleteItemAsync<LeaderboardEntry>(
                oldId, new PartitionKey(oldKey));
        }
        catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.NotFound) { }

        // If the player has scores, add to new regional partition
        if (bestScore > 0)
        {
            var newEntry = new LeaderboardEntry
            {
                Id = $"{playerId}_regional_{newRegion}",
                PlayerId = playerId,
                DisplayName = displayName,
                Region = newRegion,
                Score = bestScore,
                LeaderboardKey = $"regional_{newRegion}"
            };
            await _cosmosDb.LeaderboardContainer.UpsertItemAsync(
                newEntry, new PartitionKey(newEntry.LeaderboardKey));
        }
    }

    private async Task<List<LeaderboardEntry>> ExecuteQueryAsync(
        QueryDefinition query, QueryRequestOptions options)
    {
        var results = new List<LeaderboardEntry>();
        using var iterator = _cosmosDb.LeaderboardContainer.GetItemQueryIterator<LeaderboardEntry>(
            query, requestOptions: options);
        while (iterator.HasMoreResults)
        {
            var response = await iterator.ReadNextAsync();
            results.AddRange(response);
        }
        return results;
    }
}
