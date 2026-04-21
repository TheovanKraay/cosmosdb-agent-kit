using Microsoft.AspNetCore.Mvc;
using Microsoft.Azure.Cosmos;
using GamingLeaderboard.Models;
using GamingLeaderboard.Services;
using System.Net;
using System.Text.RegularExpressions;

namespace GamingLeaderboard.Controllers;

[ApiController]
[Route("api/players")]
public partial class PlayersController : ControllerBase
{
    private readonly CosmosDbService _cosmosService;
    private readonly ILogger<PlayersController> _logger;

    public PlayersController(CosmosDbService cosmosService, ILogger<PlayersController> logger)
    {
        _cosmosService = cosmosService;
        _logger = logger;
    }

    private static string SanitizeForLog(string? input) =>
        input == null ? "" : ControlCharsRegex().Replace(input, "_");

    [HttpPost]
    public async Task<IActionResult> CreatePlayer([FromBody] CreatePlayerRequest? request)
    {
        if (request == null || string.IsNullOrWhiteSpace(request.PlayerId)
            || string.IsNullOrWhiteSpace(request.DisplayName)
            || string.IsNullOrWhiteSpace(request.Region))
        {
            return BadRequest(new { error = "playerId, displayName, and region are required." });
        }

        var player = new Player
        {
            Id = request.PlayerId,
            PlayerId = request.PlayerId,
            DisplayName = request.DisplayName,
            Region = request.Region,
            TotalGames = 0,
            BestScore = 0,
            AverageScore = 0,
            TotalScoreSum = 0
        };

        try
        {
            var created = await _cosmosService.CreatePlayerAsync(player);
            var response = MapToPlayerResponse(created);
            return StatusCode(201, response);
        }
        catch (CosmosException ex) when (ex.StatusCode == HttpStatusCode.Conflict)
        {
            return Conflict(new { error = $"Player with id '{request.PlayerId}' already exists." });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error creating player {PlayerId}", SanitizeForLog(request.PlayerId));
            return StatusCode(500, new { error = "Internal server error." });
        }
    }

    [HttpGet("{playerId}")]
    public async Task<IActionResult> GetPlayer(string playerId)
    {
        try
        {
            var player = await _cosmosService.GetPlayerAsync(playerId);
            if (player == null)
            {
                return NotFound(new { error = $"Player '{playerId}' not found." });
            }
            return Ok(MapToPlayerResponse(player));
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error getting player {PlayerId}", SanitizeForLog(playerId));
            return StatusCode(500, new { error = "Internal server error." });
        }
    }

    [HttpPatch("{playerId}")]
    public async Task<IActionResult> UpdatePlayer(string playerId, [FromBody] UpdatePlayerRequest? request)
    {
        if (request == null)
        {
            return BadRequest(new { error = "Request body is required." });
        }

        try
        {
            var player = await _cosmosService.GetPlayerAsync(playerId);
            if (player == null)
            {
                return NotFound(new { error = $"Player '{playerId}' not found." });
            }

            bool changed = false;
            string oldRegion = player.Region;

            if (!string.IsNullOrWhiteSpace(request.DisplayName))
            {
                player.DisplayName = request.DisplayName;
                changed = true;
            }

            if (!string.IsNullOrWhiteSpace(request.Region))
            {
                player.Region = request.Region;
                changed = true;
            }

            if (changed)
            {
                var updated = await _cosmosService.UpdatePlayerAsync(player);

                // Update leaderboard entries with new display name/region
                if (updated.BestScore > 0)
                {
                    if (oldRegion != updated.Region)
                    {
                        // Delete all old leaderboard entries and recreate
                        await _cosmosService.DeleteLeaderboardEntriesForPlayerAsync(playerId);
                    }
                    await _cosmosService.UpdateLeaderboardEntriesForPlayerAsync(updated);
                }

                return Ok(MapToPlayerResponse(updated));
            }

            return Ok(MapToPlayerResponse(player));
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error updating player {PlayerId}", SanitizeForLog(playerId));
            return StatusCode(500, new { error = "Internal server error." });
        }
    }

