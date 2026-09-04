//! Talking to the phone or TV box that is sharing its downloads.
//!
//! The Android app runs a small HTTP server on the house network and shows a
//! PIN. A browser is enough to fetch a file from it — that was the original
//! feature and still works. This module is what the browser cannot be:
//!
//! - it **finds** the device instead of making someone read an IP address off
//!   a television and type it in;
//! - it **saves where it was told**, once, instead of prompting per file;
//! - it **resumes**, from the byte it stopped at, because a six-gigabyte film
//!   over house Wi-Fi does not always arrive first time;
//! - it **drives the queue** — pause, resume, retry, cancel — so the phone can
//!   stay in a pocket.
//!
//! ## What it trusts
//!
//! Very little. The device on the other end is whatever answered a broadcast,
//! and the name it gives for a file is a string from the network. Nothing here
//! joins that string to a path without [`safe_name`] first: the whole point of
//! a "save to this folder" client is that it writes into that folder and
//! nowhere else.

use serde::{Deserialize, Serialize};
use std::io::Write;
use std::net::{IpAddr, Ipv4Addr, SocketAddr, UdpSocket};
use std::path::{Component, Path, PathBuf};
use std::time::{Duration, Instant};

/// Must match `LanShareApi.DISCOVERY_PORT` in the Android app.
const DISCOVERY_PORT: u16 = 8788;
/// Must match `LanShareApi.PROBE`.
const PROBE: &[u8] = b"ENKTEL-DISCOVER-1";
/// Must match `LanShareApi.VERSION`. A device announcing anything else speaks
/// a protocol this build has not seen, and guessing at it is worse than saying so.
const PROTOCOL_VERSION: u32 = 1;

// ── discovery ───────────────────────────────────────────────────────────

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct Device {
    pub name: String,
    pub ip: String,
    pub port: u16,
}

#[derive(Deserialize)]
struct Announce {
    enktel: u32,
    device: String,
    port: u16,
}

/// Read one announcement datagram. `None` for anything that is not one.
///
/// Separated from the socket so the parsing — the part that reads bytes from
/// the network — can be tested without one.
pub fn parse_announce(payload: &[u8], from: IpAddr) -> Option<Device> {
    let a: Announce = serde_json::from_slice(payload).ok()?;
    if a.enktel != PROTOCOL_VERSION || a.port == 0 {
        return None;
    }
    let name = a.device.trim();
    Some(Device {
        // A device may name itself anything, including nothing and including a
        // screenful. It is only ever drawn as a label, but a label is still no
        // place for four kilobytes.
        name: if name.is_empty() {
            "EnkTel device".to_string()
        } else {
            name.chars().take(60).collect()
        },
        ip: from.to_string(),
        port: a.port,
    })
}

/// Broadcast "who's there" and collect what answers within `timeout`.
///
/// A single blocking sweep rather than a background listener: the user presses
/// Refresh, waits a second, and sees a list. Something running continuously
/// would be spraying broadcasts at the household network forever to keep a
/// screen up to date that is usually not open.
pub fn discover(timeout: Duration) -> Vec<Device> {
    let socket = match UdpSocket::bind(SocketAddr::from((Ipv4Addr::UNSPECIFIED, 0))) {
        Ok(s) => s,
        Err(_) => return Vec::new(),
    };
    if socket.set_broadcast(true).is_err() {
        return Vec::new();
    }
    let _ = socket.set_read_timeout(Some(Duration::from_millis(250)));

    let target = SocketAddr::from((Ipv4Addr::BROADCAST, DISCOVERY_PORT));
    // Sent more than once because UDP is allowed to simply lose it, and a
    // household access point under load does. Three costs nothing and turns
    // "the app can't see my phone" into a rarity.
    for _ in 0..3 {
        let _ = socket.send_to(PROBE, target);
    }

    let deadline = Instant::now() + timeout;
    let mut found: Vec<Device> = Vec::new();
    let mut buf = [0u8; 512];
    while Instant::now() < deadline {
        let (n, from) = match socket.recv_from(&mut buf) {
            Ok(v) => v,
            // A timeout is the expected way this loop idles; anything else and
            // the socket is not going to start working within the deadline.
            Err(e) if e.kind() == std::io::ErrorKind::WouldBlock => continue,
            Err(e) if e.kind() == std::io::ErrorKind::TimedOut => continue,
            Err(_) => break,
        };
        if let Some(dev) = parse_announce(&buf[..n], from.ip()) {
            // Three probes mean up to three replies from the same device.
            if !found.iter().any(|d| d.ip == dev.ip && d.port == dev.port) {
                found.push(dev);
            }
        }
    }
    found.sort_by(|a, b| a.name.cmp(&b.name));
    found
}

