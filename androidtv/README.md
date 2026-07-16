# EnkTel IPTV — Android TV / Fire TV app

Fully branded EnkTel OTT player for Android TV and Amazon Fire TV.

## Features
- **Xtream Codes** (player_api) and **M3U playlist** sources, multi-profile
- **Live TV** player: DPAD/channel-key zapping, number entry, channel side panel with
  now-playing per channel, info bar with now/next + progress, quality badge,
  audio/subtitle track selection, aspect-ratio cycling, real-time stream stats overlay
  (resolution, fps, codecs, bitrate, network estimate, buffer health, dropped frames, decoder)
- **TV Guide**: full-day grid EPG with day navigation, program details, watch live,
  play-from-start (catch-up) and schedule recording straight from the grid
- **EPG**: streaming XMLTV parser (handles gzip + multi-hundred-MB guides), auto-discovers
  `url-tvg` from M3U, Xtream `xmltv.php`
- **Catch-up TV**: Xtream timeshift archive browser per channel
- **DVR**: instant record + scheduled recordings (foreground service + WorkManager),
  local playback of finished recordings
- **VOD**: movies & series with categories, posters, plot/cast/rating details,
  seasons/episodes, resume playback (continue-watching rail), favorites
- **Search** across channels, movies and series
- **Player**: Media3/ExoPlayer with HLS·DASH·SmoothStreaming·RTSP·TS, OkHttp networking,
  tunable buffer profiles (fast zap ↔ max stability), bounded auto-retry on stream errors,
  playback speed, subtitles

## Build
```bash
cd androidtv
gradle assembleDebug   # or open in Android Studio
```
APK output: `app/build/outputs/apk/debug/app-debug.apk`

Requires Android SDK 35. `minSdk 21` covers Fire TV Stick (2nd gen+) and Android TV 5+.
Leanback launcher + banner included; touchscreen not required (Fire TV compliant).

## Sideload on Fire TV
```bash
adb connect <firetv-ip>
adb install app-debug.apk
```
