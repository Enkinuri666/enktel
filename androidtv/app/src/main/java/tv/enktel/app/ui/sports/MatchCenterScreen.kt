package tv.enktel.app.ui.sports

import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import tv.enktel.app.AppGraph
import tv.enktel.app.data.TimeFormat
import tv.enktel.app.data.repo.Broadcast
import tv.enktel.app.data.repo.MatchDetail
import tv.enktel.app.data.repo.MatchEvent
import tv.enktel.app.data.repo.MatchStat
import tv.enktel.app.ui.components.Badge
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.LocalToaster
import tv.enktel.app.ui.theme.EnktelBg
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import tv.enktel.app.ui.components.tvRailFocus

/** Route for [MatchCenterScreen]. */
fun matchCenterRoute(eventId: String, title: String = ""): String =
    "matchCenter?event=$eventId&title=" + android.net.Uri.encode(title)

/**
 * Live Match Centre — the in-play picture for a single fixture.
 *
 * The Sports Hub can tell a user *that* a match is on and put them on the right
 * channel; this tells them what's actually happening in it. Scoreline, clock,
 * period, the goal/card timeline, the shot and possession splits, the venue,
 * every broadcaster officially carrying it, and — once it's over — a link to
 * the highlights package.
 *
 * Everything comes from TheSportsDB and every section is independent: a fixture
 * with a scoreline but no published stats renders the scoreline and drops the
 * stats block rather than showing a screen full of empty rows. If nothing at
 * all resolves, the screen says so plainly instead of spinning forever.
 *
 * While the match is in play the data refreshes on a [REFRESH_MS] timer, backed
 * off automatically when the device is running hot (see
 * [tv.enktel.app.data.net.ThermalGuard]) — a set-top box behind a TV throttling
 * mid-match is exactly what ruins the playback this screen sits next to.
 */
private const val REFRESH_MS = 30_000L

