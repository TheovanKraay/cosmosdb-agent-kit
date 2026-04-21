using Microsoft.Azure.Cosmos;
using GamingLeaderboard.Models;
using System.Net;

namespace GamingLeaderboard.Services;

public class CosmosDbService
{
    private readonly Container _playersContainer;
    private readonly Container _scoresContainer;
    private readonly Container _leaderboardsContainer;
    private const int MaxRetries = 10;

    public CosmosDbService(CosmosClient cosmosClient, string databaseName)
    {
        var database = cosmosClient.GetDatabase(databaseName);
        _playersContainer = database.GetContainer("players");
        _scoresContainer = database.GetContainer("scores");
        _leaderboardsContainer = database.GetContainer("leaderboards");
    }

    // Player operations

    public async Task<Player> CreatePlayerAsync(Player player)
    {
        player.Id = player.PlayerId;
        var response = await _playersContainer.CreateItemAsync(player, new PartitionKey(player.PlayerId));
        return response.Resource;
    }

    public async Task<Player?> GetPlayerAsync(string playerId)
    {
        try
        {
            var response = await _playersContainer.ReadItemAsync<Player>(playerId, new PartitionKey(playerId));
            return response.Resource;
        }
        catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.NotFound)
        {
            return null;
        }
    }

    public async Task<Player> UpdatePlayerAsync(Player player)
    {
        var response = await _playersContainer.ReplaceItemAsync(player, player.Id, new PartitionKey(player.PlayerId));
        return response.Resource;
    }

    public async Task<bool> DeletePlayerAsync(string playerId)
    {
        try
        {
            // 1. Get the player to find their region for leaderboard cleanup
            var player = await GetPlayerAsync(playerId);
            if (player == null) return false;

            // 2. Delete all scores for the player
            var scoreQuery = new QueryDefinition("SELECT c.id FROM c WHERE c.playerId = @playerId")
                .WithParameter("@playerId", playerId);
            var scoreIterator = _scoresContainer.GetItemQueryIterator<Score>(scoreQuery,
                requestOptions: new QueryRequestOptions { PartitionKey = new PartitionKey(playerId) });
            while (scoreIterator.HasMoreResults)
            {
                var scores = await scoreIterator.ReadNextAsync();
                foreach (var score in scores)
                {
                    await _scoresContainer.DeleteItemAsync<Score>(score.Id, new PartitionKey(playerId));
                }
            }

            // 3. Delete leaderboard entries (global + regional)
            try
            {
                string globalEntryId = $"{playerId}_global";
                await _leaderboardsContainer.DeleteItemAsync<LeaderboardEntry>(globalEntryId, new PartitionKey("global"));
            }
            catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.NotFound) { }

            try
            {
                string regionalEntryId = $"{playerId}_{player.Region}";
                await _leaderboardsContainer.DeleteItemAsync<LeaderboardEntry>(regionalEntryId, new PartitionKey(player.Region));
            }
            catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.NotFound) { }

            // 4. Delete the player document
            await _playersContainer.DeleteItemAsync<Player>(playerId, new PartitionKey(playerId));
            return true;
        }
        catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.NotFound)
        {
            return false;
        }
    }

    // Score operations

    public async Task<Score> SubmitScoreAsync(string playerId, int scoreValue, string? gameMode)
    {
        // Create score document first
        var scoreId = Guid.NewGuid().ToString();
        var score = new Score
        {
            Id = scoreId,
            ScoreId = scoreId,
            PlayerId = playerId,
            ScoreValue = scoreValue,
            GameMode = gameMode,
            Timestamp = DateTime.UtcNow.ToString("o"),
            Type = "score",
            SchemaVersion = 1
        };

        // Update player stats with optimistic concurrency (ETag-based retry)
        for (int retry = 0; retry < MaxRetries; retry++)
        {
            ItemResponse<Player> playerResponse;
            try
            {
                playerResponse = await _playersContainer.ReadItemAsync<Player>(playerId, new PartitionKey(playerId));
            }
            catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.NotFound)
            {
                throw new InvalidOperationException("Player not found");
            }

            var player = playerResponse.Resource;
            string etag = playerResponse.ETag;

            player.TotalGames++;
            player.TotalScore += scoreValue;
            if (scoreValue > player.BestScore)
                player.BestScore = scoreValue;
            player.AverageScore = (double)player.TotalScore / player.TotalGames;

            try
            {
                await _playersContainer.ReplaceItemAsync(player, player.Id,
                    new PartitionKey(player.PlayerId),
                    new ItemRequestOptions { IfMatchEtag = etag });

                // Player updated successfully; now create the score and update leaderboard
                await _scoresContainer.CreateItemAsync(score, new PartitionKey(playerId));

                // Upsert global leaderboard entry
                var globalEntry = new LeaderboardEntry
                {
                    Id = $"{playerId}_global",
                    LeaderboardKey = "global",
                    PlayerId = playerId,
                    DisplayName = player.DisplayName,
                    Region = player.Region,
                    Score = player.BestScore,
                    Type = "leaderboardEntry",
                    SchemaVersion = 1
                };
                await _leaderboardsContainer.UpsertItemAsync(globalEntry, new PartitionKey("global"));

                // Upsert regional leaderboard entry
                var regionalEntry = new LeaderboardEntry
                {
                    Id = $"{playerId}_{player.Region}",
                    LeaderboardKey = player.Region,
                    PlayerId = playerId,
                    DisplayName = player.DisplayName,
                    Region = player.Region,
                    Score = player.BestScore,
                    Type = "leaderboardEntry",
                    SchemaVersion = 1
                };
                await _leaderboardsContainer.UpsertItemAsync(regionalEntry, new PartitionKey(player.Region));

                return score;
            }
            catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.PreconditionFailed)
            {
                // ETag conflict — another write happened; retry
                await Task.Delay(10 * (retry + 1));
            }
        }

        throw new InvalidOperationException("Failed to update player stats after maximum retries");
    }

    public async Task<List<Score>> GetPlayerScoresAsync(string playerId, int limit)
    {
        var queryText = $"SELECT TOP {limit} * FROM c WHERE c.playerId = @playerId ORDER BY c.timestamp DESC";
        var query = new QueryDefinition(queryText)
            .WithParameter("@playerId", playerId);

        var iterator = _scoresContainer.GetItemQueryIterator<Score>(query,
            requestOptions: new QueryRequestOptions { PartitionKey = new PartitionKey(playerId) });

        var scores = new List<Score>();
        while (iterator.HasMoreResults)
        {
            var response = await iterator.ReadNextAsync();
            scores.AddRange(response);
        }

        return scores;
    }

    // Leaderboard operations

    public async Task<List<LeaderboardEntry>> GetGlobalLeaderboardAsync(int top)
    {
        var queryText = $"SELECT TOP {top} * FROM c WHERE c.leaderboardKey = 'global' ORDER BY c.score DESC, c.displayName ASC";
        var query = new QueryDefinition(queryText);

        var iterator = _leaderboardsContainer.GetItemQueryIterator<LeaderboardEntry>(query);

        var entries = new List<LeaderboardEntry>();
        while (iterator.HasMoreResults)
        {
            var response = await iterator.ReadNextAsync();
            entries.AddRange(response);
        }

        return entries;
    }

    public async Task<List<LeaderboardEntry>> GetRegionalLeaderboardAsync(string region, int top)
    {
        var queryText = $"SELECT TOP {top} * FROM c WHERE c.leaderboardKey = @region ORDER BY c.score DESC, c.displayName ASC";
        var query = new QueryDefinition(queryText)
            .WithParameter("@region", region);

        var iterator = _leaderboardsContainer.GetItemQueryIterator<LeaderboardEntry>(query);

        var entries = new List<LeaderboardEntry>();
        while (iterator.HasMoreResults)
        {
            var response = await iterator.ReadNextAsync();
            entries.AddRange(response);
        }

        return entries;
    }

    public async Task<(LeaderboardEntry? playerEntry, List<LeaderboardEntry> allEntries)> GetPlayerRankDataAsync(string playerId)
    {
        var query = new QueryDefinition("SELECT * FROM c WHERE c.leaderboardKey = 'global' ORDER BY c.score DESC, c.displayName ASC");

        var iterator = _leaderboardsContainer.GetItemQueryIterator<LeaderboardEntry>(query);

        var allEntries = new List<LeaderboardEntry>();
        LeaderboardEntry? playerEntry = null;

        while (iterator.HasMoreResults)
        {
            var response = await iterator.ReadNextAsync();
            foreach (var entry in response)
            {
                allEntries.Add(entry);
                if (entry.PlayerId == playerId)
                    playerEntry = entry;
            }
        }

        return (playerEntry, allEntries);
    }

    public async Task UpdateLeaderboardEntriesAsync(Player player, string oldRegion, bool regionChanged)
    {
        // Update global leaderboard entry
        var globalEntry = new LeaderboardEntry
        {
            Id = $"{player.PlayerId}_global",
            LeaderboardKey = "global",
            PlayerId = player.PlayerId,
            DisplayName = player.DisplayName,
            Region = player.Region,
            Score = player.BestScore,
            Type = "leaderboardEntry",
            SchemaVersion = 1
        };
        await _leaderboardsContainer.UpsertItemAsync(globalEntry, new PartitionKey("global"));

        if (regionChanged)
        {
            // Delete old regional entry
            try
            {
                string oldRegionalId = $"{player.PlayerId}_{oldRegion}";
                await _leaderboardsContainer.DeleteItemAsync<LeaderboardEntry>(oldRegionalId, new PartitionKey(oldRegion));
            }
            catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.NotFound) { }
        }

        // Upsert new regional entry
        var regionalEntry = new LeaderboardEntry
        {
            Id = $"{player.PlayerId}_{player.Region}",
            LeaderboardKey = player.Region,
            PlayerId = player.PlayerId,
            DisplayName = player.DisplayName,
            Region = player.Region,
            Score = player.BestScore,
            Type = "leaderboardEntry",
            SchemaVersion = 1
        };
        await _leaderboardsContainer.UpsertItemAsync(regionalEntry, new PartitionKey(player.Region));
    }
}
