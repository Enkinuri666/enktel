# EnkTel Android TV — Build & Signing Audit

**Repo:** `Enkinuri666/enktel` (branch `main`)
**App module:** `androidtv/` · **applicationId:** `tv.enktel.app`
**Reviewed:** 2026-07-19 · Static configuration review (no CI run logs exist)

---

## 0. Important corrections to the request

Three assumptions in the task do not match the actual repository:

| Requested premise | Actual state in repo |
|---|---|
| "Flutter project" | **Not Flutter.** `androidtv/` is a native **Kotlin + Jetpack Compose** Android TV app (Gradle Kotlin DSL, `com.android.application` plugin). There is no `pubspec.yaml`, no Flutter SDK dependency. |
| "existing GitHub Actions `build-apk.yml`" | **No workflow file exists.** There is no `.github/` directory at all — `gh api .../contents/.github/workflows` returns 404, `gh run list` returns nothing. |
| "ensure release APK includes `xtream_code_client` dependencies" | There is **no `xtream_code_client` package** (it would be a Dart/Flutter pub package). Xtream Codes support is implemented as a **native Kotlin class** `tv.enktel.app.data.xtream.XtreamClient` (OkHttp + kotlinx.serialization), compiled directly into the app. No external dependency to "include". |

This report therefore covers (a) the build config as it actually exists, (b) the keystore/GitHub Secrets checklist adapted to the real env-var signing scheme, (c) the dependency/Xtream audit, and (d) the steps — including a recommended `build-apk.yml` to create from scratch — to reach a signed production release for Google Play / Fire TV.

---

## 1. Current build status

**Status: Debug-only, no CI, not signed for release.**

- Only `assembleDebug` is documented in `androidtv/README.md`; output `app/build/outputs/apk/debug/app-debug.apk`.
- No CI/CD pipeline of any kind (no `.github/workflows/`).
- **Gradle wrapper is not committed** — `gradlew`, `gradlew.bat`, and `gradle/wrapper/` are all absent. CI cannot run `./gradlew` without first generating or installing Gradle.
- Release `signingConfig` is wired to environment variables (`ENKTEL_KEYSTORE`, `ENKTEL_KEYSTORE_PASS`, `ENKTEL_KEY_ALIAS`) in `app/build.gradle.kts`, but **nothing sets those variables today**, and the build silently falls back to debug signing (see §4).
- No keystore, `key.properties`, or `.jks` file is committed — good, no secret leak detected in the tree.
- `versionCode = 3`, `versionName = "1.0.2"` (hardcoded; not auto-incremented).
- `minSdk 21`, `targetSdk 35`, `compileSdk 35`, JDK 17, AGP 8.7.3.

---

## 2. Keystore / GitHub Secrets security checklist

The project intentionally avoids a committed `key.properties` and instead reads signing inputs from environment variables. The checklist below secures that flow end-to-end.

### 2.1 Generate the release keystore locally (once)
```bash
keytool -genkeypair -v \
  -keystore enktel-release.jks \
  -storetype PKCS12 \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -alias enktel \
  -dname "CN=EnkTel, O=EnkTel, C=AU" \
  -storepass <STOREPASS> -keypass <KEYPASS>
```
- Use **separate** store password and key password (the current `build.gradle.kts` reuses `ENKTEL_KEYSTORE_PASS` for both — acceptable, but separating is stronger).
- Back up the `.jks` in a password manager / 1Password vault. **If this keystore is lost, you can never push an update to the same Play Store listing.**
- Never commit the `.jks`. Add to `.gitignore`: `*.jks`, `*.keystore`, `key.properties`.

### 2.2 Store as GitHub repository secrets
In repo **Settings → Secrets and variables → Actions**, create:

| Secret name | Value | Notes |
|---|---|---|
| `ENKTEL_KEYSTORE_BASE64` | base64 of the `.jks` | `base64 -i enktel-release.jks` → store the string. The workflow decodes it to a file at runtime (see §6). Named `_BASE64` to avoid confusion with the `ENKTEL_KEYSTORE` env var, which holds a **file path**. |
| `ENKTEL_KEYSTORE_PASS` | store password | — |
| `ENKTEL_KEY_PASS` | key password | **New** — current Gradle file has no env var for this; add `ENKTEL_KEY_PASS` and update `build.gradle.kts` (see §4 fix). |
| `ENKTEL_KEY_ALIAS` | `enktel` | — |

