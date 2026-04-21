using Newtonsoft.Json;

namespace GamingLeaderboard.Models;

public class Score
{
    [JsonProperty("id")]
    public string Id { get; set; } = string.Empty;

    [JsonProperty("scoreId")]
    public string ScoreId { get; set; } = string.Empty;

    [JsonProperty("playerId")]
    public string PlayerId { get; set; } = string.Empty;

    [JsonProperty("score")]
    public int ScoreValue { get; set; }

    [JsonProperty("gameMode")]
    public string? GameMode { get; set; }

    [JsonProperty("timestamp")]
    public string Timestamp { get; set; } = string.Empty;

    [JsonProperty("type")]
    public string Type { get; set; } = "score";

    [JsonProperty("schemaVersion")]
    public string SchemaVersion { get; set; } = "1.0";
}
