using Microsoft.Azure.Cosmos;
using GamingLeaderboard.Models;
using System.Net;

namespace GamingLeaderboard.Services;

public class CosmosDbService
{
    private readonly CosmosClient _cosmosClient;
    private readonly string _databaseName = "gaming-leaderboard";
    private Database? _database;
    private Container? _playersContainer;
    private Container? _scoresContainer;
    private Container? _leaderboardContainer;

    public CosmosDbService(CosmosClient cosmosClient)
    {
        _cosmosClient = cosmosClient;
    }

    public async Task InitializeAsync()
    {
        var databaseResponse = await _cosmosClient.CreateDatabaseIfNotExistsAsync(
            _databaseName,
            ThroughputProperties.CreateAutoscaleThroughput(4000));
        _database = databaseResponse.Database;

        // Players container - partitioned by /playerId for efficient point reads
        var playersProperties = new ContainerProperties("players", "/playerId")
        {
            IndexingPolicy = new IndexingPolicy
            {
                Automatic = true,
                IndexingMode = IndexingMode.Consistent,
                IncludedPaths =
                {
                    new IncludedPath { Path = "/playerId/?" },
                    new IncludedPath { Path = "/region/?" },
                    new IncludedPath { Path = "/bestScore/?" }
                },
                ExcludedPaths =
                {
                    new ExcludedPath { Path = "/*" },
                    new ExcludedPath { Path = "/\"_etag\"/?" }
                }
            }
        };
        _playersContainer = await CreateContainerAsync(playersProperties);

        // Scores container - partitioned by /playerId for efficient player score history
        var scoresProperties = new ContainerProperties("scores", "/playerId")
        {
            IndexingPolicy = new IndexingPolicy
            {
                Automatic = true,
                IndexingMode = IndexingMode.Consistent,
                IncludedPaths =
                {
                    new IncludedPath { Path = "/playerId/?" },
                    new IncludedPath { Path = "/timestamp/?" },
                    new IncludedPath { Path = "/score/?" }
                },
                ExcludedPaths =
                {
                    new ExcludedPath { Path = "/*" },
                    new ExcludedPath { Path = "/\"_etag\"/?" }
                },
                CompositeIndexes =
                {
                    new System.Collections.ObjectModel.Collection<CompositePath>
                    {
                        new CompositePath { Path = "/playerId", Order = CompositePathSortOrder.Ascending },
                        new CompositePath { Path = "/timestamp", Order = CompositePathSortOrder.Descending }
                    }
                }
            }
        };
        _scoresContainer = await CreateContainerAsync(scoresProperties);

        // Leaderboard container - synthetic partition key /leaderboardKey
        var leaderboardProperties = new ContainerProperties("leaderboard", "/leaderboardKey")
        {
            IndexingPolicy = new IndexingPolicy
            {
                Automatic = true,
                IndexingMode = IndexingMode.Consistent,
                IncludedPaths =
                {
                    new IncludedPath { Path = "/leaderboardKey/?" },
                    new IncludedPath { Path = "/bestScore/?" },
                    new IncludedPath { Path = "/displayName/?" }
                },
                ExcludedPaths =
                {
                    new ExcludedPath { Path = "/*" },
                    new ExcludedPath { Path = "/\"_etag\"/?" }
                },
                CompositeIndexes =
                {
                    new System.Collections.ObjectModel.Collection<CompositePath>
                    {
                        new CompositePath { Path = "/bestScore", Order = CompositePathSortOrder.Descending },
                        new CompositePath { Path = "/displayName", Order = CompositePathSortOrder.Ascending }
                    }
                }
            }
        };
        _leaderboardContainer = await CreateContainerAsync(leaderboardProperties);
    }

    private async Task<Container> CreateContainerAsync(ContainerProperties properties)
    {
        var response = await _database!.CreateContainerIfNotExistsAsync(properties);
        return response.Container;
    }

    // ---- Player Operations ----

