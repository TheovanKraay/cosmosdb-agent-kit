using Newtonsoft.Json;

namespace GamingLeaderboard.Models;

public class LeaderboardEntry
{
    [JsonProperty("id")]
    public string Id { get; set; } = string.Empty;

    [JsonProperty("leaderboardKey")]
    public string LeaderboardKey { get; set; } = string.Empty;

    [JsonProperty("playerId")]
    public string PlayerId { get; set; } = string.Empty;

    [JsonProperty("displayName")]
    public string DisplayName { get; set; } = string.Empty;

    [JsonProperty("region")]
    public string Region { get; set; } = string.Empty;

    [JsonProperty("bestScore")]
    public int BestScore { get; set; }

    [JsonProperty("type")]
    public string Type { get; set; } = "leaderboardEntry";

    [JsonProperty("schemaVersion")]
    public int SchemaVersion { get; set; } = 1;

    [JsonProperty("updatedAt")]
    public string UpdatedAt { get; set; } = DateTime.UtcNow.ToString("o");
}
