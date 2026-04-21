using System.Text.Json.Serialization;

namespace GamingLeaderboard.Models;

public class CreatePlayerRequest
{
    [JsonPropertyName("playerId")]
    public string? PlayerId { get; set; }

    [JsonPropertyName("displayName")]
    public string? DisplayName { get; set; }

    [JsonPropertyName("region")]
    public string? Region { get; set; }
}

public class UpdatePlayerRequest
{
    [JsonPropertyName("displayName")]
    public string? DisplayName { get; set; }

    [JsonPropertyName("region")]
    public string? Region { get; set; }
}

public class SubmitScoreRequest
{
    [JsonPropertyName("playerId")]
    public string? PlayerId { get; set; }

    [JsonPropertyName("score")]
    public int? Score { get; set; }

    [JsonPropertyName("gameMode")]
    public string? GameMode { get; set; }
}

public class PlayerResponse
{
    [JsonPropertyName("playerId")]
    public string PlayerId { get; set; } = string.Empty;

    [JsonPropertyName("displayName")]
    public string DisplayName { get; set; } = string.Empty;

    [JsonPropertyName("region")]
    public string Region { get; set; } = string.Empty;

    [JsonPropertyName("totalGames")]
    public int TotalGames { get; set; }

    [JsonPropertyName("bestScore")]
    public int BestScore { get; set; }

    [JsonPropertyName("averageScore")]
    public double AverageScore { get; set; }

    public static PlayerResponse FromPlayer(Player player)
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
}

public class ScoreResponse
{
    [JsonPropertyName("scoreId")]
    public string ScoreId { get; set; } = string.Empty;

    [JsonPropertyName("playerId")]
    public string PlayerId { get; set; } = string.Empty;

    [JsonPropertyName("score")]
    public int Score { get; set; }
}

public class ScoreHistoryEntry
{
    [JsonPropertyName("scoreId")]
    public string ScoreId { get; set; } = string.Empty;

    [JsonPropertyName("playerId")]
    public string PlayerId { get; set; } = string.Empty;

    [JsonPropertyName("score")]
    public int Score { get; set; }

    [JsonPropertyName("gameMode")]
    public string? GameMode { get; set; }

    [JsonPropertyName("timestamp")]
    public string Timestamp { get; set; } = string.Empty;
}

public class LeaderboardEntryResponse
{
    [JsonPropertyName("rank")]
    public int Rank { get; set; }

    [JsonPropertyName("playerId")]
    public string PlayerId { get; set; } = string.Empty;

    [JsonPropertyName("displayName")]
    public string DisplayName { get; set; } = string.Empty;

    [JsonPropertyName("score")]
    public int Score { get; set; }
}

public class PlayerRankResponse
{
    [JsonPropertyName("playerId")]
    public string PlayerId { get; set; } = string.Empty;

    [JsonPropertyName("rank")]
    public int Rank { get; set; }

    [JsonPropertyName("score")]
    public int Score { get; set; }

    [JsonPropertyName("neighbors")]
    public List<LeaderboardEntryResponse> Neighbors { get; set; } = new();
}
