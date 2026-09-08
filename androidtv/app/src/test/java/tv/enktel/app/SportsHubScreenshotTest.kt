package tv.enktel.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.HistoryToggleOff
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Scoreboard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.db.EpgProgram
import tv.enktel.app.data.repo.HighlightClip
import tv.enktel.app.data.repo.LiveScore
import tv.enktel.app.data.repo.SportsEvent
import tv.enktel.app.ui.sports.FinishedEventCard
import tv.enktel.app.ui.sports.FixtureChip
import tv.enktel.app.ui.sports.HighlightCard
import tv.enktel.app.ui.sports.LiveEventCard
import tv.enktel.app.ui.sports.LiveScoreChip
import tv.enktel.app.ui.sports.SectionHeader
import tv.enktel.app.ui.sports.UpcomingEventCard
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelBg
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelTheme
import java.io.File

/**
 * Photographs the Sports Hub's cards.
 *
 * The screen itself wants a profile, an EPG scan and three network feeds
 * before it draws anything, none of which stands up under Robolectric — so
 * this composes the cards directly against fixed fixtures. That is enough to
 * see the things that actually went wrong here and went unnoticed for want of
 * anyone looking: a channel logo stretched to a square, a white play marker
 * on a white still, a countdown that never counted.
 *
 * Fixed timestamps, not `System.currentTimeMillis()`: a card that renders
 * "37m in" today and "38m in" tomorrow makes every capture a diff.
 *
 * Like ScreenshotCaptureTest this writes files rather than asserting, so
 * `build.gradle.kts` excludes it unless `-Penktel.shots=1`:
 *
 *     ./gradlew :app:testMobileDebugUnitTest -Penktel.shots=1 \
 *         --tests '*SportsHubScreenshotTest'
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SportsHubScreenshotTest {

    @get:Rule val rule = createComposeRule()

    private companion object {
        const val PHONE = "w411dp-h891dp-xhdpi"
        const val TV = "w960dp-h540dp-land-television-xhdpi-notouch"

        /** 2026-09-08 19:45 UTC, so every capture reads the same. */
        const val NOW = 1_789_242_300_000L
    }

    private val outDir: File by lazy {
        File(System.getProperty("enktel.shots.dir") ?: "build/shots").apply { mkdirs() }
    }

    private fun capture(name: String) {
        val img = rule.onRoot().captureToImage()
        val f = File(outDir, "$name-${BuildConfig.FLAVOR}.png")
        f.outputStream().use {
            img.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        println("SHOT $name ${img.width}x${img.height} -> ${f.absolutePath}")
    }

    // ── fixtures ──────────────────────────────────────────────────────

    private fun channel(name: String, archive: Boolean = false) = Channel(
        key = "1:${name.hashCode()}", profileId = 1, streamId = 1, name = name,
        hasArchive = archive,
    )

    private fun event(
        title: String, sport: String, phase: String,
        startMs: Long, endMs: Long, channelName: String, archive: Boolean = false,
    ) = SportsEvent(
        program = EpgProgram(
            profileId = 1, epgId = "e", startMs = startMs, endMs = endMs, title = title,
        ),
        channel = channel(channelName, archive),
        sport = sport,
        phase = phase,
    )

    private fun themed(content: @Composable () -> Unit) {
        rule.setContent {
            EnktelTheme {
                Box(Modifier.fillMaxWidth().background(EnktelBg)) { content() }
            }
        }
    }

    /** Every card in the hub, in the order the screen stacks them. */
    @Composable
    private fun Gallery(pad: Dp) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeader(Icons.Rounded.Scoreboard, "LIVE SCORES", EnktelOk, pad, count = 3)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = pad),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LiveScoreChip(
                    LiveScore(
                        eventId = "1", home = "Arsenal", away = "Chelsea",
                        homeScore = "2", awayScore = "1", minute = "67'",
                        league = "Premier League", sport = "Soccer", status = "1H",
                    ),
                    onTap = {}, onStats = {},
                )
            }

            SectionHeader(
                Icons.Rounded.Podcasts, "ON TODAY", EnktelBlue, pad,
                count = 12, subtitle = "Published schedule",
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = pad),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FixtureChip(
                    fixture = LiveScore(
                        eventId = "2", home = "Melbourne Storm", away = "Penrith Panthers",
                        homeScore = "", awayScore = "", minute = "20:05",
                        league = "NRL Telstra Premiership", sport = "Rugby", status = "NS",
                    ),
                    broadcasters = listOf("Fox League", "Nine"),
                    tuneTo = channel("AU | Fox League HD"),
                    onTap = {},
                )
                FixtureChip(
                    fixture = LiveScore(
                        eventId = "3", home = "Lakers", away = "Celtics",
                        homeScore = "", awayScore = "", minute = "11:30",
                        league = "NBA", sport = "Basketball", status = "NS",
                    ),
                    broadcasters = listOf("ESPN"),
                    tuneTo = null,
                    onTap = {},
                )
            }

            SectionHeader(Icons.Rounded.Movie, "HIGHLIGHTS", EnktelOk, pad, subtitle = "Latest packages")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = pad),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HighlightCard(
                    HighlightClip(
                        title = "Manchester City 3-2 Liverpool | Extended highlights",
                        league = "Premier League", sport = "Football",
                        dateMs = NOW, videoUrl = "", thumb = "",
                    ),
                    onPlay = {},
                )
            }

            SectionHeader(Icons.Rounded.FiberManualRecord, "LIVE NOW", EnktelLive, pad, count = 4)
            LiveEventCard(
                event(
                    "Australia v India — 3rd Test, Day 2", "Cricket", "LIVE",
                    NOW - 37 * 60_000L, NOW + 143 * 60_000L, "AU | Fox Cricket HD",
                ),
                score = null, padHoriz = pad, now = NOW, onTap = {}, onStats = {},
            )

            SectionHeader(Icons.Rounded.CalendarMonth, "UPCOMING", EnktelBlue, pad, count = 9)
            UpcomingEventCard(
                event(
                    "Bathurst 1000 — Qualifying", "Motor Racing", "UPCOMING",
                    NOW + 134 * 60_000L, NOW + 254 * 60_000L, "AU | Fox Sports 501",
                ),
                padHoriz = pad, now = NOW, onSchedule = {}, onRemind = {}, onOpen = {},
            )

            SectionHeader(
                Icons.Rounded.HistoryToggleOff, "CATCH-UP", EnktelOk, pad,
                count = 6, subtitle = "Finished in the last 6 hours",
            )
            FinishedEventCard(
                event(
                    "Collingwood v Carlton — AFL Semi Final", "Football", "FINISHED",
                    NOW - 300 * 60_000L, NOW - 150 * 60_000L, "AU | Fox Footy HD",
                    archive = true,
                ),
                padHoriz = pad, onReplay = {},
            )
            FinishedEventCard(
                event(
                    "Wallabies v All Blacks — Bledisloe Cup", "Rugby", "FINISHED",
                    NOW - 420 * 60_000L, NOW - 300 * 60_000L, "AU | Channel 9 HD",
                ),
                padHoriz = pad, onReplay = {},
            )
            Spacer(Modifier.height(10.dp))
        }
    }

    @Test
    @Config(qualifiers = PHONE)
    fun sportsHubPhone() {
        themed { Gallery(pad = 16.dp) }
        capture("sports-hub")
    }

    @Test
    @Config(qualifiers = TV)
    fun sportsHubTv() {
        themed {
            Column(Modifier.padding(PaddingValues(0.dp))) { Gallery(pad = 48.dp) }
        }
        capture("sports-hub-bigscreen")
    }
}
