using System.Net;
using Microsoft.Azure.Cosmos;
using GamingLeaderboard.Models;

namespace GamingLeaderboard.Services;

public class ScoreRepository
{
    private readonly CosmosDbService _cosmosDb;

    public ScoreRepository(CosmosDbService cosmosDb)
    {
        _cosmosDb = cosmosDb;
    }

    public async Task<Score> CreateScoreAsync(Score score)
    {
        var response = await _cosmosDb.ScoresContainer.CreateItemAsync(
            score, new PartitionKey(score.PlayerId));
        return response.Resource;
    }

    public async Task<List<Score>> GetPlayerScoresAsync(string playerId, int limit)
    {
        // Query scoped to partition (playerId), ordered by timestamp DESC
        var query = new QueryDefinition(
            "SELECT * FROM c WHERE c.playerId = @playerId AND c.type = 'score' ORDER BY c.timestamp DESC OFFSET 0 LIMIT @limit")
            .WithParameter("@playerId", playerId)
            .WithParameter("@limit", limit);

        var options = new QueryRequestOptions
        {
            PartitionKey = new PartitionKey(playerId)
        };

        var results = new List<Score>();
        using var iterator = _cosmosDb.ScoresContainer.GetItemQueryIterator<Score>(query, requestOptions: options);
        while (iterator.HasMoreResults)
        {
            var response = await iterator.ReadNextAsync();
            results.AddRange(response);
        }
        return results;
    }

    public async Task DeletePlayerScoresAsync(string playerId)
    {
        // Query all scores for this player to delete them
        var query = new QueryDefinition("SELECT c.id FROM c WHERE c.playerId = @playerId")
            .WithParameter("@playerId", playerId);

        var options = new QueryRequestOptions
        {
            PartitionKey = new PartitionKey(playerId)
        };

        using var iterator = _cosmosDb.ScoresContainer.GetItemQueryIterator<dynamic>(query, requestOptions: options);
        while (iterator.HasMoreResults)
        {
            var response = await iterator.ReadNextAsync();
            foreach (var item in response)
            {
                string id = item.id;
                try
                {
                    await _cosmosDb.ScoresContainer.DeleteItemAsync<dynamic>(
                        id, new PartitionKey(playerId));
                }
                catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.NotFound)
                {
                    // Already deleted, ignore
                }
            }
        }
    }
}
