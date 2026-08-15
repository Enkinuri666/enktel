plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

/**
 * Baseline-profile generator.
 *
 * See `src/main/java/tv/enktel/baselineprofile/StartupProfileGenerator.kt` for
 * what it records and why, and `.github/workflows/baseline-profile.yml` for how
 * it is run. This module is only in the build when ENKTEL_BASELINE_PROFILE is
 * set — see the guard in settings.gradle.kts.
 */
android {
    namespace = "tv.enktel.baselineprofile"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        // Macrobenchmark needs API 28+ to read a profile back off the device.
        // The app itself still goes down to 23; this module never ships.
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

    targetProjectPath = ":app"

    // Must mirror the app's dimension exactly. A test module that does not
    // declare the same flavors cannot be matched to a variant of its target,
    // and the failure reads as an unrelated dependency-resolution error.
    flavorDimensions += "form"
    productFlavors {
        create("tv") { dimension = "form" }
        create("mobile") { dimension = "form" }
    }
}

baselineProfile {
    // Generated on whatever device or emulator is attached, by the workflow.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
