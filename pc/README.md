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
