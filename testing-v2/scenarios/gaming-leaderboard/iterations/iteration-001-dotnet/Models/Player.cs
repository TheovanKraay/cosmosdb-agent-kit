using System.Text.Json.Serialization;
using Newtonsoft.Json;

namespace GamingLeaderboard.Models;

public class Player
{
    [JsonProperty("id")]
    [JsonPropertyName("id")]
    public string Id { get; set; } = string.Empty;

    [JsonProperty("playerId")]
    [JsonPropertyName("playerId")]
    public string PlayerId { get; set; } = string.Empty;

    [JsonProperty("displayName")]
    [JsonPropertyName("displayName")]
    public string DisplayName { get; set; } = string.Empty;

    [JsonProperty("region")]
    [JsonPropertyName("region")]
    public string Region { get; set; } = string.Empty;

    [JsonProperty("totalGames")]
    [JsonPropertyName("totalGames")]
    public int TotalGames { get; set; }

    [JsonProperty("bestScore")]
    [JsonPropertyName("bestScore")]
    public int BestScore { get; set; }

    [JsonProperty("averageScore")]
    [JsonPropertyName("averageScore")]
    public double AverageScore { get; set; }

    [JsonProperty("totalScore")]
    [JsonPropertyName("totalScore")]
    public long TotalScore { get; set; }

    [JsonProperty("type")]
    [JsonPropertyName("type")]
    public string Type { get; set; } = "player";

    [JsonProperty("schemaVersion")]
    [JsonPropertyName("schemaVersion")]
    public int SchemaVersion { get; set; } = 1;
}
