use axum::{
    extract::{Path, Query, State},
    http::StatusCode,
    response::IntoResponse,
    Json,
};
use serde_json::{json, Value};
use std::sync::Arc;
use uuid::Uuid;

use crate::cosmos::CosmosDbClient;
use crate::errors::AppError;
use crate::models::*;

pub type AppState = Arc<CosmosDbClient>;

// GET /health
pub async fn health() -> StatusCode {
    StatusCode::OK
}

// POST /api/players
pub async fn create_player(
    State(db): State<AppState>,
    body: Option<Json<Value>>,
) -> Result<impl IntoResponse, AppError> {
    let body = body.ok_or_else(|| AppError::BadRequest("Request body is required".into()))?;
    let body = body.0;

    if body.is_null() || (body.is_object() && body.as_object().unwrap().is_empty()) {
        return Err(AppError::BadRequest("Request body cannot be empty".into()));
    }

    let req: CreatePlayerRequest =
        serde_json::from_value(body).map_err(|e| AppError::BadRequest(e.to_string()))?;

    let player_id = req
        .player_id
        .filter(|s| !s.is_empty())
        .ok_or_else(|| AppError::BadRequest("playerId is required".into()))?;
    let display_name = req
        .display_name
        .filter(|s| !s.is_empty())
        .ok_or_else(|| AppError::BadRequest("displayName is required".into()))?;
    let region = req
        .region
        .filter(|s| !s.is_empty())
        .ok_or_else(|| AppError::BadRequest("region is required".into()))?;

    let doc = PlayerDoc {
        id: player_id.clone(),
        player_id: player_id.clone(),
        display_name: display_name.clone(),
        region: region.clone(),
        total_games: 0,
        best_score: 0.0,
        average_score: 0.0,
        doc_type: "player".to_string(),
        etag: None,
    };

    let resp = db
        .create_document("players", &player_id, &doc)
        .await
        .map_err(|e| AppError::Internal(e))?;

    if resp.status == 409 {
        return Err(AppError::Conflict(format!(
            "Player {} already exists",
            player_id
        )));
    }
    if resp.status >= 400 {
        return Err(AppError::Internal(format!(
            "Failed to create player: {}",
            resp.body
        )));
    }

    let response = PlayerResponse {
        player_id,
        display_name,
        region,
        total_games: 0,
        best_score: 0.0,
        average_score: 0.0,
    };

    Ok((StatusCode::CREATED, Json(response)))
}

// GET /api/players/:playerId
pub async fn get_player(
    State(db): State<AppState>,
    Path(player_id): Path<String>,
) -> Result<impl IntoResponse, AppError> {
    let resp = db
        .read_document("players", &player_id, &player_id)
        .await
        .map_err(|e| AppError::Internal(e))?;

    if resp.status == 404 {
        return Err(AppError::NotFound(format!("Player {} not found", player_id)));
    }
    if resp.status >= 400 {
        return Err(AppError::Internal(format!("Error reading player: {}", resp.body)));
    }

    let doc: PlayerDoc =
        serde_json::from_value(resp.body).map_err(|e| AppError::Internal(e.to_string()))?;

    Ok(Json(PlayerResponse::from(doc)))
}

