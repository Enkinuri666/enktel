# EnkTel IPTV for Windows

The desktop "bigger brother" to the EnkTel IPTV mobile & TV apps. Built for power
users and IPTV enthusiasts who want a premium desktop experience they'll be
proud to show off.

## Stack

Chosen for the "lightweight yet feature-packed and super responsive" brief:

- **[Tauri 2](https://v2.tauri.app/)** shell — Rust backend + Chromium WebView2 on
  Windows 11. Total runtime footprint ~30 MB (vs Electron's ~120 MB) so the app
  cold-starts in a couple of seconds.
- **Rust** (`src-tauri/`) — Xtream Codes client, M3U + XMLTV parsers, SQLite
  library, MPV control, all native.
- **React 18 + TypeScript + Vite** (`src/`) — hot-reload, ES modules, strict types.
- **TailwindCSS + shadcn/ui** — Netflix-grade UI without hand-rolling a design
  system.
- **hls.js + shaka-player + video.js** — every stream flavour the mobile/TV app
  supports plus adaptive bitrate, low-latency LL-HLS, DRM hooks.
- **Zustand** — tiny state atom.
- **React Query** — server-state cache for the Xtream / EPG requests.

## Versioning

This app carries the **same version number as the Android apps**, and is bumped
with them. It sat at 1.0.0 through several Android releases, which meant an
installer and an APK that talk to each other over the network gave completely
different answers to "what version are you on?" — no use at all when someone
reports that pairing failed.

Three files have to move together, all set by `npm install --package-lock-only`
and hand-edits: `package.json` (and its lockfile), `src-tauri/Cargo.toml`, and
`src-tauri/tauri.conf.json`. The last is the one that reaches a user — it names
the installer, fills in Add/Remove Programs, and is what the built-in updater
compares — so it is the one that must never be forgotten.

## My devices — the desktop end of "Send to PC"

The one part of this app that is finished and tested rather than targeted, because
it is the half of a shipped Android feature that had no desktop half.

The mobile and TV apps can open a small HTTP server on the house network and show
a PIN (Downloads → **Send to PC**). A browser is enough to fetch a file from it.
This app does three things a browser cannot:

| | browser | EnkTel on the PC |
| --- | --- | --- |
| finding the device | read the IP off the TV, type it | UDP broadcast, pick from a list |
| saving | prompts per file | one folder, `Get all`, unattended |
| interrupted transfer | starts the film again | resumes from the byte it reached |
| the device's queue | — | pause / resume / retry / cancel |

Where it lives:

- `src-tauri/src/link.rs` — discovery, pairing, the HTTP calls, and the resuming
  save. Everything that touches the network or the disk, with its tests.
- `src/lib/link.ts` — the `invoke` wrappers and the formatting helpers.
- `src/pages/PhonePage.tsx` — the screen.

The Android end is `data/share/LanShareApi.kt` (the wire format), `LanShareServer.kt`
(the routes) and `DownloadRemote.kt` (what the remote control is allowed to do).
`LanShareApi.VERSION` and `link.rs`'s `PROTOCOL_VERSION` must agree — the two ends
ship separately, so a mismatched pair says so rather than misbehaving.

**Security, in short.** Pairing needs the PIN shown on the device; ten wrong ones
and it stops answering until sharing is restarted. Files are addressed by opaque
token, never by path. The token from pairing dies with the share and is never
written to disk. Names arriving from the network are sanitised *here* as well as
on the phone before they are joined to a path — `safe_name` and `destination` in
`link.rs`, both tested against traversal, NTFS streams and the DOS device names.
The discovery datagram carries a device name and a port and nothing else.

## Elite features (targeted)

- Multi-monitor + popout floating players.
- Global keyboard shortcuts (VLC-style + Netflix-style).
- Command palette (Ctrl+K).
- 2×2 / 3×3 mosaic mode for sports.
- On-disk recording via Rust muxer (no external ffmpeg needed on install).
- Screenshot + short clip capture with copy-to-clipboard.
- Voice commands via Web Speech API (matches the Android voice grammar).
- Chromecast + DLNA push.
- System-tray minimisation + Windows toast notifications for match reminders.
- Auto-update from the built-in updater.
- Fully skinnable — same six themes as the mobile app.

## Layout

```
pc/
├── package.json        · Node deps (React, Vite, hls.js, shaka)
├── vite.config.ts      · Vite/React config
├── tsconfig.json       · Strict TypeScript
├── tailwind.config.ts  · EnkTel palette wired in
├── public/             · Static assets shipped as-is
├── src/                · React app
│   ├── main.tsx        · Entry
│   ├── App.tsx         · Router + shell
│   ├── theme.ts        · EnkTel colours + Tailwind vars
│   ├── pages/          · Home / LiveTV / Movies / Series / Sports / Guide / Settings
│   │                     plus PhonePage — "My devices" (Send to PC)
│   ├── components/     · Player, ChannelList, EPGRow, PosterCard, MicButton, …
│   ├── lib/            · API client, utils, hooks
│   └── stores/         · Zustand stores
└── src-tauri/          · Rust backend
    ├── src/
    │   ├── main.rs         · Entry, window setup, tauri commands
    │   ├── xtream.rs       · Xtream Codes API client
    │   ├── m3u.rs          · M3U8 parser
    │   ├── xmltv.rs        · XMLTV EPG parser
    │   ├── db.rs           · SQLite library (rusqlite)
    │   ├── recorder.rs     · TS/HLS on-disk recorder
    │   └── voice.rs        · Global voice hotkey handler
    ├── Cargo.toml
    └── tauri.conf.json
```

## Getting started

Requires: Rust 1.77+, Node 20+, Microsoft Edge WebView2 runtime (ships on
Windows 11 by default).

```powershell
cd pc
npm install
npm run tauri dev       # runs in dev mode with hot reload
npm run tauri build     # produces an MSI + NSIS installer under src-tauri/target/release/bundle
```

Signed release builds go through the same keystore signing story as the mobile
app — see `../.github/workflows/build-pc.yml` (WIP).
