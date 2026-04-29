mod cosmos;
mod errors;
mod handlers;
mod models;

use axum::{
    routing::{get, post},
    Router,
};
use std::sync::Arc;
use tracing_subscriber;

use cosmos::CosmosDbClient;
use handlers::AppState;

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt::init();

    let endpoint = std::env::var("COSMOS_ENDPOINT")
        .unwrap_or_else(|_| "https://localhost:8081".to_string());
    let key = std::env::var("COSMOS_KEY").unwrap_or_else(|_| {
        "C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw=="
            .to_string()
    });
    let database = "gaming-leaderboard";

    let client = CosmosDbClient::new(&endpoint, &key, database);

    // Ensure database and containers exist
    if let Err(e) = client.ensure_database_and_containers().await {
        tracing::warn!("Failed to ensure DB/containers (may already exist): {}", e);
    }

    let state: AppState = Arc::new(client);

    let app = Router::new()
        .route("/health", get(handlers::health))
        .route("/api/players", post(handlers::create_player))
        .route(
            "/api/players/:playerId",
            get(handlers::get_player)
                .patch(handlers::update_player)
                .delete(handlers::delete_player),
        )
        .route("/api/scores", post(handlers::create_score))
        .route(
            "/api/players/:playerId/scores",
            get(handlers::get_player_scores),
        )
        .route(
            "/api/leaderboards/global",
            get(handlers::get_global_leaderboard),
        )
        .route(
            "/api/leaderboards/regional/:region",
            get(handlers::get_regional_leaderboard),
        )
        .route(
            "/api/players/:playerId/rank",
            get(handlers::get_player_rank),
        )
        .with_state(state);

    let listener = tokio::net::TcpListener::bind("0.0.0.0:8080")
        .await
        .expect("Failed to bind to port 8080");

    tracing::info!("Server listening on 0.0.0.0:8080");
    axum::serve(listener, app).await.expect("Server failed");
}