> The current Gradle reads `ENKTEL_KEYSTORE` as a **file path**, not base64. Either (a) decode the secret to a temp file in the workflow and point `ENKTEL_KEYSTORE` at that path, or (b) switch the Gradle file to accept `ENKTEL_KEYSTORE_BASE64` and decode in Gradle. Option (a) keeps secrets out of Gradle logic and is recommended.

### 2.3 Secret-handling rules for the workflow
- Decode the keystore into `$RUNNER_TEMP` (ephemeral, scrubbed post-job), never into the workspace.
- Use `actions/checkout` with default token; do not print secrets in steps.
- Mask any echoed filenames; never `echo` the passwords.
- Do **not** upload the keystore or `key.properties` as a build artifact.
- Restrict which workflows can read secrets via environment + `environment` protection (e.g. a `release` environment requiring manual approval).
- Rotate the keystore only by creating a new one + uploading a new key to Play Console "App signing keys → Request key upgrade" (Play uses Play App Signing; the upload key can be reset).

---

## 3. Workflow audit (of what should be built)

Because no `build-apk.yml` exists, this is the spec the recommended file (§6) must satisfy:

- **Runner / OS:** `ubuntu-latest`.
- **JDK:** Temurin 17 (matches `JavaVersion.VERSION_17`).
- **Android SDK:** build-tools + platform 35 (compileSdk/targetSdk).
- **Gradle:** must supply the wrapper (currently missing — see fix §5.1) or use `gradle/actions/setup-gradle`.
- **Cache:** `gradle/actions/setup-gradle` with `cache-read-only: false` for dependency caching.
- **Build command:** `./gradlew :app:assembleRelease` (APK) and `:app:bundleRelease` (AAB for Play).
- **Signing:** decode keystore secret → set `ENKTEL_KEYSTORE` path + password envs → build → verify APK is v2/v3-signed with `apksigner verify --verbose`.
- **Quality gates:** `./gradlew lintRelease` and `:app:assembleDebug` + unit tests (`./gradlew test`) before release.
- **Artifact upload:** `app/build/outputs/apk/release/app-release.apk` and `app/build/outputs/bundle/release/app-release.aab` via `actions/upload-artifact`.
- **Trigger:** on `push` to `main` (debug APK) + on `workflow_dispatch`/tags for signed release.

---

## 4. Signing config audit & critical fix

Current `androidtv/app/build.gradle.kts` (lines 24–43):

```kotlin
val envKeystore = System.getenv("ENKTEL_KEYSTORE")
val envKeystorePass = System.getenv("ENKTEL_KEYSTORE_PASS")
if (!envKeystore.isNullOrBlank() && !envKeystorePass.isNullOrBlank()) {
    signingConfigs { create("release") { storeFile = file(envKeystore); storePassword = envKeystorePass
        keyAlias = System.getenv("ENKTEL_KEY_ALIAS") ?: "enktel"
        keyPassword = envKeystorePass } }        // ← key password == store password (no separate secret)
}
buildTypes {
    release {
        isMinifyEnabled = true; isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        signingConfig = signingConfigs.findByName("release") ?: signingConfig   // ← silent fallback
    }
}
```

**Issues found:**

1. **No fail-fast on missing signing secrets (high severity).** `signingConfigs.findByName("release") ?: signingConfig` does **not** fall back to the debug key — the bare `signingConfig` reference resolves to the release build type's signing config, which defaults to `null`. The consequence is that a release build with absent env vars produces an **unsigned** (or non-store-ready) APK rather than failing the build. That is dangerous because CI can report success on an artifact that Play Console will reject, and it is not obviously broken until upload. **Fix:** fail the build loudly via `GradleException` when the env vars are missing (see replacement below).
2. **No separate key password secret.** `keyPassword = envKeystorePass`. Add `ENKTEL_KEY_PASS`.
3. **No `v1Enabled` / `apksigner` scheme controls** — relying on AGP defaults is fine, but for Fire TV (older devices) ensure v2 signing at minimum (default OK).

