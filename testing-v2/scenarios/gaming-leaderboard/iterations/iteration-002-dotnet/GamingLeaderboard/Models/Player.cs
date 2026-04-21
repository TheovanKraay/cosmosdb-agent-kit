using Newtonsoft.Json;

namespace GamingLeaderboard.Models;

public class Player
{
    [JsonProperty("id")]
    public string Id { get; set; } = string.Empty;

    [JsonProperty("playerId")]
    public string PlayerId { get; set; } = string.Empty;

    [JsonProperty("displayName")]
    public string DisplayName { get; set; } = string.Empty;

    [JsonProperty("region")]
    public string Region { get; set; } = string.Empty;

    [JsonProperty("totalGames")]
    public int TotalGames { get; set; }

    [JsonProperty("bestScore")]
    public int BestScore { get; set; }

    [JsonProperty("totalScore")]
    public long TotalScore { get; set; }

    [JsonProperty("averageScore")]
    public double AverageScore { get; set; }

    [JsonProperty("type")]
    public string Type { get; set; } = "player";

    [JsonProperty("schemaVersion")]
    public int SchemaVersion { get; set; } = 1;

    [JsonProperty("createdAt")]
    public string CreatedAt { get; set; } = DateTime.UtcNow.ToString("o");

    [JsonProperty("updatedAt")]
    public string UpdatedAt { get; set; } = DateTime.UtcNow.ToString("o");

    [JsonProperty("_etag")]
    public string? ETag { get; set; }
}
