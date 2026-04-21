using Microsoft.AspNetCore.Mvc;
using GamingLeaderboard.Models;
using GamingLeaderboard.Services;

namespace GamingLeaderboard.Controllers;

[ApiController]
[Route("api/leaderboards")]
public class LeaderboardsController : ControllerBase
{
    private readonly CosmosDbService _cosmosService;
    private readonly ILogger<LeaderboardsController> _logger;

    public LeaderboardsController(CosmosDbService cosmosService, ILogger<LeaderboardsController> logger)
    {
        _cosmosService = cosmosService;
        _logger = logger;
    }

    [HttpGet("global")]
    public async Task<IActionResult> GetGlobalLeaderboard([FromQuery] int top = 100)
    {
        try
        {
            if (top < 1) top = 1;
            if (top > 100) top = 100;

            var entries = await _cosmosService.GetLeaderboardAsync("global_all-time", top);

            var response = entries.Select((e, i) => new LeaderboardItem
            {
                Rank = i + 1,
                PlayerId = e.PlayerId,
                DisplayName = e.DisplayName,
                Score = e.BestScore
            }).ToList();

            return Ok(response);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error getting global leaderboard");
            return StatusCode(500, new { error = "Internal server error." });
        }
    }

    [HttpGet("regional/{region}")]
    public async Task<IActionResult> GetRegionalLeaderboard(string region, [FromQuery] int top = 100)
    {
        try
        {
            if (top < 1) top = 1;
            if (top > 100) top = 100;

            var leaderboardKey = $"{region}_all-time";
            var entries = await _cosmosService.GetLeaderboardAsync(leaderboardKey, top);

            var response = entries.Select((e, i) => new LeaderboardItem
            {
                Rank = i + 1,
                PlayerId = e.PlayerId,
                DisplayName = e.DisplayName,
                Score = e.BestScore
            }).ToList();

            return Ok(response);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error getting regional leaderboard for {Region}", region);
            return StatusCode(500, new { error = "Internal server error." });
        }
    }
}