**Recommended replacement:**

```kotlin
val envKeystore = System.getenv("ENKTEL_KEYSTORE")
val envKeystorePass = System.getenv("ENKTEL_KEYSTORE_PASS")
val envKeyPass = System.getenv("ENKTEL_KEY_PASS")
val envKeyAlias = System.getenv("ENKTEL_KEY_ALIAS") ?: "enktel"
val canSign = !envKeystore.isNullOrBlank() &&
    !envKeystorePass.isNullOrBlank() &&
    !envKeyPass.isNullOrBlank()

if (canSign) {
    signingConfigs {
        create("release") {
            storeFile = file(envKeystore)
            storePassword = envKeystorePass
            keyAlias = envKeyAlias
            keyPassword = envKeyPass
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
        }
    }
}

buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        // Fail loudly instead of silently debug-signing:
        signingConfig = if (canSign) signingConfigs.getByName("release")
                        else throw GradleException(
                            "Release build requires ENKTEL_KEYSTORE/ENKTEL_KEYSTORE_PASS/ENKTEL_KEY_PASS")
    }
}
```

---

## 5. Dependency & Fire TV / Xtream audit

### 5.1 Build-tooling gaps
- **Gradle wrapper missing.** Commit it: from `androidtv/` run `gradle wrapper --gradle-version 8.9` (AGP 8.7.3 needs Gradle ≥ 8.9) and commit `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.{jar,properties}`. Without this, CI cannot reproducibly build.
- `.gitignore` covers `*.apk` and build dirs but **not** `*.jks`/`*.keystore`/`key.properties` — add them.

### 5.2 Xtream Codes support (the "xtream_code_client" question)
There is no external dependency to wire. The Xtream Codes `player_api.php` integration is a **first-party Kotlin class** (`XtreamClient.kt`) built on:
- `okhttp` 4.12.0 (HTTP),
- `kotlinx-serialization-json` 1.7.3 (JSON parsing via `LenientJson`).

It is compiled into `:app` unconditionally, so any release APK **does include** Xtream support by construction — there is no flavor/variant gate that could strip it. Required runtime wiring is present in `AndroidManifest.xml`:
- `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE(_DATA_SYNC)` permissions — present.
- `android:usesCleartextTraffic="true"` — needed for many IPTV `http://` streams and Fire TV side-loads.

### 5.3 Fire TV readiness
- `uses-feature android.software.leanback required="false"` ✓ (installable on both touch + TV).
- `LEANBACK_LAUNCHER` intent + `android:banner` ✓ (shows in Fire TV / Android TV launcher row).
- `minSdk 21` ✓ (Fire TV Stick 2nd-gen+ runs Android 5.0/API 21).
- Touchscreen not required ✓.
- **Fire TV store submission:** Amazon accepts a signed release APK (AAB not required). Current config is fine for Amazon Appstore once signed with a real key.

### 5.4 Dependency version matrix & conflict risk

| Component | Version | Status |
|---|---|---|
| AGP | 8.7.3 | OK with Gradle 8.9+ and JDK 17. |
| Kotlin | 2.0.21 | Matches Compose compiler plugin (`kotlin.plugin.compose` 2.0.21) ✓. |
| KSP | 2.0.21-1.0.25 | Aligned with Kotlin 2.0.21 ✓. |
| Compose BOM | 2024.10.01 | Compatible with Kotlin 2.0.x ✓. |
| androidx.tv:tv-material | 1.0.0 | Stable, OK for TV UI. |
| Media3 | 1.5.0 (exoplayer hls/dash/smoothstreaming/rtsp/datasource-okhttp/ui) | OK; pulls OkHttp consistently. |
| Room | 2.6.1 (+KSP compiler) | OK. |
| OkHttp | 4.12.0 | Matches Media3 datasource-okhttp transitive ✓. |
| Coil | 2.7.0 (`io.coil-kt:coil-compose`) | Legacy Coil 2 groupId; works, no conflict (Coil 3 is `io.coil-kt.coil3`). |
| kotlinx.coroutines | 1.9.0 | OK. |
| WorkManager | 2.10.0 | OK. |

