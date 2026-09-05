# How the welcome guide's screenshots are made

`scripts/build-welcome-guide.py` does not invent pictures. It reads PNGs from a
directory and leaves a gap, visibly, where one is missing. This is how that
directory is filled.

Every image is the **real UI** — the shipped components, stylesheets and
strings. What is substituted is the *data behind* them, because there is no
live subscription in CI and a guide full of empty grids teaches nobody
anything. Nothing is redrawn, mocked up or traced.

```
docs/screenshots/
├── android/   from Robolectric   (mobile + tv flavours)
├── pc/        from Playwright    (the Tauri app's web layer)
└── web/       from Playwright    (the watch.enktel.tv app)
```

## Android — `android/`

No emulator is needed, and none is available in CI (no KVM). Robolectric 4.15's
**native graphics mode** rasterises Compose for real, so `captureToImage()`
returns actual pixels rather than a blank bitmap.

```bash
cd androidtv
D=$(pwd)/../docs/screenshots/android

./gradlew :app:testMobileDebugUnitTest -Penktel.shots=1 "-Penktel.shots.dir=$D" \
    --tests '*ScreenshotCaptureTest'
./gradlew :app:testTvDebugUnitTest     -Penktel.shots=1 "-Penktel.shots.dir=$D" \
    --tests '*ScreenshotCaptureTest'
```

`ScreenshotCaptureTest` builds a real `AppGraph`, seeds its Room database with a
profile and a handful of downloads, renders a screen inside `EnktelTheme`, and
writes a PNG. Files are named `<screen>-<flavour>.png`, so the two runs fill one
directory instead of overwriting each other.

Screen size comes from Robolectric qualifiers, because the layouts branch on it:
`w411dp-h891dp-xhdpi` for a phone, `w960dp-h540dp-land-television-xhdpi-notouch`
for a television. Capturing everything at Robolectric's 320x480 default
photographs a phone layout and labels it a TV. Qualifier order is the canonical
Android one — get it wrong and Robolectric refuses to parse it.

The test is **excluded from the ordinary test task**; it writes files instead of
asserting anything. `-Penktel.shots=1` opts in.

## PC and web — `pc/`, `web/`

Both are Playwright against a local dev server. Neither talks to the internet,
so **do not pass a proxy**: this session's agent relay only accepts HTTPS
CONNECT tunnels and answers every `http://localhost` page with an error, which
is how eight identical screenshots of a proxy error once got as far as the PDF.

```bash
cd pc      && npm run dev     # localhost:1420
cd ../../enki/web && npm run dev   # localhost:5173

node capture-pc.mjs
node capture-web.mjs
```

The two harnesses substitute data at different layers, and deliberately so:

- **PC** — the app reaches the Rust side over Tauri's IPC, which does not exist
  in a plain browser. `window.__TAURI_INTERNALS__` is replaced with a stand-in
  answering the same `invoke` calls. Nothing in the app is patched.
- **Web** — the app speaks real Xtream over HTTP, so the *panel* is mocked at
  the wire with `page.route('**/player_api.php*')`. The app is untouched; only
  the server on the far end is ours.

Poster and logo URLs are answered with a flat brand-tinted SVG. Studio artwork
is not ours to ship in a document.

Wait for the splash to leave the DOM rather than guessing a timeout — a cold
dev server adds its own compile pause, and a fixed wait produced five identical
photographs of the splash screen.

## Building the guide

```bash
python3 scripts/build-welcome-guide.py --shots docs/screenshots
```

It prints the path, the size, the version it read from the build file, and the
name of every screenshot it could not find.
