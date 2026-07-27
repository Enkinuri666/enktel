// Prevent additional console window in release mode on Windows.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod xtream;
mod m3u;
mod xmltv;
mod db;

use serde::Serialize;

/// Minimal ping so the frontend can verify the Rust ↔ WebView bridge is up.
#[tauri::command]
fn ping() -> String { "EnkTel IPTV backend online".into() }

/// Wrapper struct for command results — the frontend always inspects `.ok`
/// before touching `.data` / `.error`, matching the pattern React Query's
/// `select` helpers expect.
#[derive(Serialize)]
pub struct Reply<T: Serialize> {
    ok: bool,
    data: Option<T>,
    error: Option<String>,
}

impl<T: Serialize> Reply<T> {
    fn ok(v: T) -> Self { Reply { ok: true, data: Some(v), error: None } }
    fn err(msg: String) -> Self { Reply { ok: false, data: None, error: Some(msg) } }
}

// ---- Xtream login ---------------------------------------------------------

#[tauri::command]
async fn xtream_login(
    server: String, username: String, password: String,
) -> Reply<xtream::ServerInfo> {
    match xtream::login(&server, &username, &password).await {
        Ok(s) => Reply::ok(s),
        Err(e) => Reply::err(e.to_string()),
    }
}

// ---- Catalog fetchers -----------------------------------------------------

#[tauri::command]
async fn xtream_categories(
    server: String, username: String, password: String, kind: String,
) -> Reply<Vec<xtream::Category>> {
    match xtream::list_categories(&server, &username, &password, &kind).await {
        Ok(v) => Reply::ok(v),
        Err(e) => Reply::err(e.to_string()),
    }
}

#[tauri::command]
async fn xtream_live(
    server: String, username: String, password: String,
) -> Reply<Vec<xtream::Channel>> {
    match xtream::list_live(&server, &username, &password).await {
        Ok(v) => Reply::ok(v),
        Err(e) => Reply::err(e.to_string()),
    }
}

#[tauri::command]
async fn xtream_movies(
    server: String, username: String, password: String,
) -> Reply<Vec<xtream::Movie>> {
    match xtream::list_movies(&server, &username, &password).await {
        Ok(v) => Reply::ok(v),
        Err(e) => Reply::err(e.to_string()),
    }
}

#[tauri::command]
async fn xtream_series(
    server: String, username: String, password: String,
) -> Reply<Vec<xtream::Series>> {
    match xtream::list_series(&server, &username, &password).await {
        Ok(v) => Reply::ok(v),
        Err(e) => Reply::err(e.to_string()),
    }
}

// ---- URL builders ---------------------------------------------------------
// These are pure functions but exposed as async commands so the frontend
// can call them via the same invoke() plumbing without a wasm-friendly
// URL builder having to be duplicated in TS.

#[tauri::command]
fn xtream_live_url(
    server: String, username: String, password: String, stream_id: i64, prefer_hls: bool,
) -> Vec<String> {
    xtream::live_url(&server, &username, &password, stream_id, prefer_hls)
}

#[tauri::command]
fn xtream_movie_url(
    server: String, username: String, password: String, stream_id: i64, container_extension: String,
) -> Vec<String> {
    xtream::movie_url(&server, &username, &password, stream_id, &container_extension)
}

#[tauri::command]
fn xtream_episode_url(
    server: String, username: String, password: String, episode_id: i64, container_extension: String,
) -> Vec<String> {
    xtream::episode_url(&server, &username, &password, episode_id, &container_extension)
}

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .invoke_handler(tauri::generate_handler![
            ping,
            xtream_login,
            xtream_categories,
            xtream_live,
            xtream_movies,
            xtream_series,
            xtream_live_url,
            xtream_movie_url,
            xtream_episode_url,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