// ── the HTTP API ────────────────────────────────────────────────────────

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Paired {
    pub token: String,
    pub device: String,
    pub app: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RemoteFile {
    pub token: String,
    pub name: String,
    pub size: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Job {
    pub id: String,
    pub title: String,
    pub subtitle: String,
    pub status: String,
    #[serde(rename = "progressPct")]
    pub progress_pct: i32,
    #[serde(rename = "sizeBytes")]
    pub size_bytes: u64,
    #[serde(rename = "downloadedBytes")]
    pub downloaded_bytes: u64,
    #[serde(rename = "speedBps")]
    pub speed_bps: u64,
    pub error: String,
}

#[derive(Deserialize)]
struct PairedWire {
    version: u32,
    token: String,
    device: String,
    app: String,
}

#[derive(Deserialize)]
struct FilesWire {
    files: Vec<RemoteFile>,
}

#[derive(Deserialize)]
struct JobsWire {
    downloads: Vec<Job>,
}

fn client() -> reqwest::Client {
    reqwest::Client::builder()
        // The device is on the same network and either answers quickly or is
        // not there. A long timeout here only makes a wrong address take
        // longer to be wrong.
        .connect_timeout(Duration::from_secs(4))
        .build()
        .unwrap_or_default()
}

/// Exchange the PIN shown on the phone for a token every later call carries.
pub async fn pair(base: &str, pin: &str) -> anyhow::Result<Paired> {
    let res = client()
        .post(format!("{}/api/pair", base.trim_end_matches('/')))
        .header("Content-Type", "application/json")
        .body(serde_json::json!({ "pin": pin.trim() }).to_string())
        .timeout(Duration::from_secs(10))
        .send()
        .await?;

    if res.status() == reqwest::StatusCode::UNAUTHORIZED {
        anyhow::bail!("That PIN was not right. Check the number on the phone.");
    }
    if res.status() == reqwest::StatusCode::TOO_MANY_REQUESTS {
        anyhow::bail!("The phone stopped accepting PINs after too many wrong ones. Stop and restart sending on it.");
    }
    if !res.status().is_success() {
        anyhow::bail!("The device answered {}.", res.status());
    }

    let wire: PairedWire = res.json().await?;
    if wire.version != PROTOCOL_VERSION {
        anyhow::bail!(
            "That device speaks version {} of the EnkTel link and this app speaks {}. Update whichever is older.",
            wire.version, PROTOCOL_VERSION
        );
    }
    Ok(Paired { token: wire.token, device: wire.device, app: wire.app })
}

async fn get_json(base: &str, token: &str, path: &str) -> anyhow::Result<reqwest::Response> {
    let res = client()
        .get(format!("{}{}", base.trim_end_matches('/'), path))
        .bearer_auth(token)
        .timeout(Duration::from_secs(15))
        .send()
        .await?;
    if res.status() == reqwest::StatusCode::UNAUTHORIZED {
        anyhow::bail!("This device is no longer paired — sharing was stopped and restarted. Pair again.");
    }
    if !res.status().is_success() {
        anyhow::bail!("The device answered {}.", res.status());
    }
    Ok(res)
}

pub async fn files(base: &str, token: &str) -> anyhow::Result<Vec<RemoteFile>> {
    let wire: FilesWire = get_json(base, token, "/api/files").await?.json().await?;
    Ok(wire.files)
}

pub async fn downloads(base: &str, token: &str) -> anyhow::Result<Vec<Job>> {
    let wire: JobsWire = get_json(base, token, "/api/downloads").await?.json().await?;
    Ok(wire.downloads)
}

/// Pause, resume, retry or cancel one download on the device.
pub async fn act(base: &str, token: &str, id: &str, action: &str) -> anyhow::Result<bool> {
    let res = client()
        .post(format!("{}/api/downloads/act", base.trim_end_matches('/')))
        .bearer_auth(token)
        .header("Content-Type", "application/json")
        .body(serde_json::json!({ "id": id, "action": action }).to_string())
        .timeout(Duration::from_secs(15))
        .send()
        .await?;
    if res.status() == reqwest::StatusCode::UNAUTHORIZED {
        anyhow::bail!("This device is no longer paired. Pair again.");
    }
    if res.status() == reqwest::StatusCode::NOT_FOUND {
        // The row is gone — finished, or removed on the phone. Not an error,
        // just a list that needs refreshing.
        return Ok(false);
    }
    if !res.status().is_success() {
        anyhow::bail!("The device answered {}.", res.status());
    }
    Ok(true)
}

// ── saving a file ───────────────────────────────────────────────────────

/// Reduce a name from the network to something that can only land in the
/// folder it was meant for.
///
/// The Android side already sanitises, and that is exactly why this exists:
/// the checks that matter are the ones made by the side that owns the disk. A
/// client that writes wherever the server says is a client that hands any
/// device on the home network the ability to drop a file into Startup.
pub fn safe_name(raw: &str) -> String {
    let mut out = String::with_capacity(raw.len().min(120));
    for c in raw.chars() {
        match c {
            // Path separators, the Windows-reserved set, and control
            // characters. Anything that could steer a path, or that a shell or
            // an NTFS stream would read as syntax.
            '/' | '\\' | ':' | '*' | '?' | '"' | '<' | '>' | '|' => continue,
            c if (c as u32) < 0x20 => continue,
            c => out.push(c),
        }
    }
    // Leading dots and spaces: a leading dot hides the file on every Unix and
    // "..." is what is left of a traversal after the separators go.
    let out = out.trim().trim_start_matches('.').trim().to_string();
    let out: String = out.chars().take(120).collect();
    let out = out.trim_end_matches([' ', '.']).to_string();
    if out.is_empty() {
        return "download".to_string();
    }
    // The DOS device names. Windows still refuses to create a file called
    // `CON`, extension or not, and the failure it gives is unreadable.
    const RESERVED: [&str; 22] = [
        "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7",
        "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
    ];
    let stem = out.split('.').next().unwrap_or("").to_ascii_uppercase();
    if RESERVED.contains(&stem.as_str()) {
        return format!("_{out}");
    }
    out
}

/// Where a file will land, or an error if it would land outside `dir`.
///
/// Belt and braces over [`safe_name`]: that strips the characters, this proves
/// the result. Two independent checks, because the cost of them disagreeing is
/// a file written somewhere nobody asked for.
pub fn destination(dir: &Path, raw_name: &str) -> anyhow::Result<PathBuf> {
    let name = safe_name(raw_name);
    let path = dir.join(&name);
    let escapes = Path::new(&name)
        .components()
        .any(|c| !matches!(c, Component::Normal(_)));
    if escapes || path.parent() != Some(dir) {
        anyhow::bail!("The device sent a file name this app will not write: {raw_name}");
    }
    Ok(path)
}

/// Progress for one save, as the UI draws it.
#[derive(Debug, Clone, Serialize)]
pub struct SaveProgress {
    pub token: String,
    pub name: String,
    pub received: u64,
    pub total: u64,
    pub done: bool,
    pub error: Option<String>,
}

/// Fetch one shared file into `dir`, resuming a part-file if one is there.
///
/// `on_progress` is called as bytes land so the caller can push them at the
/// UI; it is a callback rather than an event emitted from in here so this
/// function stays testable and knows nothing about windows.
pub async fn save_file(
    base: &str,
    token: &str,
    file_token: &str,
    raw_name: &str,
    dir: &Path,
    mut on_progress: impl FnMut(u64, u64),
) -> anyhow::Result<PathBuf> {
    std::fs::create_dir_all(dir)?;
    let final_path = destination(dir, raw_name)?;
    // Written beside the target and renamed at the end. A half-copied film
    // sitting under its real name is one a media player will happily open and
    // show as broken.
    let part_path = final_path.with_extension(format!(
        "{}part",
        final_path
            .extension()
            .map(|e| format!("{}.", e.to_string_lossy()))
            .unwrap_or_default()
    ));

    let have = std::fs::metadata(&part_path).map(|m| m.len()).unwrap_or(0);

    let mut req = client()
        .get(format!("{}/f/{}", base.trim_end_matches('/'), file_token))
        .bearer_auth(token)
        .timeout(Duration::from_secs(60 * 60 * 6));
    if have > 0 {
        req = req.header("Range", format!("bytes={}-", have));
    }
    let res = req.send().await?;

    match res.status() {
        reqwest::StatusCode::UNAUTHORIZED => {
            anyhow::bail!("This device is no longer paired. Pair again.")
        }
        reqwest::StatusCode::GONE => {
            anyhow::bail!("That file has been deleted from the device.")
        }
        reqwest::StatusCode::NOT_FOUND => {
            anyhow::bail!("That file is no longer being shared.")
        }
        reqwest::StatusCode::RANGE_NOT_SATISFIABLE => {
            // The part-file is longer than the file on the phone, so it is a
            // leftover from something else. Starting over is the only honest
            // move: appending would produce a file that is the wrong length
            // and looks complete.
            let _ = std::fs::remove_file(&part_path);
            anyhow::bail!("The unfinished copy did not match the file on the device; it has been discarded. Try again.");
        }
        s if !s.is_success() => anyhow::bail!("The device answered {s}."),
        _ => {}
    }

    // A server that ignored the Range header answers 200 with the whole file.
    // Appending that to what we already have would corrupt it silently, so the
    // part-file goes and the write starts at zero.
    let resuming = have > 0 && res.status() == reqwest::StatusCode::PARTIAL_CONTENT;
    if have > 0 && !resuming {
        let _ = std::fs::remove_file(&part_path);
    }
    let already = if resuming { have } else { 0 };

    let total = res
        .content_length()
        .map(|len| len + already)
        .unwrap_or(0);

    let mut file = std::fs::OpenOptions::new()
        .create(true)
        .write(true)
        .append(resuming)
        .truncate(!resuming)
        .open(&part_path)?;

    let mut received = already;
    let mut res = res;
    while let Some(chunk) = res.chunk().await? {
        file.write_all(&chunk)?;
        received += chunk.len() as u64;
        on_progress(received, total);
    }
    file.flush()?;
    drop(file);

    if total > 0 && received != total {
        // Left in place rather than deleted: the next attempt resumes from
        // here instead of starting a multi-gigabyte transfer again.
        anyhow::bail!(
            "The connection dropped after {received} of {total} bytes. Try again to carry on from there."
        );
    }

    std::fs::rename(&part_path, &final_path)?;
    Ok(final_path)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn announcements_from_a_matching_version_become_devices() {
        let ip: IpAddr = "192.168.1.42".parse().unwrap();
        let d = parse_announce(br#"{"enktel":1,"device":"Pixel 8","port":8787}"#, ip).unwrap();
        assert_eq!(d.name, "Pixel 8");
        assert_eq!(d.ip, "192.168.1.42");
        assert_eq!(d.port, 8787);
    }

    #[test]
    fn anything_else_on_the_port_is_ignored() {
        let ip: IpAddr = "10.0.0.5".parse().unwrap();
        // Another protocol, a future version, a nonsense port, and junk. The
        // discovery port is a broadcast port: everything on the network can
        // and does put things on it.
        assert!(parse_announce(b"hello", ip).is_none());
        assert!(parse_announce(br#"{"enktel":99,"device":"x","port":1}"#, ip).is_none());
        assert!(parse_announce(br#"{"enktel":1,"device":"x","port":0}"#, ip).is_none());
        assert!(parse_announce(br#"{"device":"x","port":8787}"#, ip).is_none());
    }

    #[test]
    fn an_unnamed_or_endless_device_name_is_made_drawable() {
        let ip: IpAddr = "10.0.0.5".parse().unwrap();
        let blank = parse_announce(br#"{"enktel":1,"device":"   ","port":8787}"#, ip).unwrap();
        assert_eq!(blank.name, "EnkTel device");

        let long = format!(r#"{{"enktel":1,"device":"{}","port":8787}}"#, "a".repeat(4000));
        let d = parse_announce(long.as_bytes(), ip).unwrap();
        assert_eq!(d.name.chars().count(), 60);
    }

    #[test]
    fn a_name_cannot_steer_the_path_it_is_saved_under() {
        assert_eq!(safe_name("../../etc/passwd"), "etcpasswd");
        assert_eq!(safe_name("..\\..\\Windows\\System32\\x.dll"), "WindowsSystem32x.dll");
        assert_eq!(safe_name("/absolute"), "absolute");
        assert_eq!(safe_name("C:\\Users\\me\\evil.exe"), "CUsersmeevil.exe");
        assert_eq!(safe_name(".hidden"), "hidden");
        assert_eq!(safe_name("   "), "download");
        assert_eq!(safe_name(""), "download");
        assert_eq!(safe_name("file\u{0000}name.mkv"), "filename.mkv");
        // An NTFS alternate data stream is written with a colon, so the colon
        // going is what stops `film.mkv:hidden` being two things.
        assert_eq!(safe_name("film.mkv:hidden"), "film.mkvhidden");
    }

    #[test]
    fn windows_device_names_are_defused_rather_than_dropped() {
        assert_eq!(safe_name("CON"), "_CON");
        assert_eq!(safe_name("nul.txt"), "_nul.txt");
        assert_eq!(safe_name("COM9.mkv"), "_COM9.mkv");
        // Not reserved — the check is on the stem, not on any dot-separated part.
        assert_eq!(safe_name("my.con.mkv"), "my.con.mkv");
        assert_eq!(safe_name("console.log"), "console.log");
    }

    #[test]
    fn a_trailing_dot_or_space_is_trimmed_because_windows_would_do_it_anyway() {
        // Windows silently strips these when creating the file, so the name we
        // report and the name on disk would otherwise differ.
        assert_eq!(safe_name("report."), "report");
        assert_eq!(safe_name("report "), "report");
    }

    #[test]
    fn a_destination_always_lands_directly_in_the_chosen_folder() {
        let dir = Path::new("/tmp/enktel-test");
        for hostile in [
            "../../etc/passwd",
            "..\\..\\evil.exe",
            "/etc/shadow",
            "sub/dir/file.mkv",
            "...",
        ] {
            let got = destination(dir, hostile).expect("should sanitise, not fail");
            assert_eq!(got.parent(), Some(dir), "{hostile} escaped to {got:?}");
        }
    }
}
