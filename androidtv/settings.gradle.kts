pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "EnktelTV"
include(":app")

// Baseline-profile generator, deliberately not part of a normal build.
//
// It is a com.android.test module that drives the app on a real device or
// emulator to record which code paths run at startup. That needs hardware, an
// emulator image and the benchmark plugin, none of which should be in the way
// of building an APK — so it is included only when the generation workflow asks
// for it. Everything about it, including whether its plugin version resolves at
// all, is therefore invisible to `assembleRelease`.
//
// See .github/workflows/baseline-profile.yml.
if (System.getenv("ENKTEL_BASELINE_PROFILE") == "true") {
    include(":baselineprofile")
}