// PATCH /api/players/:playerId
pub async fn update_player(
    State(db): State<AppState>,
    Path(player_id): Path<String>,
    Json(req): Json<UpdatePlayerRequest>,
) -> Result<impl IntoResponse, AppError> {
    // Read current player
    let resp = db
        .read_document("players", &player_id, &player_id)
        .await
        .map_err(|e| AppError::Internal(e))?;

    if resp.status == 404 {
        return Err(AppError::NotFound(format!("Player {} not found", player_id)));
    }
    if resp.status >= 400 {
        return Err(AppError::Internal(format!("Error: {}", resp.body)));
    }

    let mut doc: PlayerDoc =
        serde_json::from_value(resp.body).map_err(|e| AppError::Internal(e.to_string()))?;
    let old_region = doc.region.clone();

    if let Some(name) = &req.display_name {
        doc.display_name = name.clone();
    }
    if let Some(region) = &req.region {
        doc.region = region.clone();
    }

    doc.etag = None; // Don't send etag back as field

    let replace_resp = db
        .replace_document("players", &player_id, &player_id, &doc, None)
        .await
        .map_err(|e| AppError::Internal(e))?;

    if replace_resp.status >= 400 {
        return Err(AppError::Internal(format!(
            "Failed to update player: {}",
            replace_resp.body
        )));
    }

    // If region changed or displayName changed, update leaderboard entries
    let region_changed = req.region.is_some() && doc.region != old_region;
    let name_changed = req.display_name.is_some();

    if region_changed || name_changed {
        // Update global leaderboard entry
        update_leaderboard_entry(&db, &player_id, &doc.display_name, doc.best_score, "global")
            .await;

        if region_changed {
            // Delete old regional entry
            let _ = db
                .delete_document("leaderboards", &player_id, &old_region)
                .await;
            // Create new regional entry
            if doc.best_score > 0.0 || doc.total_games > 0 {
                update_leaderboard_entry(
                    &db,
                    &player_id,
                    &doc.display_name,
                    doc.best_score,
                    &doc.region,
                )
                .await;
            }
        } else if name_changed {
            // Update regional entry with new name
            update_leaderboard_entry(
                &db,
                &player_id,
                &doc.display_name,
                doc.best_score,
                &doc.region,
            )
            .await;
        }
    }

    Ok(Json(PlayerResponse::from(doc)))
}

// DELETE /api/players/:playerId
pub async fn delete_player(
    State(db): State<AppState>,
    Path(player_id): Path<String>,
) -> Result<impl IntoResponse, AppError> {
    // Check player exists
    let resp = db
        .read_document("players", &player_id, &player_id)
        .await
        .map_err(|e| AppError::Internal(e))?;

    if resp.status == 404 {
        return Err(AppError::NotFound(format!("Player {} not found", player_id)));
    }

    let doc: PlayerDoc =
        serde_json::from_value(resp.body).map_err(|e| AppError::Internal(e.to_string()))?;

    // Delete all scores for this player
    let scores = db
        .query_documents(
            "scores",
            "SELECT c.id FROM c WHERE c.playerId = @pid",
            vec![json!({"name": "@pid", "value": player_id.clone()})],
            Some(&player_id),
        )
        .await
        .unwrap_or_default();

    for score in &scores {
        if let Some(id) = score["id"].as_str() {
            let _ = db.delete_document("scores", id, &player_id).await;
        }
    }

    // Delete leaderboard entries
    let _ = db
        .delete_document("leaderboards", &player_id, &doc.region)
        .await;
    let _ = db
        .delete_document("leaderboards", &player_id, "global")
        .await;

    // Delete player
    let status = db
        .delete_document("players", &player_id, &player_id)
        .await
        .map_err(|e| AppError::Internal(e))?;

    if status == 404 {
        return Err(AppError::NotFound(format!("Player {} not found", player_id)));
    }

    Ok(StatusCode::NO_CONTENT)
}

