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

/// Log in to an Xtream Codes panel. Uses `player_api.php` with the standard
/// user_info + server_info bundle.
pub async fn login(server: &str, username: &str, password: &str) -> anyhow::Result<ServerInfo> {
    let base = server.trim_end_matches('/');
    let url = format!(
        "{base}/player_api.php?username={username}&password={password}",
        username = urlencoding::encode(username),
        password = urlencoding::encode(password),
    );
    let resp = reqwest::Client::builder()
        .user_agent("EnktelPC/1.0")
        .timeout(std::time::Duration::from_secs(15))
        .build()?
        .get(&url)
        .send()
        .await?
        .error_for_status()?
        .text()
        .await?;
    let v: serde_json::Value = serde_json::from_str(&resp)?;
    let auth = v.get("user_info")
        .and_then(|u| u.get("auth"))
        .and_then(|a| a.as_i64())
        .ok_or_else(|| anyhow::anyhow!("Panel returned no auth field"))?;
    if auth != 1 {
        anyhow::bail!("Panel refused the credentials");
    }
    let si = v.get("server_info").ok_or_else(|| anyhow::anyhow!("Missing server_info"))?;
    Ok(ServerInfo {
        url: si.get("url").and_then(|s| s.as_str()).unwrap_or("").into(),
        port: si.get("port").and_then(|p| p.as_str()).and_then(|p| p.parse().ok()).unwrap_or(0),
        timezone: si.get("timezone").and_then(|s| s.as_str()).map(str::to_string),
        time_now: si.get("time_now").and_then(|s| s.as_str()).map(str::to_string),
        server_protocol: si.get("server_protocol").and_then(|s| s.as_str()).map(str::to_string),
    })
}

/// urlencoding fallback — Cargo doesn't automatically pull it in via reqwest.
mod urlencoding {
    pub fn encode(s: &str) -> String {
        s.chars().flat_map(|c| {
            if c.is_ascii_alphanumeric() || matches!(c, '-' | '_' | '.' | '~') {
                vec![c]
            } else {
                let mut b = [0u8; 4];
                let bytes = c.encode_utf8(&mut b).bytes();
                let mut out: Vec<char> = Vec::new();
                for byte in bytes {
                    let hi = byte >> 4;
                    let lo = byte & 0xF;
                    out.push('%');
                    out.push(hex(hi));
                    out.push(hex(lo));
                }
                out
            }
        }).collect()
    }
    fn hex(nibble: u8) -> char {
        match nibble { 0..=9 => (b'0' + nibble) as char, _ => (b'A' + nibble - 10) as char }
    }
}
