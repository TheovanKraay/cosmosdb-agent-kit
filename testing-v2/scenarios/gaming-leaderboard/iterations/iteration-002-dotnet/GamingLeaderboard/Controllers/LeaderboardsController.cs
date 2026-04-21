using Microsoft.AspNetCore.Mvc;
using Microsoft.Azure.Cosmos;
using GamingLeaderboard.Models;
using GamingLeaderboard.Services;

namespace GamingLeaderboard.Controllers;

[ApiController]
[Route("api/leaderboards")]
public class LeaderboardsController : ControllerBase
{
    private readonly CosmosDbService _cosmos;
    private readonly ILogger<LeaderboardsController> _logger;

    public LeaderboardsController(CosmosDbService cosmos, ILogger<LeaderboardsController> logger)
    {
        _cosmos = cosmos;
        _logger = logger;
    }

    [HttpGet("global")]
    public async Task<IActionResult> GetGlobalLeaderboard([FromQuery] int top = 100)
    {
        if (top < 0) top = 0;
        if (top > 100) top = 100;

        if (top == 0)
        {
            return Ok(new List<LeaderboardEntryResponse>());
        }

        var query = new QueryDefinition(
            "SELECT * FROM c ORDER BY c.bestScore DESC, c.displayName ASC OFFSET 0 LIMIT @top")
            .WithParameter("@top", top);

        var results = new List<LeaderboardEntryResponse>();
        int rank = 1;

        using var feed = _cosmos.LeaderboardsContainer.GetItemQueryIterator<LeaderboardEntry>(
            query, requestOptions: new QueryRequestOptions
            {
                PartitionKey = new PartitionKey("global_all-time")
            });

        while (feed.HasMoreResults)
        {
            var batch = await feed.ReadNextAsync();
            foreach (var entry in batch)
            {
                results.Add(new LeaderboardEntryResponse
                {
                    Rank = rank++,
                    PlayerId = entry.PlayerId,
                    DisplayName = entry.DisplayName,
                    Score = entry.BestScore
                });
            }
        }

        return Ok(results);
    }

    [HttpGet("regional/{region}")]
    public async Task<IActionResult> GetRegionalLeaderboard(string region, [FromQuery] int top = 100)
    {
        if (top < 0) top = 0;
        if (top > 100) top = 100;

        if (top == 0)
        {
            return Ok(new List<LeaderboardEntryResponse>());
        }

        var partitionKey = $"{region}_all-time";

        var query = new QueryDefinition(
            "SELECT * FROM c ORDER BY c.bestScore DESC, c.displayName ASC OFFSET 0 LIMIT @top")
            .WithParameter("@top", top);

        var results = new List<LeaderboardEntryResponse>();
        int rank = 1;

        using var feed = _cosmos.LeaderboardsContainer.GetItemQueryIterator<LeaderboardEntry>(
            query, requestOptions: new QueryRequestOptions
            {
                PartitionKey = new PartitionKey(partitionKey)
            });

        while (feed.HasMoreResults)
        {
            var batch = await feed.ReadNextAsync();
            foreach (var entry in batch)
            {
                results.Add(new LeaderboardEntryResponse
                {
                    Rank = rank++,
                    PlayerId = entry.PlayerId,
                    DisplayName = entry.DisplayName,
                    Score = entry.BestScore
                });
            }
        }

        return Ok(results);
    }
}
