package tv.enktel.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which code runs while the app starts, so ART can compile it ahead of
 * time instead of interpreting it on every cold start.
 *
 * ## What a baseline profile buys
 *
 * Without one, every method in the app is interpreted the first time it runs
 * and only gets compiled once JIT decides it is hot — which, for code that
 * executes a handful of times during startup, may be never. A baseline profile
 * is a list of methods and classes shipped inside the APK; ART compiles them at
 * install time. Typical result on low-end hardware is a materially faster cold
 * start and less jank through the first interactions, which is precisely the
 * hardware this app is aimed at.
 *
 * The AndroidX libraries already ship their own profiles, so Compose's
 * internals are covered. What is not covered is any of this app's own code, and
 * that is what this generates.
 *
 * ## Why the delivery mechanism matters here more than usual
 *
 * On Play-installed apps the profile also arrives as a cloud profile. This app
 * is sideloaded onto Fire TV, so that route does not exist and the embedded
 * profile is the only one there will ever be. `ProfileInstaller` — already on
 * the classpath via `androidx.compose.ui` — is what writes it into ART on first
 * run.
 *
 * ## Why this only covers startup
 *
 * Deliberately. A generator can only exercise what the app will actually do on
 * a bare emulator, and without a provider configured this app cannot load a
 * catalogue, open a channel or play anything. Trying to script past the setup
 * screen would produce a profile full of whatever the error paths do, which is
 * worse than a smaller honest one.
 *
 * Startup is also where the benefit is concentrated: it is the one path that
 * runs on every launch, runs once, and therefore never gets warm enough for JIT
 * to help. Catalogue scrolling — the other candidate — is repetitive enough
 * that JIT does eventually cover it.
 *
 * If this is ever extended to browsing, it needs a test provider whose
 * credentials can live in CI, not a scripted walk through a real one.
 */
@RunWith(AndroidJUnit4::class)
class StartupProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(
        // The television flavour keeps the original package; mobile appends
        // `.mobile`. Fire TV is the target this matters for.
        packageName = "tv.enktel.app",
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        // Let the first frame settle rather than stopping at the activity
        // becoming visible — splash, theme resolution and the first Compose
        // composition all land after that point, and they are the expensive
        // part of a cold start.
        device.waitForIdle()
    }
}
