plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    // On the classpath, but not applied unless the generation workflow asks.
    //
    // `apply false` still resolves the artifact, so an ordinary build proves
    // the version is real without the plugin ever running. That matters
    // because applying it adds build types and expects a producer module, and
    // none of that should exist while simply building an APK. It is applied,
    // and its producer wired up, in the block at the bottom of this file.
    alias(libs.plugins.baselineprofile) apply false
}

android {
    namespace = "tv.enktel.app"
    // core-ktx 1.19 and lifecycle 2.11 both refuse to be consumed below API
    // 37, so the compile target moves with them. This only widens the set of
    // APIs available at compile time — what the app opts into at runtime is
    // targetSdk, which is deliberately a step behind.
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "tv.enktel.app"
        minSdk = 23
        targetSdk = 36
        versionCode = 168
        versionName = "1.60.48"
        vectorDrawables { useSupportLibrary = true }

        // These must be the host *this* Next.js app deploys to, which is
        // enktel.tv. watch.enktel.tv is a different property — the browser
        // player — and this repo's own copy says as much ("apps at enktel.tv,
        // or the new browser player at watch.enktel.tv"). Pointing the app
        // there gave 404 HTML to an M3U parser and to an XMLTV parser, and is
        // why the trial button could never have worked: /api/trial is in this
        // repo, and this repo is not what answers on watch.enktel.tv.
        //
        // v1.34.0 — Eagle 4K trial signup + upgrade CTA URLs. The trial endpoint
        // is expected to accept a POST and return JSON with the shape
        //   { server_url, username, password, expires_at }
        // Overridable at build time via -PenkTrialUrl / -PenkUpgradeUrl so a
        // deployer can point at a different panel without editing this file.
        val trialUrl = (project.findProperty("enkTrialUrl") as? String)
            ?: System.getenv("ENK_TRIAL_URL")
            ?: "https://watch.enktel.tv/api/trial"
        // The hosted PayPal checkout. `?plan=annual` preselects the
        // 12-month plan, which is what the in-app upgrade CTA sells.
        //
        // Was ".../upgrade", which is not a route the site has — the field was
        // never read by any Kotlin code, so nothing ever followed it to the
        // 404 it pointed at.
        val upgradeUrl = (project.findProperty("enkUpgradeUrl") as? String)
            ?: System.getenv("ENK_UPGRADE_URL")
            ?: "https://watch.enktel.tv/checkout?plan=annual"
        buildConfigField("String", "EAGLE_TRIAL_URL", "\"$trialUrl\"")
        buildConfigField("String", "EAGLE_UPGRADE_URL", "\"$upgradeUrl\"")

        // The line a fresh install starts on. See
        // tv.enktel.app.data.repo.DefaultLine for why only the server has a
        // default: an APK is a zip, so a credential compiled into a public
        // build is a published credential, and an Xtream line capped at a few
        // connections does not survive being shared. A private build can bake
        // them in with -PenkDefaultUser / -PenkDefaultPass (or
        // ENK_DEFAULT_USER / ENK_DEFAULT_PASS); neither touches a committed
        // file.
        // This has to be the host the reseller actually issues lines on —
        // `STREAM_SERVER_URL` in src/lib/reseller.ts, which is also what
        // /api/trial hands back as `server_url` and what verifyStreamCredentials
        // authenticates against. It was line.enktel.online, which is a
        // different machine: a subscriber typing correct credentials into the
        // prefilled form was pointing them at a host that does not hold their
        // line, and got the origin's HTTP 512 rather than a login failure.
        val defaultServer = (project.findProperty("enkDefaultServer") as? String)
            ?: System.getenv("ENK_DEFAULT_SERVER")
            ?: "http://api.elg-26.com"
        val defaultUser = (project.findProperty("enkDefaultUser") as? String)
            ?: System.getenv("ENK_DEFAULT_USER")
            ?: ""
        val defaultPass = (project.findProperty("enkDefaultPass") as? String)
            ?: System.getenv("ENK_DEFAULT_PASS")
            ?: ""
        buildConfigField("String", "DEFAULT_SERVER", "\"$defaultServer\"")
        buildConfigField("String", "DEFAULT_USERNAME", "\"$defaultUser\"")
        buildConfigField("String", "DEFAULT_PASSWORD", "\"$defaultPass\"")

        // The playlist a build with no baked-in line falls back to, so a fresh
        // install has something to watch before it has an account. These are
        // the free-to-air streams the scraper collects and verifies — complete,
        // public URLs that carry no credentials and need no login, unlike a
        // panel catalog, where the credentials *are* the stream URL.
        //
        // Points at the generated lineup rather than the full scrape: 2,923
        // channels already grouped by country and genre with 97.5% logo and
        // 98.7% EPG coverage is a storefront, where 11,175 ungrouped ones are
        // a dump.
        //
        // Served from our own origin rather than raw.githubusercontent.com. A
        // device reported `Failed to connect to raw.githubusercontent.com` on
        // the guide, which follows a redirect to GitHub Pages — and the
        // playlist came from GitHub too, so both pieces of runtime data
        // depended on GitHub being reachable from the viewer's network. On a
        // mobile network that is not a safe assumption, and no amount of
        // retrying in the app fixes it.
        val freePlaylist = (project.findProperty("enkFreePlaylistUrl") as? String)
            ?: System.getenv("ENK_FREE_PLAYLIST_URL")
            ?: "https://enktel.tv/playlists/enktel-lineup.m3u"
        // The playlist's own x-tvg-url was inherited from whichever source
        // header the scraper happened to read — a free-tier worker on
        // onrender.com that nobody here chose and that answers nothing. This
        // is the guide the project already knows: scripts/lib/sources.mjs
        // declares it for the Brisbane feed, and it is what a tv_grab points
        // at in practice.
        //
        // /api/guide fetches the upstream server-side and streams it back, so
        // the viewer only ever talks to our origin and one upstream fetch
        // serves every device. ENKTEL_GUIDE_UPSTREAM repoints it without a
        // redeploy.
        //
        // It still covers the AU channels and the mjh-* ids rather than the
        // whole lineup, which is 84% American. That is the right trade only
        // because the lineup is sorted local-first — a guide for the channels
        // the viewer lands on beats a guide for none of them. A lineup
        // spanning four countries wants a guide built for it, which means
        // self-hosting iptv-org/epg; it publishes no hosted guide of its own.
        val freePlaylistEpg = (project.findProperty("enkFreePlaylistEpg") as? String)
            ?: System.getenv("ENK_FREE_PLAYLIST_EPG")
            ?: "https://enktel.tv/api/guide"
        buildConfigField("String", "FREE_PLAYLIST_URL", "\"$freePlaylist\"")
        buildConfigField("String", "FREE_PLAYLIST_EPG", "\"$freePlaylistEpg\"")
    }

    // Room writes the resolved schema for every version under
    // app/schemas. Those files are committed, so an entity change that has no
    // matching Migration shows up as an unreviewed schema diff instead of
    // silently tripping fallbackToDestructiveMigration() on a user's device
    // and taking their profiles, favourites and watch progress with it.
    ksp { arg("room.schemaLocation", "$projectDir/schemas") }

    // Two product flavors so users can install the mobile and TV builds side-by-side.
    // Shared code lives in src/main; each flavor supplies its own manifest + branding.
    flavorDimensions += "form"
    productFlavors {
        create("tv") {
            dimension = "form"
            // No applicationIdSuffix: TV keeps the original package for OTA continuity.
            resValue("string", "app_name_flavor", "EnkTel IPTV")
        }
        create("mobile") {
            dimension = "form"
            applicationIdSuffix = ".mobile"
            versionNameSuffix = "-mobile"
            resValue("string", "app_name_flavor", "EnkTel IPTV Mobile")
        }
    }

    // Release signing comes from the environment so no credentials live in the repo:
    //   ENKTEL_KEYSTORE (path), ENKTEL_KEYSTORE_PASS, ENKTEL_KEY_PASS, optional ENKTEL_KEY_ALIAS (default "enktel")
    // Release builds FAIL FAST if these are not set, rather than silently producing an
    // unsigned/non-store-ready APK. Debug/other build types are unaffected.
    val envKeystore = System.getenv("ENKTEL_KEYSTORE")
    val envKeystorePass = System.getenv("ENKTEL_KEYSTORE_PASS")
    val envKeyPass = System.getenv("ENKTEL_KEY_PASS")
    val envKeyAlias = System.getenv("ENKTEL_KEY_ALIAS") ?: "enktel"
    val canSignRelease = !envKeystore.isNullOrBlank() &&
        !envKeystorePass.isNullOrBlank() &&
        !envKeyPass.isNullOrBlank()

    if (canSignRelease) {
        signingConfigs {
            create("release") {
                storeFile = file(envKeystore!!)
                storePassword = envKeystorePass
                keyAlias = envKeyAlias
                keyPassword = envKeyPass
                // minSdk 23 (Android 6.0) can only verify APK Signature Scheme v1
                // (JAR signing) — v2 verification requires API 24+. v1 MUST stay
                // enabled or the APK is uninstallable on Marshmallow.
                enableV1Signing = true
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
            // The FFmpeg decoder AAR carries all four ABIs, and x86/x86_64 are
            // 3.2 MB of the 6.2 MB it adds. No Fire TV, Android TV or phone
            // ships an x86 CPU — those slices exist for emulators, which run
            // debug builds. Dropping them from release halves the cost of the
            // decoder for every real device.
            ndk {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a")
            }
            signingConfig = if (canSignRelease) {
                signingConfigs.getByName("release")
            } else if (gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = false) }) {
                throw GradleException(
                    "Release build requires ENKTEL_KEYSTORE, ENKTEL_KEYSTORE_PASS and ENKTEL_KEY_PASS " +
                        "environment variables to be set (see GitHub Secrets). Refusing to produce an " +
                        "unsigned release artifact."
                )
            } else {
                signingConfig
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // kotlinOptions is deprecated in the Kotlin 2.2 Gradle plugin; the
    // compilerOptions DSL is the replacement and takes a typed JvmTarget.
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    buildFeatures {
        compose = true
        buildConfig = true // used to switch TV vs mobile navigation shells at runtime
        // AGP 9 turns resValue off by default. Both flavors declare
        // app_name_flavor through it, so it has to be opted back in.
        resValues = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }

    lint {
        // The EnkTel mark is a full-bleed emblem by design — it is meant to be
        // read as a badge, not as a glyph floating on a plate. On API 26+ the
        // adaptive icon in src/tv/res does the right thing (brand background,
        // art inset into the safe zone); the legacy PNGs this fires on are the
        // pre-26 fallback, where a full-bleed icon is the convention anyway.
        disable += "IconLauncherShape"

        // NullSafeMutableLiveData used to be disabled here: lifecycle 2.10's
        // lint checks were compiled against a newer Kotlin analysis API than
        // AGP's bundled lint, and the detector took the whole run down with
        // an IncompatibleClassChangeError. AGP 9.3 and lifecycle 2.11 agree,
        // and lint passes with the check enabled, so the suppression is gone.
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons)
    implementation(libs.tv.material)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.smoothstreaming)
    implementation(libs.media3.exoplayer.rtsp)
    implementation(libs.media3.ui)
    // System media integration: transport controls on the Fire TV overlay and
    // the lock screen, "what's playing" for Alexa and Assistant, and hardware
    // media keys. The app published none of this — see PlaybackSession.
    implementation(libs.media3.session)
    implementation(libs.media3.ui.compose)
    implementation(libs.media3.datasource.okhttp)
    // No media3-datasource-rtmp, deliberately.
    //
    // Its librtmp-jni.so ships from Google's AAR with 4 KB ELF segment
    // alignment (p_align 0x1000). Android 15+ devices with 16 KB memory pages
    // refuse to load such a library, and because it is a prebuilt binary in a
    // published artifact there is no local fix — the alignment is baked in.
    // It was the only 64-bit library in the APK that was not 16 KB-clean, so
    // dropping it makes the arm64 build load correctly on 16 KB hardware.
    //
    // The cost is rtmp:// playback, which this deployment does not use. If it
    // is ever needed again, take the dependency back and check
    // `readelf -lW lib/arm64-v8a/librtmp-jni.so` for a 0x4000 LOAD alignment
    // first. PlayerEngine names the scheme in its error path so a stray
    // rtmp:// URL says what is wrong rather than failing as a generic
    // container error.
    // Software AC-3 / E-AC-3 / DTS / TrueHD audio, for the Fire TV hardware
    // that decodes none of them and plays those streams silently. Built by
    // build-ffmpeg-extension.yml from androidx/media 1.11.0 — Google ships
    // this decoder as source, not to Maven (#121), so it is a checked-in
    // binary rather than a coordinate. Its version must track the media3
    // version above; a mismatch fails at runtime, not at build time.
    implementation(files("libs/media3-decoder-ffmpeg-1.11.0.aar"))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    // Coil 3 ships no network fetcher by default — without this every
    // remote poster and channel logo silently resolves to nothing.
    implementation(libs.coil.network.okhttp)
    implementation(libs.palette.ktx)
    implementation(libs.work.runtime.ktx)
    // Storage Access Framework helpers — used by the download manager so
    // the user can pick a target folder (USB, external SD, NAS via
    // DocumentsUI) and have it treated as a plain folder for writes.
    implementation(libs.documentfile)

    testImplementation(libs.junit)
}