    [HttpDelete("{playerId}")]
    public async Task<IActionResult> DeletePlayer(string playerId)
    {
        try
        {
            var player = await _cosmosService.GetPlayerAsync(playerId);
            if (player == null)
            {
                return NotFound(new { error = $"Player '{playerId}' not found." });
            }

            var deleted = await _cosmosService.DeletePlayerAsync(playerId);
            if (!deleted)
            {
                return NotFound(new { error = $"Player '{playerId}' not found." });
            }

            // Delete all scores and leaderboard entries
            await _cosmosService.DeletePlayerScoresAsync(playerId);
            await _cosmosService.DeleteLeaderboardEntriesForPlayerAsync(playerId);

            return NoContent();
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error deleting player {PlayerId}", SanitizeForLog(playerId));
            return StatusCode(500, new { error = "Internal server error." });
        }
    }

    [HttpGet("{playerId}/scores")]
    public async Task<IActionResult> GetPlayerScores(string playerId, [FromQuery] int limit = 10)
    {
        try
        {
            var player = await _cosmosService.GetPlayerAsync(playerId);
            if (player == null)
            {
                return NotFound(new { error = $"Player '{playerId}' not found." });
            }

            if (limit < 1) limit = 1;
            if (limit > 100) limit = 100;

            var scores = await _cosmosService.GetPlayerScoresAsync(playerId, limit);
            var response = scores.Select(s => new ScoreHistoryItem
            {
                ScoreId = s.ScoreId,
                PlayerId = s.PlayerId,
                Score = s.ScoreValue,
                GameMode = s.GameMode,
                Timestamp = s.Timestamp
            }).ToList();

            return Ok(response);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error getting scores for player {PlayerId}", SanitizeForLog(playerId));
            return StatusCode(500, new { error = "Internal server error." });
        }
    }

    [HttpGet("{playerId}/rank")]
    public async Task<IActionResult> GetPlayerRank(string playerId)
    {
        try
        {
            var player = await _cosmosService.GetPlayerAsync(playerId);
            if (player == null)
            {
                return NotFound(new { error = $"Player '{playerId}' not found." });
            }

            if (player.BestScore == 0 && player.TotalGames == 0)
            {
                return NotFound(new { error = $"Player '{playerId}' has no scores." });
            }

            var entries = await _cosmosService.GetLeaderboardAsync("global_all-time", 10000);

            int playerIndex = -1;
            for (int i = 0; i < entries.Count; i++)
            {
                if (entries[i].PlayerId == playerId)
                {
                    playerIndex = i;
                    break;
                }
            }

            if (playerIndex == -1)
            {
                return NotFound(new { error = $"Player '{playerId}' not found on leaderboard." });
            }

            int rank = playerIndex + 1;

            int startIdx = Math.Max(0, playerIndex - 10);
            int endIdx = Math.Min(entries.Count - 1, playerIndex + 10);

            var neighbors = new List<LeaderboardItem>();
            for (int i = startIdx; i <= endIdx; i++)
            {
                if (i == playerIndex) continue;
                neighbors.Add(new LeaderboardItem
                {
                    Rank = i + 1,
                    PlayerId = entries[i].PlayerId,
                    DisplayName = entries[i].DisplayName,
                    Score = entries[i].BestScore
                });
            }

            return Ok(new PlayerRankResponse
            {
                PlayerId = playerId,
                Rank = rank,
                Score = player.BestScore,
                Neighbors = neighbors
            });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error getting rank for player {PlayerId}", SanitizeForLog(playerId));
            return StatusCode(500, new { error = "Internal server error." });
        }
    }

    private static PlayerResponse MapToPlayerResponse(Player player)
    {
        return new PlayerResponse
        {
            PlayerId = player.PlayerId,
            DisplayName = player.DisplayName,
            Region = player.Region,
            TotalGames = player.TotalGames,
            BestScore = player.BestScore,
            AverageScore = player.AverageScore
        };
    }

    [GeneratedRegex(@"[\r\n\t]")]
    private static partial Regex ControlCharsRegex();
}
