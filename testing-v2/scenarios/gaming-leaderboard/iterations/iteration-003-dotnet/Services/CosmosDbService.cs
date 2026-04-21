using Microsoft.Azure.Cosmos;
using GamingLeaderboard.Models;
using System.Net;

namespace GamingLeaderboard.Services;

public class CosmosDbService
{
    private readonly CosmosClient _client;
    private readonly string _databaseName;
    private Database? _database;
    private Container? _playersContainer;
    private Container? _scoresContainer;
    private Container? _leaderboardContainer;

    public CosmosDbService(CosmosClient client, string databaseName)
    {
        _client = client;
        _databaseName = databaseName;
    }

    public async Task InitializeAsync()
    {
        var databaseResponse = await _client.CreateDatabaseIfNotExistsAsync(
            _databaseName,
            throughput: 400);
        _database = databaseResponse.Database;

        // Players container: partitioned by /playerId for efficient point reads
        var playersContainerProps = new ContainerProperties("players", "/playerId")
        {
            IndexingPolicy = new IndexingPolicy
            {
                IncludedPaths = { new IncludedPath { Path = "/*" } },
                ExcludedPaths =
                {
                    new ExcludedPath { Path = "/displayName/?" },
                    new ExcludedPath { Path = "/totalScore/?" },
                    new ExcludedPath { Path = "/schemaVersion/?" },
                    new ExcludedPath { Path = "/\"_etag\"/?" }
                }
            }
        };
        _playersContainer = (await _database.CreateContainerIfNotExistsAsync(playersContainerProps)).Container;

        // Scores container: partitioned by /playerId for efficient player score history queries
        var scoresContainerProps = new ContainerProperties("scores", "/playerId")
        {
            IndexingPolicy = new IndexingPolicy
            {
                IncludedPaths = { new IncludedPath { Path = "/*" } },
                ExcludedPaths =
                {
                    new ExcludedPath { Path = "/gameMode/?" },
                    new ExcludedPath { Path = "/schemaVersion/?" },
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
        _scoresContainer = (await _database.CreateContainerIfNotExistsAsync(scoresContainerProps)).Container;

        // Leaderboard container: partitioned by /leaderboardKey for efficient top-N queries within a partition
        var leaderboardContainerProps = new ContainerProperties("leaderboard", "/leaderboardKey")
        {
            IndexingPolicy = new IndexingPolicy
            {
                IncludedPaths = { new IncludedPath { Path = "/*" } },
                ExcludedPaths =
                {
                    new ExcludedPath { Path = "/schemaVersion/?" },
                    new ExcludedPath { Path = "/\"_etag\"/?" }
                },
                CompositeIndexes =
                {
                    new System.Collections.ObjectModel.Collection<CompositePath>
                    {
                        new CompositePath { Path = "/score", Order = CompositePathSortOrder.Descending },
                        new CompositePath { Path = "/displayName", Order = CompositePathSortOrder.Ascending }
                    }
                }
            }
        };
        _leaderboardContainer = (await _database.CreateContainerIfNotExistsAsync(leaderboardContainerProps)).Container;
    }

    // ---- Player Operations ----

    public async Task<Player> CreatePlayerAsync(Player player)
    {
        var response = await _playersContainer!.CreateItemAsync(
            player,
            new PartitionKey(player.PlayerId));
        return response.Resource;
    }

    public async Task<Player?> GetPlayerAsync(string playerId)
    {
        try
        {
            var response = await _playersContainer!.ReadItemAsync<Player>(
                playerId,
                new PartitionKey(playerId));
            return response.Resource;
        }
        catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.NotFound)
        {
            return null;
        }
    }

    public async Task<Player> UpdatePlayerAsync(Player player)
    {
        var response = await _playersContainer!.ReplaceItemAsync(
            player,
            player.Id,
            new PartitionKey(player.PlayerId));
        return response.Resource;
    }

    /// <summary>
    /// Atomically update player stats after a new score submission.
    /// Uses read-modify-write with ETag-based optimistic concurrency to prevent lost updates.
    /// </summary>
    public async Task<Player> UpdatePlayerStatsWithRetryAsync(string playerId, int newScore, int maxRetries = 10)
    {
        for (int attempt = 0; attempt <= maxRetries; attempt++)
        {
            try
            {
                var readResponse = await _playersContainer!.ReadItemAsync<Player>(
                    playerId,
                    new PartitionKey(playerId));

                var player = readResponse.Resource;
                string etag = readResponse.ETag;

                player.TotalGames += 1;
                player.TotalScore += newScore;
                if (newScore > player.BestScore)
                {
                    player.BestScore = newScore;
                }
                player.AverageScore = (double)player.TotalScore / player.TotalGames;

                var options = new ItemRequestOptions
                {
                    IfMatchEtag = etag
                };

                var response = await _playersContainer.ReplaceItemAsync(
                    player,
                    player.Id,
                    new PartitionKey(player.PlayerId),
                    options);

                return response.Resource;
            }
            catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.PreconditionFailed && attempt < maxRetries)
            {
                // ETag mismatch — another request updated the document. Retry.
                await Task.Delay(Random.Shared.Next(10, 50));
            }
        }

        throw new InvalidOperationException($"Failed to update player stats for {playerId} after {maxRetries} retries");
    }

    public async Task<bool> DeletePlayerAsync(string playerId)
    {
        try
        {
            await _playersContainer!.DeleteItemAsync<Player>(
                playerId,
                new PartitionKey(playerId));
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
            score,
            new PartitionKey(score.PlayerId));
        return response.Resource;
    }

    public async Task<List<Score>> GetPlayerScoresAsync(string playerId, int limit)
    {
        var query = new QueryDefinition(
            "SELECT * FROM c WHERE c.playerId = @playerId ORDER BY c.timestamp DESC OFFSET 0 LIMIT @limit")
            .WithParameter("@playerId", playerId)
            .WithParameter("@limit", limit);

        var results = new List<Score>();
        using var iterator = _scoresContainer!.GetItemQueryIterator<Score>(
            query,
            requestOptions: new QueryRequestOptions
            {
                PartitionKey = new PartitionKey(playerId)
            });

        while (iterator.HasMoreResults)
        {
            var response = await iterator.ReadNextAsync();
            results.AddRange(response);
        }

        return results;
    }

    public async Task DeletePlayerScoresAsync(string playerId)
    {
        var query = new QueryDefinition("SELECT c.id FROM c WHERE c.playerId = @playerId")
            .WithParameter("@playerId", playerId);

        var iterator = _scoresContainer!.GetItemQueryIterator<dynamic>(
            query,
            requestOptions: new QueryRequestOptions
            {
                PartitionKey = new PartitionKey(playerId)
            });

        while (iterator.HasMoreResults)
        {
            var response = await iterator.ReadNextAsync();
            foreach (var item in response)
            {
                string id = item.id;
                await _scoresContainer.DeleteItemAsync<dynamic>(
                    id,
                    new PartitionKey(playerId));
            }
        }
    }

    // ---- Leaderboard Operations ----

    public async Task UpsertLeaderboardEntryAsync(LeaderboardEntry entry)
    {
        await _leaderboardContainer!.UpsertItemAsync(
            entry,
            new PartitionKey(entry.LeaderboardKey));
    }

    public async Task<List<LeaderboardEntry>> GetLeaderboardAsync(string leaderboardKey, int top)
    {
        var query = new QueryDefinition(
            "SELECT * FROM c WHERE c.leaderboardKey = @key ORDER BY c.score DESC, c.displayName ASC OFFSET 0 LIMIT @top")
            .WithParameter("@key", leaderboardKey)
            .WithParameter("@top", top);

        var results = new List<LeaderboardEntry>();
        using var iterator = _leaderboardContainer!.GetItemQueryIterator<LeaderboardEntry>(
            query,
            requestOptions: new QueryRequestOptions
            {
                PartitionKey = new PartitionKey(leaderboardKey)
            });

        while (iterator.HasMoreResults)
        {
            var response = await iterator.ReadNextAsync();
            results.AddRange(response);
        }

        return results;
    }

    public async Task<List<LeaderboardEntry>> GetFullLeaderboardAsync(string leaderboardKey)
    {
        var query = new QueryDefinition(
            "SELECT * FROM c WHERE c.leaderboardKey = @key ORDER BY c.score DESC, c.displayName ASC")
            .WithParameter("@key", leaderboardKey);

        var results = new List<LeaderboardEntry>();
        using var iterator = _leaderboardContainer!.GetItemQueryIterator<LeaderboardEntry>(
            query,
            requestOptions: new QueryRequestOptions
            {
                PartitionKey = new PartitionKey(leaderboardKey)
            });

        while (iterator.HasMoreResults)
        {
            var response = await iterator.ReadNextAsync();
            results.AddRange(response);
        }

        return results;
    }

    public async Task DeleteLeaderboardEntriesForPlayerAsync(string playerId)
    {
        // Delete from global leaderboard
        await DeleteLeaderboardEntryAsync("global", playerId);
    }

    public async Task DeleteLeaderboardEntryAsync(string leaderboardKey, string playerId)
    {
        try
        {
            string id = $"{leaderboardKey}_{playerId}";
            await _leaderboardContainer!.DeleteItemAsync<LeaderboardEntry>(
                id,
                new PartitionKey(leaderboardKey));
        }
        catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.NotFound)
        {
            // Entry may not exist, that's okay
        }
    }

    public async Task DeleteRegionalLeaderboardEntryAsync(string region, string playerId)
    {
        string leaderboardKey = $"region_{region}";
        await DeleteLeaderboardEntryAsync(leaderboardKey, playerId);
    }
}