**No hard dependency version conflicts detected.** Conflicting-transitive cases are all single-source.

**Residual risks (medium):**

1. **ProGuard/R8 keep rules are incomplete.** `proguard-rules.pro` only keeps `kotlinx.serialization` serializers. With `isMinifyEnabled=true` + `isShrinkResources=true`, release builds can strip/obfuscate classes from **OkHttp, Okio, Coil, Media3, and Room-generated code**. Media3 ships consumer ProGuard rules and Room/KSP code is generally safe, so this is a *release-only breakage risk to validate by testing*, not a known defect — Coil image loading is the most likely place to surface a release-only issue. Add keep rules (see §5.5) and **test a release APK on a real device**, not just debug.
2. **`usesCleartextTraffic="true"` globally** — acceptable for an IPTV client but a Play Store policy review flag for some categories. Prefer a `network_security_config.xml` that allows cleartext only to the IPTV server hosts and enforces HTTPS elsewhere.
3. **`org.gradle.configuration-cache=false`** — safe but slower; AGP 8.7 supports it. Not a conflict.
4. **No `versionCode` automation** — every Play/Amazon upload needs a strictly higher `versionCode`. Currently manual (3).

### 5.5 Suggested ProGuard additions (`proguard-rules.pro`)
```
# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
# Coil
-dontwarn coil.**
-keep class coil.** { *; }
# Media3 (it ships consumer rules; add only if release crashes appear)
-keep class androidx.media3.** { *; }
# Room (generated impls are kept by the compiler; keep entities to be safe)
-keep class tv.enktel.app.data.db.** { *; }
```

---

## 6. Recommended `build-apk.yml` (to create at `.github/workflows/build-apk.yml`)

> Adapted to the real native-Kotlin project (not Flutter). Replace `cd androidtv` paths to match your layout — the Gradle project root is `androidtv/`.

```yaml
name: Build & Sign APK/AAB

on:
  push:
    branches: [main]
    paths:
      - 'androidtv/**'
      - '.github/workflows/build-apk.yml'
  workflow_dispatch:
    inputs:
      release:
        description: 'Build signed release'
        type: boolean
        default: false

jobs:
  build:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: androidtv
    env:
      ENKTEL_KEYSTORE: ${{ github.workspace }}/enktel-release.jks
      ENKTEL_KEYSTORE_PASS: ${{ secrets.ENKTEL_KEYSTORE_PASS }}
      ENKTEL_KEY_PASS: ${{ secrets.ENKTEL_KEY_PASS }}
      ENKTEL_KEY_ALIAS: ${{ secrets.ENKTEL_KEY_ALIAS }}

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Lint
        run: ./gradlew :app:lintRelease --no-daemon

      - name: Unit tests
        run: ./gradlew :app:testDebugUnitTest --no-daemon

      - name: Build debug APK (default)
        if: ${{ github.event.inputs.release != 'true' }}
        run: ./gradlew :app:assembleDebug --no-daemon

      - name: Set up Android SDK build-tools
        if: ${{ github.event.inputs.release == 'true' }}
        uses: android-actions/setup-android@v3
        with:
          packages: 'build-tools;35.0.0 platforms;android-35'

      - name: Decode release keystore
        if: ${{ github.event.inputs.release == 'true' }}
        run: |
          echo "${{ secrets.ENKTEL_KEYSTORE_BASE64 }}" | base64 -d > "$ENKTEL_KEYSTORE"
          test -s "$ENKTEL_KEYSTORE"

      - name: Build signed release APK + AAB
        if: ${{ github.event.inputs.release == 'true' }}
        run: ./gradlew :app:assembleRelease :app:bundleRelease --no-daemon

      - name: Verify APK signature
        if: ${{ github.event.inputs.release == 'true' }}
        run: |
          APKSIGNER="$ANDROID_HOME/build-tools/35.0.0/apksigner"
          [ -x "$APKSIGNER" ] || APKSIGNER=$(find "$ANDROID_HOME/build-tools" -name apksigner | sort -V | tail -1)
          "$APKSIGNER" verify --verbose app/build/outputs/apk/release/app-release.apk

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: enktel-apk
          path: |
            androidtv/app/build/outputs/apk/debug/app-debug.apk
            androidtv/app/build/outputs/apk/release/app-release.apk
          if-no-files-found: warn

      - name: Upload AAB (Play Store)
        if: ${{ github.event.inputs.release == 'true' }}
        uses: actions/upload-artifact@v4
        with:
          name: enktel-aab
          path: androidtv/app/build/outputs/bundle/release/app-release.aab
          if-no-files-found: warn
```