// POST /api/scores
pub async fn create_score(
    State(db): State<AppState>,
    body: Option<Json<Value>>,
) -> Result<impl IntoResponse, AppError> {
    let body = body.ok_or_else(|| AppError::BadRequest("Request body is required".into()))?;
    let body = body.0;

    let req: CreateScoreRequest =
        serde_json::from_value(body).map_err(|e| AppError::BadRequest(e.to_string()))?;

    let player_id = req
        .player_id
        .filter(|s| !s.is_empty())
        .ok_or_else(|| AppError::BadRequest("playerId is required".into()))?;

    let score_val = req
        .score
        .ok_or_else(|| AppError::BadRequest("score is required".into()))?;

    let score: f64 = match &score_val {
        Value::Number(n) => n.as_f64().ok_or_else(|| AppError::BadRequest("Invalid score".into()))?,
        _ => return Err(AppError::BadRequest("score must be a number".into())),
    };

    if score < 0.0 {
        return Err(AppError::BadRequest("score must not be negative".into()));
    }

    let game_mode = req.game_mode.unwrap_or_else(|| "default".to_string());

    // Verify player exists
    let player_resp = db
        .read_document("players", &player_id, &player_id)
        .await
        .map_err(|e| AppError::Internal(e))?;

    if player_resp.status == 404 {
        return Err(AppError::BadRequest(format!(
            "Player {} not found",
            player_id
        )));
    }
    if player_resp.status >= 400 {
        return Err(AppError::Internal(format!(
            "Error reading player: {}",
            player_resp.body
        )));
    }

    // Create score document
    let score_id = Uuid::new_v4().to_string();
    let timestamp = chrono::Utc::now().to_rfc3339();

    let score_doc = ScoreDoc {
        id: score_id.clone(),
        player_id: player_id.clone(),
        score,
        game_mode,
        timestamp,
        doc_type: "score".to_string(),
    };

    let create_resp = db
        .create_document("scores", &player_id, &score_doc)
        .await
        .map_err(|e| AppError::Internal(e))?;

    if create_resp.status >= 400 {
        return Err(AppError::Internal(format!(
            "Failed to create score: {}",
            create_resp.body
        )));
    }

    // Update player stats with ETag-based optimistic concurrency
    let max_retries = 50;
    for attempt in 0..max_retries {
        let p_resp = db
            .read_document("players", &player_id, &player_id)
            .await
            .map_err(|e| AppError::Internal(e))?;

        if p_resp.status >= 400 {
            return Err(AppError::Internal("Failed to read player for update".into()));
        }

        let etag = p_resp.etag.clone();
        let mut player: PlayerDoc = serde_json::from_value(p_resp.body)
            .map_err(|e| AppError::Internal(e.to_string()))?;

        let new_total = player.total_games + 1;
        let new_avg =
            (player.average_score * player.total_games as f64 + score) / new_total as f64;
        let new_best = if score > player.best_score {
            score
        } else {
            player.best_score
        };

        player.total_games = new_total;
        player.average_score = new_avg;
        player.best_score = new_best;

        let player_etag_for_replace = player.etag.clone();
        player.etag = None;

        let replace_resp = db
            .replace_document(
                "players",
                &player_id,
                &player_id,
                &player,
                etag.as_deref().or(player_etag_for_replace.as_deref()),
            )
            .await
            .map_err(|e| AppError::Internal(e))?;

        if replace_resp.status == 412 {
            // Precondition failed - retry
            if attempt < max_retries - 1 {
                tokio::time::sleep(tokio::time::Duration::from_millis(10 * (attempt as u64 + 1)))
                    .await;
                continue;
            }
            return Err(AppError::Internal(
                "Failed to update player stats after retries".into(),
            ));
        }

        if replace_resp.status >= 400 {
            return Err(AppError::Internal(format!(
                "Failed to update player: {}",
                replace_resp.body
            )));
        }

        // Update leaderboard entries with new best score
        update_leaderboard_entry(
            &db,
            &player_id,
            &player.display_name,
            player.best_score,
            "global",
        )
        .await;
        update_leaderboard_entry(
            &db,
            &player_id,
            &player.display_name,
            player.best_score,
            &player.region,
        )
        .await;

        break;
    }

    let response = ScoreCreateResponse {
        score_id,
        player_id,
        score,
    };

    Ok((StatusCode::CREATED, Json(response)))
}

async fn update_leaderboard_entry(
    db: &CosmosDbClient,
    player_id: &str,
    display_name: &str,
    score: f64,
    region: &str,
) {
    let entry = crate::models::LeaderboardDoc {
        id: player_id.to_string(),
        region: region.to_string(),
        player_id: player_id.to_string(),
        display_name: display_name.to_string(),
        score,
        doc_type: "leaderboard".to_string(),
    };

    let _ = db.upsert_document("leaderboards", region, &entry).await;
}

// GET /api/players/:playerId/scores
pub async fn get_player_scores(
    State(db): State<AppState>,
    Path(player_id): Path<String>,
    Query(params): Query<LimitQuery>,
) -> Result<impl IntoResponse, AppError> {
    // Check player exists
    let player_resp = db
        .read_document("players", &player_id, &player_id)
        .await
        .map_err(|e| AppError::Internal(e))?;

    if player_resp.status == 404 {
        return Err(AppError::NotFound(format!("Player {} not found", player_id)));
    }

    let limit = params.limit.unwrap_or(10);

    let docs = db
        .query_documents(
            "scores",
            &format!(
                "SELECT TOP {} * FROM c WHERE c.playerId = @pid ORDER BY c.timestamp DESC",
                limit
            ),
            vec![json!({"name": "@pid", "value": player_id.clone()})],
            Some(&player_id),
        )
        .await
        .map_err(|e| AppError::Internal(e))?;

    let scores: Vec<ScoreResponse> = docs
        .into_iter()
        .filter_map(|v| {
            let doc: ScoreDoc = serde_json::from_value(v).ok()?;
            Some(ScoreResponse::from(doc))
        })
        .collect();

    Ok(Json(scores))
}