// Room's KSP tasks must not run at the same time as each other.
//
// The schemaLocation set above is one directory shared by every variant, and
// each product flavour has its own KSP task. Build tv and mobile in a single
// Gradle invocation — which both the lint step and the APK step do — and the
// two tasks read and write the same `<version>.json` concurrently. The failure
// surfaces as a corrupt schema rather than as anything resembling a race:
//
//   e: [ksp] JsonDecodingException: Unexpected JSON token at offset 41586:
//   Expected colon ':', but had 'EOF' instead at path: $.database.entities[16]
//
// which is one task parsing a file the other is half way through writing.
//
// `all` rather than a snapshot of `tasks.names` inside `afterEvaluate`. The
// first attempt at this did the latter and silently did nothing: KSP registers
// its tasks from AGP's variant callbacks, which run after `afterEvaluate`, so
// the filter matched an empty set and no ordering was applied. The build that
// followed went green anyway — the race is intermittent, and one green build
// is not evidence. `all` fires for tasks registered later too, which is the
// property this needs.
//
// mustRunAfter only orders tasks that are already scheduled — it never causes
// one to run — so this costs nothing when a build touches a single variant.
run {
    val ordered = mutableListOf<Task>()
    tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("Kotlin") }.all {
        ordered.forEach { earlier -> this.mustRunAfter(earlier) }
        ordered += this
    }
}

// Baseline profile generation, off unless explicitly requested.
//
// Everything here is inert in a normal build: the plugin is on the classpath
// but unapplied, :baselineprofile is not in settings.gradle.kts, and this block
// does not run. It is switched on only by .github/workflows/baseline-profile.yml.
//
// `add("baselineProfile", …)` rather than the generated `baselineProfile(…)`
// accessor: that accessor only exists when the plugin is applied in the plugins
// block, and applying it there is exactly what this avoids.
if (System.getenv("ENKTEL_BASELINE_PROFILE") == "true") {
    apply(plugin = "androidx.baselineprofile")
    dependencies { add("baselineProfile", project(":baselineprofile")) }
}
