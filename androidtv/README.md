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

Requires Android SDK 36. `minSdk 23` covers Fire TV Stick (3rd gen / 4K and newer) and Android TV 6+.
Leanback launcher + banner included; touchscreen not required (Fire TV compliant).

## Sideload on Fire TV
```bash
adb connect <firetv-ip>
adb install app-debug.apk
```

## The default line

A fresh install opens on the onboarding form. `tv.enktel.app.data.repo.DefaultLine`
prefills the panel address so that form is two fields rather than three, and a
build given credentials signs in during startup and lands straight on the
channel list.

```
./gradlew :app:assembleTvDebug -PenkDefaultUser=... -PenkDefaultPass=...
./gradlew :app:assembleMobileDebug -PenkDefaultUser=... -PenkDefaultPass=...
```

`ENK_DEFAULT_USER` / `ENK_DEFAULT_PASS` in the environment work too, and
`-PenkDefaultServer` points at a different panel.

**Only the server has a default.** An APK is a zip file: anything compiled into
it is readable by anyone who downloads it. A credential baked into a public
build is a published credential, and an Xtream line capped at a few
simultaneous connections does not merely leak — it stops working for its owner.
Builds made with the properties above are for whoever owns the line, not for
distribution.

Seeding runs once, before the start destination is chosen, and re-reads the
database rather than waiting on the profiles flow: the start destination is
read once by the NavHost, so a profile that arrives a frame later would leave
the viewer on the onboarding form with an account already configured. A login
that fails falls through to onboarding rather than to an empty home screen.