// GET /api/leaderboards/global
pub async fn get_global_leaderboard(
    State(db): State<AppState>,
    Query(params): Query<TopQuery>,
) -> Result<impl IntoResponse, AppError> {
    get_leaderboard_by_region(&db, "global", params.top.unwrap_or(100)).await
}

// GET /api/leaderboards/regional/:region
pub async fn get_regional_leaderboard(
    State(db): State<AppState>,
    Path(region): Path<String>,
    Query(params): Query<TopQuery>,
) -> Result<impl IntoResponse, AppError> {
    get_leaderboard_by_region(&db, &region, params.top.unwrap_or(100)).await
}

async fn get_leaderboard_by_region(
    db: &CosmosDbClient,
    region: &str,
    top: u64,
) -> Result<impl IntoResponse, AppError> {
    let docs = db
        .query_documents(
            "leaderboards",
            "SELECT * FROM c WHERE c.region = @region ORDER BY c.score DESC, c.displayName ASC",
            vec![json!({"name": "@region", "value": region})],
            Some(region),
        )
        .await
        .map_err(|e| AppError::Internal(e))?;

    let entries: Vec<LeaderboardEntry> = docs
        .into_iter()
        .take(top as usize)
        .enumerate()
        .map(|(i, v)| {
            let player_id = v["playerId"].as_str().unwrap_or("").to_string();
            let display_name = v["displayName"].as_str().unwrap_or("").to_string();
            let score = v["score"].as_f64().unwrap_or(0.0);
            LeaderboardEntry {
                rank: (i + 1) as u64,
                player_id,
                display_name,
                score,
            }
        })
        .collect();

    Ok(Json(entries))
}

// GET /api/players/:playerId/rank
pub async fn get_player_rank(
    State(db): State<AppState>,
    Path(player_id): Path<String>,
) -> Result<impl IntoResponse, AppError> {
    // Check player exists
    let player_resp = db
        .read_document("players", &player_id, &player_id)
        .await
        .map_err(|e| AppError::Internal(e))?;

    if player_resp.status == 404 {
        return Err(AppError::NotFound(format!("Player {} not found", player_id)));
    }

    // Get full global leaderboard to find rank
    let docs = db
        .query_documents(
            "leaderboards",
            "SELECT * FROM c WHERE c.region = 'global' ORDER BY c.score DESC, c.displayName ASC",
            vec![],
            Some("global"),
        )
        .await
        .map_err(|e| AppError::Internal(e))?;

    let entries: Vec<LeaderboardEntry> = docs
        .iter()
        .enumerate()
        .map(|(i, v)| {
            let pid = v["playerId"].as_str().unwrap_or("").to_string();
            let dn = v["displayName"].as_str().unwrap_or("").to_string();
            let sc = v["score"].as_f64().unwrap_or(0.0);
            LeaderboardEntry {
                rank: (i + 1) as u64,
                player_id: pid,
                display_name: dn,
                score: sc,
            }
        })
        .collect();

    // Find player in leaderboard
    let player_entry = entries.iter().find(|e| e.player_id == player_id);

    match player_entry {
        Some(entry) => {
            let rank = entry.rank;
            let score = entry.score;

            // Get neighbors (2 above, 2 below)
            let idx = (rank - 1) as usize;
            let start = if idx >= 2 { idx - 2 } else { 0 };
            let end = std::cmp::min(idx + 3, entries.len());
            let neighbors: Vec<LeaderboardEntry> = entries[start..end]
                .iter()
                .filter(|e| e.player_id != player_id)
                .cloned()
                .collect();

            Ok(Json(RankResponse {
                player_id,
                rank,
                score,
                neighbors,
            }))
        }
        None => {
            // Player exists but has no scores yet
            Ok(Json(RankResponse {
                player_id,
                rank: 0,
                score: 0.0,
                neighbors: vec![],
            }))
        }
    }
}
