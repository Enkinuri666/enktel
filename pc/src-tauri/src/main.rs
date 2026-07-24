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

/// Xtream login → server info (auth, base URL). Frontend uses this for the
/// onboarding "test connection" button and the subsequent library imports.
#[derive(Serialize)]
struct XtreamAuthResult {
    ok: bool,
    server: Option<xtream::ServerInfo>,
    error: Option<String>,
}

#[tauri::command]
async fn xtream_login(
    server: String, username: String, password: String,
) -> XtreamAuthResult {
    match xtream::login(&server, &username, &password).await {
        Ok(server) => XtreamAuthResult { ok: true, server: Some(server), error: None },
        Err(e) => XtreamAuthResult { ok: false, server: None, error: Some(e.to_string()) },
    }
}

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .invoke_handler(tauri::generate_handler![ping, xtream_login])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