@Composable
fun MatchCenterScreen(
    graph: AppGraph,
    nav: NavHostController,
    eventId: String,
    fallbackTitle: String = "",
) {
    val toaster = LocalToaster.current
    // Observable read, so the kick-off line re-formats if the language changes.
    val uiLocale = TimeFormat.currentLocale()
    val context = androidx.compose.ui.platform.LocalContext.current
    var detail by remember(eventId) { mutableStateOf<MatchDetail?>(null) }
    var stats by remember(eventId) { mutableStateOf<List<MatchStat>>(emptyList()) }
    var timeline by remember(eventId) { mutableStateOf<List<MatchEvent>>(emptyList()) }
    var broadcasters by remember(eventId) { mutableStateOf<List<Broadcast>>(emptyList()) }
    var loading by remember(eventId) { mutableStateOf(true) }
    var tick by remember(eventId) { mutableIntStateOf(0) }

    // Resolving the published broadcaster names against this playlist. Channel
    // names, not the EPG — the guide is the unreliable part, which is the whole
    // reason this screen could not answer "which channel is it on".
    val profileForMatch by androidx.compose.runtime.produceState<tv.enktel.app.data.db.Profile?>(null) {
        value = try { graph.playlists.activeProfile() } catch (_: Throwable) { null }
    }
    val matchProfileId = profileForMatch?.id ?: -1L
    val channelsFlow = remember(matchProfileId) { graph.content.channels(matchProfileId) }
    val playlistChannels by channelsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val myChannels = remember(broadcasters, playlistChannels) {
        tv.enktel.app.data.repo.BroadcastMatcher.findAny(
            broadcasters = broadcasters.map { it.channel },
            channels = playlistChannels,
            limit = 6,
            key = { it.key },
            name = { it.name },
        )
    }

    LaunchedEffect(eventId, tick) {
        if (eventId.isBlank()) { loading = false; return@LaunchedEffect }
        try {
            detail = graph.scores.matchDetail(eventId)
            stats = graph.scores.matchStats(eventId)
            timeline = graph.scores.matchTimeline(eventId)
            broadcasters = graph.scores.broadcasts(eventId)
        } catch (ce: kotlinx.coroutines.CancellationException) { throw ce
        } catch (_: Throwable) { /* leave whatever we already have on screen */ }
        loading = false
    }
    // Only poll while there's something to poll for: a finished match's stats
    // don't change, and hammering a free API for them helps nobody.
    val inPlay = detail?.let { it.progress.isNotBlank() || it.status.equals("live", true) } ?: false
    LaunchedEffect(eventId, inPlay) {
        if (!inPlay) return@LaunchedEffect
        while (true) {
            val multiplier = tv.enktel.app.data.net.ThermalGuard.level.value.pollIntervalMultiplier
            if (multiplier.isInfinite()) return@LaunchedEffect
            delay((REFRESH_MS * multiplier).toLong())
            tick++
        }
    }

    val isMobile = tv.enktel.app.BuildConfig.FLAVOR == "mobile"
    val pad = if (isMobile) 16.dp else 48.dp
    val d = detail

    LazyColumn(
        Modifier.fillMaxSize().background(EnktelBg),
        contentPadding = PaddingValues(top = 20.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = pad),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Match Centre", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(
                        d?.league?.ifBlank { fallbackTitle } ?: fallbackTitle,
                        color = EnktelTextDim, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                FocusButton("↻", onClick = { tick++ })
                Spacer(Modifier.width(8.dp))
                FocusButton("Back", onClick = { nav.popBackStack() })
            }
        }

        when {
            loading && d == null -> item {
                Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                    Text("Loading match data…", color = EnktelTextDim, fontSize = 14.sp)
                }
            }
            d == null -> item {
                MatchCard(pad) {
                    Column(Modifier.padding(20.dp)) {
                        Text("No live data for this fixture", color = Color.White,
                            fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "In-play stats come from TheSportsDB, which doesn't cover every " +
                                "competition. You can still watch the match from the Sports Hub.",
                            color = EnktelTextDim, fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        if (d != null) {
            item { Scoreboard(d, pad) }

            if (d.venue.isNotBlank() || d.round.isNotBlank() || d.season.isNotBlank() || d.kickoffMs > 0) {
                item {
                    MatchCard(pad) {
                        Column(Modifier.padding(14.dp)) {
                            SubHeader("FIXTURE")
                            Spacer(Modifier.height(6.dp))
                            if (d.kickoffMs > 0) {
                                InfoLine(
                                    "Kick-off",
                                    TimeFormat.format("EEE d MMM · HH:mm", d.kickoffMs, uiLocale),
                                )
                            }
                            if (d.venue.isNotBlank()) InfoLine("Venue", d.venue)
                            if (d.country.isNotBlank()) InfoLine("Country", d.country)
                            if (d.round.isNotBlank()) InfoLine("Round", d.round)
                            if (d.season.isNotBlank()) InfoLine("Season", d.season)
                        }
                    }
                }
            }

            if (stats.isNotEmpty()) {
                item {
                    MatchCard(pad) {
                        Column(Modifier.padding(14.dp)) {
                            SubHeader("IN-PLAY STATS")
                            Spacer(Modifier.height(8.dp))
                            stats.forEach { StatRow(it) }
                        }
                    }
                }
            }

            if (timeline.isNotEmpty()) {
                item {
                    MatchCard(pad) {
                        Column(Modifier.padding(14.dp)) {
                            SubHeader("TIMELINE")
                            Spacer(Modifier.height(8.dp))
                            timeline.forEach { TimelineRow(it) }
                        }
                    }
                }
            }

            // Official broadcast guide: exactly the "where do I tune in" answer
            // an IPTV EPG can never give, since it only knows the user's own
            // channel list.
            if (broadcasters.isNotEmpty()) {
                item {
                    MatchCard(pad) {
                        Column(Modifier.padding(14.dp)) {
                            SubHeader("OFFICIAL BROADCASTERS")
                            Spacer(Modifier.height(8.dp))
                            LazyRow(modifier = Modifier.tvRailFocus(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(broadcasters, key = { it.channel + it.country }) { b -> BroadcasterChip(b) }
                            }
                            // Naming the broadcaster and stopping there is what
                            // sent people back to scroll fifteen thousand rows.
                            // These are the lines on their own playlist that
                            // carry it, and pressing one tunes.
                            Spacer(Modifier.height(12.dp))
                            SubHeader("ON YOUR PLAYLIST")
                            Spacer(Modifier.height(8.dp))
                            if (myChannels.isEmpty()) {
                                Text(
                                    "None of your channels matched those broadcaster names. " +
                                        "Providers rename lines freely, so it may still be " +
                                        "carried under something else — try the Channel Finder.",
                                    color = EnktelTextDim, fontSize = 12.sp,
                                )
                            } else {
                                LazyRow(
                                    modifier = Modifier.tvRailFocus(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(myChannels, key = { it.channel.key }) { hit ->
                                        FocusButton("▶  ${hit.channel.name}", accent = true, onClick = {
                                            toaster.info("Tuning to ${hit.channel.name}")
                                            nav.navigate("live?ch=${hit.channel.key}")
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (d.videoUrl.isNotBlank()) {
                item {
                    MatchCard(pad) {
                        Column(Modifier.padding(14.dp)) {
                            SubHeader("HIGHLIGHTS")
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "A highlights package has been published for this fixture.",
                                color = EnktelTextDim, fontSize = 12.sp,
                            )
                            Spacer(Modifier.height(10.dp))
                            FocusButton("▶  Watch highlights", accent = true, onClick = {
                                openHighlight(context, nav, toaster, d.videoUrl, d.name.ifBlank { fallbackTitle })
                            })
                        }
                    }
                }
            }

            if (d.description.isNotBlank()) {
                item {
                    MatchCard(pad) {
                        Column(Modifier.padding(14.dp)) {
                            SubHeader("REPORT")
                            Spacer(Modifier.height(6.dp))
                            Text(d.description, color = EnktelTextDim, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Opens a highlights clip. TheSportsDB links are almost always YouTube, which
 * ExoPlayer can't play directly, so those hand off to whatever app the device
 * uses for them; anything that looks like a direct media file plays in-app.
 */
internal fun openHighlight(
    context: android.content.Context,
    nav: NavHostController,
    toaster: tv.enktel.app.ui.components.Toaster,
    url: String,
    title: String,
) {
    val isYouTube = url.contains("youtube.com", true) || url.contains("youtu.be", true)
    if (isYouTube) {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri())
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Throwable) {
            toaster.error("No app on this device can open YouTube links")
        }
    } else {
        nav.navigate(tv.enktel.app.vodPlayerRoute(url, title))
    }
}

@Composable
private fun Scoreboard(d: MatchDetail, pad: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = pad)
            .clip(RoundedCornerShape(16.dp))
            .background(EnktelSurface.copy(0.75f))
            .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(16.dp)),
    ) {
        if (d.thumb.isNotBlank()) {
            // matchParentSize (not fillMaxSize): the card's height comes from
            // the Column below, and inside a LazyColumn the incoming height
            // constraint is unbounded, which fillMaxSize can't resolve.
            AsyncImage(
                model = d.thumb, contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(Modifier.matchParentSize().background(Color.Black.copy(0.62f)))
        }
        Column(Modifier.padding(18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (d.progress.isNotBlank()) Badge("● ${d.progress}", EnktelLive)
                else if (d.status.isNotBlank()) Badge(d.status.uppercase(), EnktelBlue)
                if (d.sport.isNotBlank()) Badge(d.sport, EnktelBlue)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamColumn(d.home, d.homeBadge, Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${d.homeScore}  –  ${d.awayScore}",
                        color = EnktelOk, fontSize = 34.sp, fontWeight = FontWeight.Black,
                    )
                    if (d.progress.isNotBlank()) {
                        Text(d.progress, color = EnktelTextDim, fontSize = 12.sp)
                    }
                }
                TeamColumn(d.away, d.awayBadge, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TeamColumn(name: String, badge: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (badge.isNotBlank()) {
            AsyncImage(
                model = badge, contentDescription = null,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(6.dp))
        }
        Text(
            name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatRow(stat: MatchStat) {
    val home = stat.home.filter { it.isDigit() }.toFloatOrNull() ?: 0f
    val away = stat.away.filter { it.isDigit() }.toFloatOrNull() ?: 0f
    val total = (home + away).coerceAtLeast(1f)
    Column(Modifier.padding(vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stat.home, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                stat.name, color = EnktelTextDim, fontSize = 11.sp,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(stat.away, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        // Split bar: home share from the left, away share from the right, so
        // the eye reads dominance without parsing the numbers. Weights are
        // floored above zero because Row rejects a zero weight.
        Row(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))) {
            Box(
                Modifier
                    .weight((home / total).coerceAtLeast(0.001f))
                    .fillMaxHeight()
                    .background(EnktelBlue),
            )
            Box(
                Modifier
                    .weight((away / total).coerceAtLeast(0.001f))
                    .fillMaxHeight()
                    .background(EnktelSurfaceHigh),
            )
        }
    }
}

@Composable
private fun TimelineRow(e: MatchEvent) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (e.minute.isNotBlank()) "${e.minute}'" else "–",
            color = EnktelBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(42.dp),
        )
        Text(iconFor(e.type), fontSize = 13.sp, color = Color.White)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.player.ifBlank { e.type }, color = Color.White, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(
                e.team.takeIf { it.isNotBlank() },
                e.assist.takeIf { it.isNotBlank() }?.let { "assist $it" },
            ).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, color = EnktelTextDim, fontSize = 11.sp)
        }
    }
}

private fun iconFor(type: String): String = when {
    type.contains("goal", true) -> "⚽"
    type.contains("yellow", true) -> "🟨"
    type.contains("red", true) -> "🟥"
    type.contains("subst", true) -> "🔁"
    type.contains("penalty", true) -> "🎯"
    else -> "•"
}

@Composable
private fun BroadcasterChip(b: Broadcast) {
    Row(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(EnktelSurfaceHigh.copy(0.7f))
            .border(1.dp, EnktelBlue.copy(0.4f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (b.logo.isNotBlank()) {
            AsyncImage(model = b.logo, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(b.channel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        if (b.country.isNotBlank()) {
            Spacer(Modifier.width(6.dp))
            Text(b.country, color = EnktelTextDim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MatchCard(pad: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = pad)
            .clip(RoundedCornerShape(14.dp))
            .background(EnktelSurface.copy(0.55f))
            .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(14.dp)),
    ) { content() }
}

@Composable
private fun SubHeader(text: String) {
    Text(text, color = EnktelBlue, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = EnktelTextDim, fontSize = 12.sp, modifier = Modifier.width(90.dp))
        Text(value, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
