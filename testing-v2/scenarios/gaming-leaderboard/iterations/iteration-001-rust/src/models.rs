use serde::{Deserialize, Serialize};

// Player document stored in Cosmos DB
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PlayerDoc {
    pub id: String,
    pub player_id: String,
    pub display_name: String,
    pub region: String,
    pub total_games: i64,
    pub best_score: i64,
    pub average_score: f64,
    #[serde(rename = "type")]
    pub doc_type: String,
    pub schema_version: i64,
    #[serde(rename = "_etag", skip_serializing_if = "Option::is_none")]
    pub etag: Option<String>,
}

// Player response (without internal fields)
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PlayerResponse {
    pub player_id: String,
    pub display_name: String,
    pub region: String,
    pub total_games: i64,
    pub best_score: i64,
    pub average_score: f64,
}

impl From<PlayerDoc> for PlayerResponse {
    fn from(doc: PlayerDoc) -> Self {
        Self {
            player_id: doc.player_id,
            display_name: doc.display_name,
            region: doc.region,
            total_games: doc.total_games,
            best_score: doc.best_score,
            average_score: doc.average_score,
        }
    }
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreatePlayerRequest {
    pub player_id: Option<String>,
    pub display_name: Option<String>,
    pub region: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdatePlayerRequest {
    pub display_name: Option<String>,
    pub region: Option<String>,
}

// Score document stored in Cosmos DB
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ScoreDoc {
    pub id: String,
    pub player_id: String,
    pub score: i64,
    pub game_mode: String,
    pub timestamp: String,
    #[serde(rename = "type")]
    pub doc_type: String,
    pub schema_version: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ScoreResponse {
    pub score_id: String,
    pub player_id: String,
    pub score: i64,
    pub game_mode: String,
    pub timestamp: String,
}

impl From<ScoreDoc> for ScoreResponse {
    fn from(doc: ScoreDoc) -> Self {
        Self {
            score_id: doc.id,
            player_id: doc.player_id,
            score: doc.score,
            game_mode: doc.game_mode,
            timestamp: doc.timestamp,
        }
    }
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateScoreRequest {
    pub player_id: Option<String>,
    pub score: Option<serde_json::Value>,
    pub game_mode: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ScoreCreateResponse {
    pub score_id: String,
    pub player_id: String,
    pub score: i64,
}

// Leaderboard document stored in Cosmos DB
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LeaderboardDoc {
    pub id: String,
    pub region: String,
    pub player_id: String,
    pub display_name: String,
    pub score: i64,
    #[serde(rename = "type")]
    pub doc_type: String,
    pub schema_version: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LeaderboardEntry {
    pub rank: i64,
    pub player_id: String,
    pub display_name: String,
    pub score: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RankResponse {
    pub player_id: String,
    pub rank: i64,
    pub score: i64,
    pub neighbors: Vec<LeaderboardEntry>,
}

#[derive(Debug, Deserialize)]
pub struct LimitQuery {
    pub limit: Option<i64>,
}

#[derive(Debug, Deserialize)]
pub struct TopQuery {
    pub top: Option<i64>,
}
