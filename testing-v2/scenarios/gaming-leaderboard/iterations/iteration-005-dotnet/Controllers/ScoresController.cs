using Microsoft.AspNetCore.Mvc;
using Microsoft.Azure.Cosmos;
using GamingLeaderboard.Models;
using GamingLeaderboard.Services;
using System.Net;

namespace GamingLeaderboard.Controllers;

[ApiController]
[Route("api/scores")]
public class ScoresController : ControllerBase
{
    private readonly CosmosDbService _cosmosService;
    private readonly ILogger<ScoresController> _logger;

    public ScoresController(CosmosDbService cosmosService, ILogger<ScoresController> logger)
    {
        _cosmosService = cosmosService;
        _logger = logger;
    }

    [HttpPost]
    public async Task<IActionResult> SubmitScore([FromBody] SubmitScoreRequest? request)
    {
        if (request == null || string.IsNullOrWhiteSpace(request.PlayerId))
        {
            return BadRequest(new { error = "playerId is required." });
        }

        if (request.Score == null || !request.Score.HasValue)
        {
            return BadRequest(new { error = "score is required." });
        }

        if (request.Score.Value <= 0)
        {
            return BadRequest(new { error = "score must be a positive integer." });
        }

        try
        {
            // Verify player exists
            var player = await _cosmosService.GetPlayerAsync(request.PlayerId);
            if (player == null)
            {
                return BadRequest(new { error = $"Player '{request.PlayerId}' not found." });
            }

            // Create score record
            var scoreId = Guid.NewGuid().ToString();
            var score = new Score
            {
                Id = scoreId,
                ScoreId = scoreId,
                PlayerId = request.PlayerId,
                ScoreValue = request.Score.Value,
                GameMode = request.GameMode,
                Timestamp = DateTime.UtcNow.ToString("o")
            };

            await _cosmosService.CreateScoreAsync(score);

            // Atomically update player stats with ETag-based optimistic concurrency
            var updatedPlayer = await _cosmosService.UpdatePlayerStatsAfterScoreAsync(
                request.PlayerId, request.Score.Value);

            // Update leaderboard entries (denormalized)
            await _cosmosService.UpdateLeaderboardEntriesForPlayerAsync(updatedPlayer);

            return StatusCode(201, new ScoreResponse
            {
                ScoreId = score.ScoreId,
                PlayerId = score.PlayerId,
                Score = score.ScoreValue
            });
        }
        catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.NotFound)
        {
            return BadRequest(new { error = $"Player '{request.PlayerId}' not found." });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error submitting score for player {PlayerId}", request.PlayerId);
            return StatusCode(500, new { error = "Internal server error." });
        }
    }
}