---

## 7. Transition: test scaffold → production-ready signed release

**A. Build infra (do first)**
1. Commit the Gradle wrapper (`gradle wrapper --gradle-version 8.9` in `androidtv/`).
2. Add `*.jks`, `*.keystore`, `key.properties`, `androidtv/.gradle/` to `.gitignore`.
3. Create `.github/workflows/build-apk.yml` (§6).

**B. Signing**
4. Generate `enktel-release.jks` locally (§2.1). Back it up.
5. Add GitHub secrets `ENKTEL_KEYSTORE` (base64), `ENKTEL_KEYSTORE_PASS`, `ENKTEL_KEY_PASS`, `ENKTEL_KEY_ALIAS`.
6. Patch `app/build.gradle.kts` signing block (§4 fix) — separate key password + fail-on-missing-secrets.
7. Run `Build & Sign APK/AAB` with `release=true`; confirm `apksigner` reports v2/v3 signed.

**C. Release-hardening**
8. Expand `proguard-rules.pro` (§5.5); smoke-test a **release** APK on a Fire TV device (not debug).
9. Replace global `usesCleartextTraffic="true"` with a `network_security_config.xml` scoped to IPTV hosts.
10. **Backup of credentials:** `android:allowBackup="true"` (manifest) will let device backup capture locally-stored Xtream profile credentials. Either set `allowBackup=false`, or mark credential fields with `android:fullBackupContent` / `dataExtractionRules` exclusion rules.
11. **Foreground-service / content-rights compliance:** `RecordingService` declares `FOREGROUND_SERVICE_DATA_SYNC`. Google Play now requires a specific foreground-service type and justification; ensure the manifest type matches the work (data sync is defensible for DVR). Streaming IPTV apps also face Play content-policy review — have licensing/rights documentation ready if asked.
12. Add Play AppSigning readiness: Google Play **requires an AAB** (`:app:bundleRelease`) for new apps — the workflow already produces it.
13. Bump `versionCode`/`versionName` per release (or auto-increment from CI).

**D. Store submission**
14. **Google Play:** upload the AAB via Play Console → Production → Create release. Enroll in Play App Signing (Google re-signs from your upload key). Complete data-safety form, content rating, target audience.
15. **Amazon Appstore (Fire TV):** upload the signed **APK** (`app-release.apk`); set device targeting to Fire TV family; provide 1280×720 banner (already present `tv_banner.png`), screenshots, and a privacy policy URL.

**E. Verification before declaring "done"**
- `apksigner verify --verbose app-release.apk` → "Verified using v2 scheme (APK Signature Scheme v2): true".
- `aapt dump badging app-release.apk` shows `applicationId='tv.enktel.app'`, `versionCode=3`, `launchable-activity` with LEANBACK_LAUNCHER.
- Install signed APK on a Fire TV Stick and confirm Xtream login, live channel playback, and EPG load (exercises `XtreamClient` + Media3 in a minified build).

---

## 8. Summary

- **Build status:** Debug-only, no CI, no signed release. Not yet production-ready.
- **Signing:** env-var scheme is the right design and no secrets leak, but the silent debug fallback must be fixed and a real keystore + GitHub Secrets must be wired in.
- **Dependencies:** No version conflicts; Kotlin/KSP/Compose/AGP are mutually aligned. Main residual risk is incomplete ProGuard rules under R8 minification, which only surfaces in release builds.
- **Xtream:** Implemented natively in `XtreamClient.kt` (not an external `xtream_code_client` package); always compiled into the APK — no special "include" step needed.
- **Path to production:** commit Gradle wrapper → add workflow → add secrets → fix signing block → harden ProGuard/cleartext → build AAB (Play) + signed APK (Fire TV) → submit.