    public async Task<Player?> GetPlayerAsync(string playerId)
    {
        try
        {
            var response = await _playersContainer!.ReadItemAsync<Player>(
                playerId, new PartitionKey(playerId));
            return response.Resource;
        }
        catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.NotFound)
        {
            return null;
        }
    }

    public async Task<Player> CreatePlayerAsync(Player player)
    {
        player.Id = player.PlayerId;
        var requestOptions = new ItemRequestOptions
        {
            IfNoneMatchEtag = "*"
        };
        var response = await _playersContainer!.CreateItemAsync(
            player, new PartitionKey(player.PlayerId), requestOptions);
        return response.Resource;
    }

    public async Task<Player> UpdatePlayerAsync(Player player, string? etag = null)
    {
        var requestOptions = new ItemRequestOptions();
        if (!string.IsNullOrEmpty(etag))
        {
            requestOptions.IfMatchEtag = etag;
        }
        var response = await _playersContainer!.ReplaceItemAsync(
            player, player.Id, new PartitionKey(player.PlayerId), requestOptions);
        return response.Resource;
    }

    public async Task<Player> UpdatePlayerStatsAfterScoreAsync(string playerId, int score, int maxRetries = 30)
    {
        for (int attempt = 0; attempt < maxRetries; attempt++)
        {
            var readResponse = await _playersContainer!.ReadItemAsync<Player>(
                playerId, new PartitionKey(playerId));
            var player = readResponse.Resource;
            var etag = readResponse.ETag;

            player.TotalGames += 1;
            player.TotalScoreSum += score;
            player.AverageScore = Math.Round((double)player.TotalScoreSum / player.TotalGames, 2);
            if (score > player.BestScore)
            {
                player.BestScore = score;
            }

            try
            {
                var options = new ItemRequestOptions { IfMatchEtag = etag };
                var writeResponse = await _playersContainer.ReplaceItemAsync(
                    player, player.Id, new PartitionKey(player.PlayerId), options);
                return writeResponse.Resource;
            }
            catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.PreconditionFailed)
            {
                if (attempt == maxRetries - 1) throw;
                await Task.Delay(Random.Shared.Next(10, 50));
            }
        }

        throw new InvalidOperationException("Failed to update player stats after max retries");
    }

    public async Task<bool> DeletePlayerAsync(string playerId)
    {
        try
        {
            await _playersContainer!.DeleteItemAsync<Player>(
                playerId, new PartitionKey(playerId));
            return true;
        }
        catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.NotFound)
        {
            return false;
        }
    }

    // ---- Score Operations ----

    public async Task<Score> CreateScoreAsync(Score score)
    {
        var response = await _scoresContainer!.CreateItemAsync(
            score, new PartitionKey(score.PlayerId));
        return response.Resource;
    }

    public async Task<List<Score>> GetPlayerScoresAsync(string playerId, int limit)
    {
        var query = new QueryDefinition(
            "SELECT * FROM c WHERE c.playerId = @playerId AND c.type = 'score' ORDER BY c.timestamp DESC OFFSET 0 LIMIT @limit")
            .WithParameter("@playerId", playerId)
            .WithParameter("@limit", limit);

        var results = new List<Score>();
        using var iterator = _scoresContainer!.GetItemQueryIterator<Score>(
            query, requestOptions: new QueryRequestOptions { PartitionKey = new PartitionKey(playerId) });

        while (iterator.HasMoreResults)
        {
            var response = await iterator.ReadNextAsync();
            results.AddRange(response.Resource);
        }

        return results;
    }

    public async Task DeletePlayerScoresAsync(string playerId)
    {
        var query = new QueryDefinition(
            "SELECT c.id FROM c WHERE c.playerId = @playerId")
            .WithParameter("@playerId", playerId);

        using var iterator = _scoresContainer!.GetItemQueryIterator<dynamic>(
            query, requestOptions: new QueryRequestOptions { PartitionKey = new PartitionKey(playerId) });

        while (iterator.HasMoreResults)
        {
            var response = await iterator.ReadNextAsync();
            foreach (var item in response.Resource)
            {
                string id = item.id;
                try
                {
                    await _scoresContainer.DeleteItemAsync<dynamic>(id, new PartitionKey(playerId));
                }
                catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.NotFound)
                {
                    // Already deleted
                }
            }
        }
    }

    // ---- Leaderboard Operations ----

    public async Task UpsertLeaderboardEntryAsync(LeaderboardEntry entry)
    {
        await _leaderboardContainer!.UpsertItemAsync(
            entry, new PartitionKey(entry.LeaderboardKey));
    }

    public async Task<List<LeaderboardEntry>> GetLeaderboardAsync(string leaderboardKey, int top)
    {
        var query = new QueryDefinition(
            "SELECT * FROM c WHERE c.leaderboardKey = @key ORDER BY c.bestScore DESC, c.displayName ASC OFFSET 0 LIMIT @top")
            .WithParameter("@key", leaderboardKey)
            .WithParameter("@top", top);

        var results = new List<LeaderboardEntry>();
        using var iterator = _leaderboardContainer!.GetItemQueryIterator<LeaderboardEntry>(
            query, requestOptions: new QueryRequestOptions { PartitionKey = new PartitionKey(leaderboardKey) });

        while (iterator.HasMoreResults)
        {
            var response = await iterator.ReadNextAsync();
            results.AddRange(response.Resource);
        }

        return results;
    }

    public async Task DeleteLeaderboardEntriesForPlayerAsync(string playerId)
    {
        var query = new QueryDefinition(
            "SELECT * FROM c WHERE c.playerId = @playerId")
            .WithParameter("@playerId", playerId);

        using var iterator = _leaderboardContainer!.GetItemQueryIterator<LeaderboardEntry>(query);

        var entriesToDelete = new List<LeaderboardEntry>();
        while (iterator.HasMoreResults)
        {
            var response = await iterator.ReadNextAsync();
            entriesToDelete.AddRange(response.Resource);
        }

        foreach (var entry in entriesToDelete)
        {
            try
            {
                await _leaderboardContainer!.DeleteItemAsync<LeaderboardEntry>(
                    entry.Id, new PartitionKey(entry.LeaderboardKey));
            }
            catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.NotFound)
            {
                // Already deleted
            }
        }
    }

    public async Task UpdateLeaderboardEntriesForPlayerAsync(Player player)
    {
        var globalEntry = new LeaderboardEntry
        {
            Id = $"global_all-time_{player.PlayerId}",
            PlayerId = player.PlayerId,
            DisplayName = player.DisplayName,
            Region = player.Region,
            BestScore = player.BestScore,
            LeaderboardKey = "global_all-time"
        };
        await UpsertLeaderboardEntryAsync(globalEntry);

        var regionalKey = $"{player.Region}_all-time";
        var regionalEntry = new LeaderboardEntry
        {
            Id = $"{regionalKey}_{player.PlayerId}",
            PlayerId = player.PlayerId,
            DisplayName = player.DisplayName,
            Region = player.Region,
            BestScore = player.BestScore,
            LeaderboardKey = regionalKey
        };
        await UpsertLeaderboardEntryAsync(regionalEntry);
    }
}
