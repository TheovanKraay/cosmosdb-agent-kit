using Newtonsoft.Json;

namespace GamingLeaderboard.Models;

public class LeaderboardEntry
{
    [JsonProperty("id")]
    public string Id { get; set; } = string.Empty;

    [JsonProperty("playerId")]
    public string PlayerId { get; set; } = string.Empty;

    [JsonProperty("displayName")]
    public string DisplayName { get; set; } = string.Empty;

    [JsonProperty("region")]
    public string Region { get; set; } = string.Empty;

    [JsonProperty("score")]
    public int Score { get; set; }

    [JsonProperty("leaderboardKey")]
    public string LeaderboardKey { get; set; } = string.Empty;

    [JsonProperty("type")]
    public string Type { get; set; } = "leaderboardEntry";

    [JsonProperty("schemaVersion")]
    public int SchemaVersion { get; set; } = 1;
}
