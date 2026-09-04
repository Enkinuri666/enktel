// Prevent additional console window in release mode on Windows.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod xtream;
mod m3u;
mod xmltv;
mod db;
mod link;

use serde::Serialize;
use std::path::PathBuf;
use std::time::{Duration, Instant};
use tauri::{Emitter, Manager};

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

// ---- Sending downloads over from the phone or TV box -----------------------
//
// The Android app opens a small server on the house network and shows a PIN.
// These commands are the desktop end of it: find the device, pair once, then
// list, fetch and drive its download queue. Everything they know about the
// protocol lives in `link.rs`.

#[tauri::command]
async fn link_discover(timeout_ms: Option<u64>) -> Reply<Vec<link::Device>> {
    // A blocking UDP sweep, so it goes on the blocking pool rather than
    // parking a runtime worker for a second and a half.
    let ms = timeout_ms.unwrap_or(1500).clamp(300, 8000);
    match tauri::async_runtime::spawn_blocking(move || link::discover(Duration::from_millis(ms))).await {
        Ok(v) => Reply::ok(v),
        Err(e) => Reply::err(e.to_string()),
    }
}

#[tauri::command]
async fn link_pair(base: String, pin: String) -> Reply<link::Paired> {
    match link::pair(&base, &pin).await {
        Ok(v) => Reply::ok(v),
        Err(e) => Reply::err(e.to_string()),
    }
}

#[tauri::command]
async fn link_files(base: String, token: String) -> Reply<Vec<link::RemoteFile>> {
    match link::files(&base, &token).await {
        Ok(v) => Reply::ok(v),
        Err(e) => Reply::err(e.to_string()),
    }
}

#[tauri::command]
async fn link_downloads(base: String, token: String) -> Reply<Vec<link::Job>> {
    match link::downloads(&base, &token).await {
        Ok(v) => Reply::ok(v),
        Err(e) => Reply::err(e.to_string()),
    }
}

#[tauri::command]
async fn link_act(base: String, token: String, id: String, action: String) -> Reply<bool> {
    match link::act(&base, &token, &id, &action).await {
        Ok(v) => Reply::ok(v),
        Err(e) => Reply::err(e.to_string()),
    }
}

/// The folder a save defaults to — the user's own Downloads, not ours.
#[tauri::command]
fn link_default_dir(app: tauri::AppHandle) -> Reply<String> {
    let dir: Option<PathBuf> = app
        .path()
        .download_dir()
        .ok()
        .or_else(|| app.path().home_dir().ok());
    match dir {
        Some(d) => Reply::ok(d.to_string_lossy().to_string()),
        None => Reply::err("Could not work out where your Downloads folder is.".into()),
    }
}

/// Fetch one shared file, emitting `link://progress` as it lands.
#[tauri::command]
async fn link_save(
    app: tauri::AppHandle,
    base: String,
    token: String,
    file_token: String,
    name: String,
    dir: String,
) -> Reply<String> {
    let emit_token = file_token.clone();
    let emit_name = name.clone();
    // Throttled: a 6 GB film arrives in tens of thousands of chunks, and an
    // event per chunk is a webview that spends the whole transfer re-rendering
    // a progress bar instead of drawing it.
    let mut last: Option<Instant> = None;
    let handle = app.clone();

    let result = link::save_file(
        &base,
        &token,
        &file_token,
        &name,
        std::path::Path::new(&dir),
        |received, total| {
            let due = last.map_or(true, |t: Instant| t.elapsed() >= Duration::from_millis(200));
            if !due && received != total {
                return;
            }
            last = Some(Instant::now());
            let _ = handle.emit(
                "link://progress",
                link::SaveProgress {
                    token: emit_token.clone(),
                    name: emit_name.clone(),
                    received,
                    total,
                    done: false,
                    error: None,
                },
            );
        },
    )
    .await;

    // A final event either way, so the UI never leaves a row stuck at 99%
    // because the last throttled tick was swallowed.
    match result {
        Ok(path) => {
            let _ = app.emit(
                "link://progress",
                link::SaveProgress {
                    token: file_token,
                    name,
                    received: 0,
                    total: 0,
                    done: true,
                    error: None,
                },
            );
            Reply::ok(path.to_string_lossy().to_string())
        }
        Err(e) => {
            let msg = e.to_string();
            let _ = app.emit(
                "link://progress",
                link::SaveProgress {
                    token: file_token,
                    name,
                    received: 0,
                    total: 0,
                    done: true,
                    error: Some(msg.clone()),
                },
            );
            Reply::err(msg)
        }
    }
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
            link_discover,
            link_pair,
            link_files,
            link_downloads,
            link_act,
            link_default_dir,
            link_save,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
