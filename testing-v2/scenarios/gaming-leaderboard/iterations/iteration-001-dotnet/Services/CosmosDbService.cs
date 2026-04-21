using Microsoft.Azure.Cosmos;
using GamingLeaderboard.Models;
using System.Net;

namespace GamingLeaderboard.Services;

public class CosmosDbService
{
    private readonly Container _playersContainer;
    private readonly Container _scoresContainer;
    private readonly Container _leaderboardsContainer;

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
            // 1. Delete all scores for the player
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

            // 2. Get the player to find their region for leaderboard cleanup
            var player = await GetPlayerAsync(playerId);
            if (player == null) return false;

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
        var player = await GetPlayerAsync(playerId);
        if (player == null)
            throw new InvalidOperationException("Player not found");

        // Create score document
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

        await _scoresContainer.CreateItemAsync(score, new PartitionKey(playerId));

        // Update player stats
        player.TotalGames++;
        player.TotalScore += scoreValue;
        if (scoreValue > player.BestScore)
            player.BestScore = scoreValue;
        player.AverageScore = (double)player.TotalScore / player.TotalGames;

        await _playersContainer.ReplaceItemAsync(player, player.Id, new PartitionKey(player.PlayerId));

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

    public async Task<List<Score>> GetPlayerScoresAsync(string playerId, int limit)
    {
        var query = new QueryDefinition("SELECT * FROM c WHERE c.playerId = @playerId ORDER BY c.timestamp DESC OFFSET 0 LIMIT @limit")
            .WithParameter("@playerId", playerId)
            .WithParameter("@limit", limit);

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
        var query = new QueryDefinition("SELECT TOP @top * FROM c WHERE c.leaderboardKey = 'global' ORDER BY c.score DESC, c.displayName ASC")
            .WithParameter("@top", top);

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
        var query = new QueryDefinition("SELECT TOP @top * FROM c WHERE c.leaderboardKey = @region ORDER BY c.score DESC, c.displayName ASC")
            .WithParameter("@top", top)
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
        // Get all global leaderboard entries sorted by score DESC, displayName ASC
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
}
