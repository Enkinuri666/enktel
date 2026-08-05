plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "tv.enktel.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "tv.enktel.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 107
        versionName = "1.47.0"
        vectorDrawables { useSupportLibrary = true }

        // v1.34.0 — Eagle 4K trial signup + upgrade CTA URLs. The trial endpoint
        // is expected to accept a POST and return JSON with the shape
        //   { server_url, username, password, expires_at }
        // Overridable at build time via -PenkTrialUrl / -PenkUpgradeUrl so a
        // deployer can point at a different panel without editing this file.
        val trialUrl = (project.findProperty("enkTrialUrl") as? String)
            ?: System.getenv("ENK_TRIAL_URL")
            ?: "https://watch.enktel.tv/api/trial"
        val upgradeUrl = (project.findProperty("enkUpgradeUrl") as? String)
            ?: System.getenv("ENK_UPGRADE_URL")
            ?: "https://watch.enktel.tv/upgrade"
        buildConfigField("String", "EAGLE_TRIAL_URL", "\"$trialUrl\"")
        buildConfigField("String", "EAGLE_UPGRADE_URL", "\"$upgradeUrl\"")
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

        // lifecycle 2.10.0 ships lint checks compiled against a newer Kotlin
        // analysis API than the one bundled with AGP 8.7.3's lint, so
        // NonNullableMutableLiveDataDetector dies with
        //   IncompatibleClassChangeError: Found class KaCallableMemberCall,
        //   but interface was expected
        // and takes the whole lint run down with it. Nothing to do with this
        // codebase — and the check is inert here regardless, since the app
        // uses no LiveData at all (StateFlow + Compose state throughout;
        // `grep -r LiveData app/src` returns nothing). Revisit when AGP moves
        // far enough forward to carry a matching lint.
        disable += "NullSafeMutableLiveData"
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
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.palette.ktx)
    implementation(libs.work.runtime.ktx)
    // Storage Access Framework helpers — used by the download manager so
    // the user can pick a target folder (USB, external SD, NAS via
    // DocumentsUI) and have it treated as a plain folder for writes.
    implementation(libs.documentfile)

    testImplementation(libs.junit)
}
