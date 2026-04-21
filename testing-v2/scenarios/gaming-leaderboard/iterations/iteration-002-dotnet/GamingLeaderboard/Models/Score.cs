using Newtonsoft.Json;

namespace GamingLeaderboard.Models;

public class Score
{
    [JsonProperty("id")]
    public string Id { get; set; } = Guid.NewGuid().ToString();

    [JsonProperty("scoreId")]
    public string ScoreId { get; set; } = string.Empty;

    [JsonProperty("playerId")]
    public string PlayerId { get; set; } = string.Empty;

    [JsonProperty("score")]
    public int Value { get; set; }

    [JsonProperty("gameMode")]
    public string? GameMode { get; set; }

    [JsonProperty("timestamp")]
    public string Timestamp { get; set; } = DateTime.UtcNow.ToString("o");

    [JsonProperty("type")]
    public string Type { get; set; } = "score";

    [JsonProperty("schemaVersion")]
    public int SchemaVersion { get; set; } = 1;
}
