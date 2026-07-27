use serde::{Deserialize, Serialize};

/// Cleaned-up shape of the `player_api.php` server_info response.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ServerInfo {
    pub url: String,
    pub port: u32,
    pub timezone: Option<String>,
    pub time_now: Option<String>,
    pub server_protocol: Option<String>,
}

/// A single content category (Live TV, Movies, Series). Xtream calls these
/// "categories" and the id is the same string across all three kinds.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Category {
    pub id: String,
    pub name: String,
}

/// Live TV channel — enough for the sidebar list + the URL builder to
/// hand off to the frontend Player.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Channel {
    pub stream_id: i64,
    pub name: String,
    pub num: i64,
    pub logo: String,
    pub category_id: String,
    pub epg_channel_id: String,
    pub tv_archive: bool,
}

/// VOD (movie) entry.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Movie {
    pub stream_id: i64,
    pub name: String,
    pub poster: String,
    pub category_id: String,
    pub rating: f64,
    pub container_extension: String,
    pub added: i64,
    pub year: Option<i32>,
    pub tmdb_id: Option<i64>,
}

/// Series entry (season/episode metadata comes from `series_info`).
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Series {
    pub series_id: i64,
    pub name: String,
    pub cover: String,
    pub category_id: String,
    pub rating: f64,
    pub plot: String,
    pub year: Option<i32>,
    pub tmdb_id: Option<i64>,
}

// ---- Log in ---------------------------------------------------------------

/// Log in to an Xtream Codes panel. Uses `player_api.php` with the standard
/// user_info + server_info bundle.
pub async fn login(server: &str, username: &str, password: &str) -> anyhow::Result<ServerInfo> {
    let json = call(server, username, password, None).await?;
    let auth = json
        .get("user_info")
        .and_then(|u| u.get("auth"))
        .and_then(|a| a.as_i64())
        .ok_or_else(|| anyhow::anyhow!("Panel returned no auth field"))?;
    if auth != 1 {
        anyhow::bail!("Panel refused the credentials");
    }
    let si = json
        .get("server_info")
        .ok_or_else(|| anyhow::anyhow!("Missing server_info"))?;
    Ok(ServerInfo {
        url: si.get("url").and_then(|s| s.as_str()).unwrap_or("").into(),
        port: si.get("port").and_then(|p| p.as_str()).and_then(|p| p.parse().ok()).unwrap_or(0),
        timezone: si.get("timezone").and_then(|s| s.as_str()).map(str::to_string),
        time_now: si.get("time_now").and_then(|s| s.as_str()).map(str::to_string),
        server_protocol: si.get("server_protocol").and_then(|s| s.as_str()).map(str::to_string),
    })
}

// ---- Catalog endpoints ----------------------------------------------------

pub async fn list_categories(
    server: &str, username: &str, password: &str, kind: &str,
) -> anyhow::Result<Vec<Category>> {
    // kind = "live" | "vod" | "series" → matching Xtream actions
    let action = match kind {
        "live" => "get_live_categories",
        "vod" => "get_vod_categories",
        "series" => "get_series_categories",
        _ => anyhow::bail!("Unknown category kind: {}", kind),
    };
    let json = call(server, username, password, Some(action)).await?;
    let arr = json.as_array().ok_or_else(|| anyhow::anyhow!("categories: not an array"))?;
    Ok(arr.iter().filter_map(|e| {
        Some(Category {
            id: e.get("category_id")?.as_str()?.to_string(),
            name: e.get("category_name")?.as_str()?.to_string(),
        })
    }).collect())
}

pub async fn list_live(
    server: &str, username: &str, password: &str,
) -> anyhow::Result<Vec<Channel>> {
    let json = call(server, username, password, Some("get_live_streams")).await?;
    let arr = json.as_array().ok_or_else(|| anyhow::anyhow!("live: not an array"))?;
    Ok(arr.iter().filter_map(|e| {
        Some(Channel {
            stream_id: as_i64(e, "stream_id")?,
            name: as_str(e, "name").unwrap_or_default(),
            num: as_i64(e, "num").unwrap_or(0),
            logo: as_str(e, "stream_icon").unwrap_or_default(),
            category_id: as_str(e, "category_id").unwrap_or_default(),
            epg_channel_id: as_str(e, "epg_channel_id").unwrap_or_default(),
            tv_archive: as_bool(e, "tv_archive"),
        })
    }).collect())
}

pub async fn list_movies(
    server: &str, username: &str, password: &str,
) -> anyhow::Result<Vec<Movie>> {
    let json = call(server, username, password, Some("get_vod_streams")).await?;
    let arr = json.as_array().ok_or_else(|| anyhow::anyhow!("movies: not an array"))?;
    Ok(arr.iter().filter_map(|e| {
        Some(Movie {
            stream_id: as_i64(e, "stream_id")?,
            name: as_str(e, "name").unwrap_or_default(),
            poster: as_str(e, "stream_icon").unwrap_or_default(),
            category_id: as_str(e, "category_id").unwrap_or_default(),
            rating: as_f64(e, "rating").unwrap_or(0.0),
            container_extension: as_str(e, "container_extension").unwrap_or_else(|| "mp4".into()),
            added: as_i64(e, "added").unwrap_or(0),
            year: as_str(e, "year").and_then(|s| s.chars().take(4).collect::<String>().parse().ok()),
            tmdb_id: as_i64(e, "tmdb"),
        })
    }).collect())
}

