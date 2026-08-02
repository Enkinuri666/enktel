plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "tv.enktel.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "tv.enktel.app"
        minSdk = 21
        targetSdk = 35
        versionCode = 89
        versionName = "1.38.2"
        vectorDrawables { useSupportLibrary = true }
    }

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
                // minSdk 21 (Android 5.0/5.1, incl. Fire TV Stick 2nd-gen on Fire OS 5) can only
                // verify APK Signature Scheme v1 (JAR signing) - v2 verification requires API 24+.
                // v1 MUST stay enabled or the APK is uninstallable on those devices.
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
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true // used to switch TV vs mobile navigation shells at runtime
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
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
}
