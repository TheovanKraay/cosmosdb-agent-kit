using System.Text.Json.Serialization;
using Newtonsoft.Json;

namespace GamingLeaderboard.Models;

public class Score
{
    [JsonProperty("id")]
    [JsonPropertyName("id")]
    public string Id { get; set; } = string.Empty;

    [JsonProperty("scoreId")]
    [JsonPropertyName("scoreId")]
    public string ScoreId { get; set; } = string.Empty;

    [JsonProperty("playerId")]
    [JsonPropertyName("playerId")]
    public string PlayerId { get; set; } = string.Empty;

    [JsonProperty("score")]
    [JsonPropertyName("score")]
    public int ScoreValue { get; set; }

    [JsonProperty("gameMode")]
    [JsonPropertyName("gameMode")]
    public string? GameMode { get; set; }

    [JsonProperty("timestamp")]
    [JsonPropertyName("timestamp")]
    public string Timestamp { get; set; } = string.Empty;

    [JsonProperty("type")]
    [JsonPropertyName("type")]
    public string Type { get; set; } = "score";

    [JsonProperty("schemaVersion")]
    [JsonPropertyName("schemaVersion")]
    public int SchemaVersion { get; set; } = 1;
}