pub async fn list_series(
    server: &str, username: &str, password: &str,
) -> anyhow::Result<Vec<Series>> {
    let json = call(server, username, password, Some("get_series")).await?;
    let arr = json.as_array().ok_or_else(|| anyhow::anyhow!("series: not an array"))?;
    Ok(arr.iter().filter_map(|e| {
        Some(Series {
            series_id: as_i64(e, "series_id")?,
            name: as_str(e, "name").unwrap_or_default(),
            cover: as_str(e, "cover").unwrap_or_default(),
            category_id: as_str(e, "category_id").unwrap_or_default(),
            rating: as_f64(e, "rating").unwrap_or(0.0),
            plot: as_str(e, "plot").unwrap_or_default(),
            year: as_str(e, "year").and_then(|s| s.chars().take(4).collect::<String>().parse().ok()),
            tmdb_id: as_i64(e, "tmdb"),
        })
    }).collect())
}

// ---- Stream URL builders --------------------------------------------------
// Mirror the six-shape fallback chain used by the Android app so playback
// still works on panels with quirky URL layouts.

pub fn live_url(server: &str, u: &str, p: &str, id: i64, prefer_hls: bool) -> Vec<String> {
    let base = server.trim_end_matches('/');
    let hls_live = format!("{base}/live/{u}/{p}/{id}.m3u8");
    let ts_live = format!("{base}/live/{u}/{p}/{id}.ts");
    let ext_live = format!("{base}/live/{u}/{p}/{id}");
    let hls_bare = format!("{base}/{u}/{p}/{id}.m3u8");
    let ts_bare = format!("{base}/{u}/{p}/{id}.ts");
    let ext_bare = format!("{base}/{u}/{p}/{id}");
    if prefer_hls {
        vec![hls_live, ts_live, ext_live, hls_bare, ts_bare, ext_bare]
    } else {
        vec![ts_live, hls_live, ext_live, ts_bare, hls_bare, ext_bare]
    }
}

pub fn movie_url(server: &str, u: &str, p: &str, id: i64, ext: &str) -> Vec<String> {
    let base = server.trim_end_matches('/');
    let ext = if ext.is_empty() { "mp4" } else { ext };
    let prefix = format!("{base}/movie/{u}/{p}/{id}");
    // Widen for panels that lie about container_extension (matches the
    // Android StreamUrlResolver.forMovie behaviour).
    let mut out = Vec::with_capacity(4);
    let ordered = [ext, "mp4", "mkv", "ts", "avi"];
    let mut seen = std::collections::HashSet::new();
    for e in ordered.iter() {
        if seen.insert(*e) {
            out.push(format!("{prefix}.{e}"));
        }
    }
    out
}

pub fn episode_url(server: &str, u: &str, p: &str, id: i64, ext: &str) -> Vec<String> {
    let base = server.trim_end_matches('/');
    let ext = if ext.is_empty() { "mp4" } else { ext };
    let prefix = format!("{base}/series/{u}/{p}/{id}");
    let mut out = Vec::with_capacity(4);
    let ordered = [ext, "mp4", "mkv", "ts", "avi"];
    let mut seen = std::collections::HashSet::new();
    for e in ordered.iter() {
        if seen.insert(*e) {
            out.push(format!("{prefix}.{e}"));
        }
    }
    out
}

// ---- Internals ------------------------------------------------------------

async fn call(
    server: &str, username: &str, password: &str, action: Option<&str>,
) -> anyhow::Result<serde_json::Value> {
    let base = server.trim_end_matches('/');
    let mut url = format!(
        "{base}/player_api.php?username={u}&password={p}",
        u = urlencoding::encode(username),
        p = urlencoding::encode(password),
    );
    if let Some(a) = action {
        url.push_str("&action=");
        url.push_str(a);
    }
    let client = reqwest::Client::builder()
        // Match the Android app's UA — the same Cloudflare/WAF rules that
        // 407-throttle OkHttp will throttle reqwest's default too.
        .user_agent("VLC/3.0.20 LibVLC/3.0.20")
        .timeout(std::time::Duration::from_secs(30))
        .build()?;
    let body = client
        .get(&url)
        .send()
        .await?
        .error_for_status()?
        .text()
        .await?;
    Ok(serde_json::from_str::<serde_json::Value>(&body)?)
}

fn as_str(v: &serde_json::Value, key: &str) -> Option<String> {
    v.get(key).and_then(|x| x.as_str()).map(str::to_string)
}

fn as_i64(v: &serde_json::Value, key: &str) -> Option<i64> {
    v.get(key).and_then(|x| {
        x.as_i64().or_else(|| x.as_str().and_then(|s| s.parse().ok()))
    })
}

fn as_f64(v: &serde_json::Value, key: &str) -> Option<f64> {
    v.get(key).and_then(|x| {
        x.as_f64().or_else(|| x.as_str().and_then(|s| s.parse().ok()))
    })
}

fn as_bool(v: &serde_json::Value, key: &str) -> bool {
    v.get(key).map(|x| {
        x.as_bool().unwrap_or_else(|| {
            x.as_i64().map(|n| n != 0).unwrap_or(false)
        })
    }).unwrap_or(false)
}

/// urlencoding fallback — Cargo doesn't automatically pull it in via reqwest.
mod urlencoding {
    pub fn encode(s: &str) -> String {
        let mut out = String::with_capacity(s.len());
        for c in s.chars() {
            if c.is_ascii_alphanumeric() || matches!(c, '-' | '_' | '.' | '~') {
                out.push(c);
            } else {
                let mut buf = [0u8; 4];
                for &byte in c.encode_utf8(&mut buf).as_bytes() {
                    out.push('%');
                    out.push(hex(byte >> 4));
                    out.push(hex(byte & 0xF));
                }
            }
        }
        out
    }
    fn hex(nibble: u8) -> char {
        match nibble { 0..=9 => (b'0' + nibble) as char, _ => (b'A' + nibble - 10) as char }
    }
}
