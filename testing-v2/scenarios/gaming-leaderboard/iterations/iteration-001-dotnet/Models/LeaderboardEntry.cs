using System.Text.Json.Serialization;
using Newtonsoft.Json;

namespace GamingLeaderboard.Models;

public class LeaderboardEntry
{
    [JsonProperty("id")]
    [JsonPropertyName("id")]
    public string Id { get; set; } = string.Empty;

    [JsonProperty("leaderboardKey")]
    [JsonPropertyName("leaderboardKey")]
    public string LeaderboardKey { get; set; } = string.Empty;

    [JsonProperty("playerId")]
    [JsonPropertyName("playerId")]
    public string PlayerId { get; set; } = string.Empty;

    [JsonProperty("displayName")]
    [JsonPropertyName("displayName")]
    public string DisplayName { get; set; } = string.Empty;

    [JsonProperty("region")]
    [JsonPropertyName("region")]
    public string Region { get; set; } = string.Empty;

    [JsonProperty("score")]
    [JsonPropertyName("score")]
    public int Score { get; set; }

    [JsonProperty("type")]
    [JsonPropertyName("type")]
    public string Type { get; set; } = "leaderboardEntry";

    [JsonProperty("schemaVersion")]
    [JsonPropertyName("schemaVersion")]
    public int SchemaVersion { get; set; } = 1;
}
