package tv.enktel.app.ui.sports

import androidx.core.net.toUri
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Sports
import androidx.compose.material.icons.rounded.SportsBaseball
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.SportsCricket
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsFootball
import androidx.compose.material.icons.rounded.SportsGolf
import androidx.compose.material.icons.rounded.SportsHandball
import androidx.compose.material.icons.rounded.SportsHockey
import androidx.compose.material.icons.rounded.SportsMma
import androidx.compose.material.icons.rounded.SportsMotorsports
import androidx.compose.material.icons.rounded.SportsRugby
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.SportsTennis
import androidx.compose.material.icons.rounded.SportsVolleyball
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.HistoryToggleOff
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Scoreboard
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.runtime.mutableLongStateOf
import androidx.tv.material3.Icon
import tv.enktel.app.ui.components.FocusIconButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.data.TimeFormat
import tv.enktel.app.data.db.FollowedTeam
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.repo.FixtureTeams
import tv.enktel.app.data.repo.SportsEvent
import tv.enktel.app.data.repo.SportsRepository
import tv.enktel.app.data.repo.SportsRepository.SportsFixture
import tv.enktel.app.data.xtream.XtreamClient
import tv.enktel.app.dvr.MatchReminderScheduler
import tv.enktel.app.dvr.RecordScheduler
import tv.enktel.app.ui.components.Badge
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.LocalToaster
import tv.enktel.app.ui.components.ProgressBarThin
import tv.enktel.app.ui.components.tapClick
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelOk
import tv.enktel.app.ui.theme.EnktelPurple
import tv.enktel.app.ui.theme.EnktelSurface
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim
import tv.enktel.app.vodPlayerRoute
import tv.enktel.app.ui.components.rememberRailPosition
import tv.enktel.app.ui.components.tvRailFocus

@Suppress("ProduceStateDoesNotAssignValue")
@Composable
fun SportsHubScreen(graph: AppGraph, nav: NavHostController) {
    val profile by produceState<Profile?>(initialValue = null) {
        value = try { graph.playlists.activeProfile() } catch (_: Throwable) { null }
    }
    val p = profile ?: return
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val toaster = LocalToaster.current

    // Replaying a finished match is catch-up, and used to be its own private
    // copy of the one-guessed-URL bug — twice over, in the two replay rails.
    // Both now go through the shared resolver, which walks the shapes the
    // panel might actually serve and says so when none of them answer.
    fun replay(ev: SportsEvent) {
        if (!tv.enktel.app.data.catchup.CatchupUrls.isSupported(p, ev.channel)) {
            toaster.error("No catch-up archive on ${ev.channel.name}")
            return
        }
        scope.launch {
            val url = tv.enktel.app.data.catchup.CatchupUrls.resolve(
                graph.http, p, ev.channel, ev.startMs, ev.endMs,
            )
            if (url == null) {
                toaster.error("The provider has no recording of that match")
            } else {
                nav.navigate(vodPlayerRoute(url, "${ev.channel.name} · ${ev.title}"))
            }
        }
    }

    var loading by remember { mutableStateOf(true) }
    var events by remember { mutableStateOf<Map<String, List<SportsFixture>>>(emptyMap()) }
    // Saveable, not remembered. Opening a match and coming back disposes this
    // screen's composition, and everything it held in `remember` went with it
    // — the sport you picked, the my-teams filter, how far down you had got.
    // That is the complaint already fixed for Movies, Series, Live TV and
    // Search; the Sports Hub is the same screen in a different room, and it
    // is the one people leave and re-enter most, because every card on it is
    // a reason to leave.
    var sportFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }
    /** True while the refresh control is pulling the guide, so it can't be pressed twice. */
    var refreshing by remember { mutableStateOf(false) }
    var teamFilterOn by rememberSaveable { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val scoresEnabled by graph.settings.scoresEnabled.collectAsStateWithLifecycle(initialValue = false)
    val matchCenterEnabled by graph.settings.matchCenterEnabled.collectAsStateWithLifecycle(initialValue = true)
    var liveScores by remember { mutableStateOf<List<tv.enktel.app.data.repo.LiveScore>>(emptyList()) }
    val followed by graph.db.sportsDao().followed().collectAsStateWithLifecycle(initialValue = emptyList())
    // What the viewer has already asked to be told about.
    //
    // `upcomingReminders` had no callers at all: pressing the bell stored a
    // row, scheduled a notification and then showed nothing anywhere, so there
    // was no list of what you had set and no way to undo one. Press it twice
    // and you were told twice.
    //
    // Subscribed once against the moment the screen opened rather than against
    // the ticker, which would re-run the query every thirty seconds; the
    // ticker trims the list below instead.
    val openedAt = remember { System.currentTimeMillis() }
    val remindersFlow = remember(openedAt) { graph.db.sportsDao().upcomingReminders(openedAt) }
    val allReminders by remindersFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    // Official broadcast schedule + published highlight packages. Both come
    // from TheSportsDB and are independent of the user's EPG, so they still
    // populate on a playlist with no guide data at all.
    var todaysFixtures by remember { mutableStateOf<List<tv.enktel.app.data.repo.LiveScore>>(emptyList()) }
    var highlightClips by remember { mutableStateOf<List<tv.enktel.app.data.repo.HighlightClip>>(emptyList()) }
    /** Official broadcasters for today's fixtures, keyed by event id. */
    var broadcastsByEvent by remember {
        mutableStateOf<Map<String, List<tv.enktel.app.data.repo.Broadcast>>>(emptyMap())
    }

    var scanCoverage by remember {
        mutableStateOf(tv.enktel.app.data.repo.SportsRepository.ScanCoverage())
    }
    LaunchedEffect(refreshTick, sportFilter) {
        loading = true; loadError = null
        try {
            events = graph.sports.load(p.id, sportFilter.orEmpty())
            scanCoverage = graph.sports.lastScan
        } catch (ce: kotlinx.coroutines.CancellationException) { throw ce
        } catch (t: Throwable) {
            loadError = t.message ?: "Could not load sports events"
            events = emptyMap()
        }
        loading = false
    }
    var scoresStatus by remember { mutableStateOf("") }
    LaunchedEffect(scoresEnabled, refreshTick) {
        liveScores = try { if (scoresEnabled) graph.scores.live() else emptyList() }
        catch (ce: kotlinx.coroutines.CancellationException) { throw ce }
        catch (_: Throwable) { emptyList() }
        // An empty scoreboard and an unreachable one looked identical, so
        // "live scores are on and I see no difference" had no explanation
        // anywhere in the UI. Say which it is.
        scoresStatus = if (scoresEnabled && liveScores.isEmpty()) graph.scores.lastStatus else ""
    }
    LaunchedEffect(matchCenterEnabled, sportFilter, refreshTick) {
        if (!matchCenterEnabled) {
            todaysFixtures = emptyList(); highlightClips = emptyList()
            return@LaunchedEffect
        }
        val sportQuery = sportFilter?.let { sportsDbName(it) }.orEmpty()
        todaysFixtures = try {
            graph.scores.scheduleForDay(System.currentTimeMillis(), sportQuery)
        } catch (ce: kotlinx.coroutines.CancellationException) { throw ce
        } catch (_: Throwable) { emptyList() }
        highlightClips = try {
            graph.scores.highlights(days = 2, sport = sportQuery)
        } catch (ce: kotlinx.coroutines.CancellationException) { throw ce
        } catch (_: Throwable) { emptyList() }
        // One request for the whole day's broadcaster listings, so every
        // fixture in the rail can say where it is on. Per-fixture lookups would
        // be forty calls to a rate-limited free tier, which is why this used to
        // live one screen deeper in the Match Centre and most people never
        // found it.
        broadcastsByEvent = try {
            graph.scores.broadcastsForDay(System.currentTimeMillis(), sportQuery)
        } catch (ce: kotlinx.coroutines.CancellationException) { throw ce
        } catch (_: Throwable) { emptyMap() }
    }

    // The user's own channel list, for turning a broadcaster's published name
    // into a line they can actually tune. Deliberately not the EPG: guide data
    // is the thing that is unreliable here, channel names are not.
    // Remembered on the profile, not rebuilt per recomposition: this screen
    // re-renders on a thirty-second tick and re-subscribing a query over a
    // fifteen-thousand-row table that often is a cost for nothing.
    val channelsFlow = remember(p.id) { graph.content.channels(p.id) }
    val allChannelsForMatch by channelsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val isMobile = tv.enktel.app.BuildConfig.FLAVOR == "mobile"
    val padHoriz = if (isMobile) 16.dp else 48.dp

    // Drives every elapsed time and countdown on the screen. See
    // rememberNowTicker: read once at composition, they stopped moving.
    val now = rememberNowTicker()

    val allSports = remember(events) {
        try { graph.sports.sportsInSet(events) } catch (_: Throwable) { emptyList() }
    }

    /** The followed names as stored — lowercased, and matched as whole words. */
    val teamNames = remember(followed) { followed.map { it.name } }

    // Which rail a fixture belongs in is a question about the clock, so it is
    // asked against the ticker rather than taken from the scan.
    //
    // The scan buckets once, when it runs, and nothing re-ran it: a hub left
    // open while waiting for a kick-off — the single most likely thing anyone
    // does with this screen — kept the match in UPCOMING after it had started,
    // counting down to a time in the past, while LIVE NOW stayed empty.
    //
    // Recomputed on the minute rather than on every tick of the clock: the
    // phase of forty fixtures changes a few times an hour, and this walks all
    // of them.
    val minuteTick = now / 60_000L
    val rails = remember(events, minuteTick, teamFilterOn, teamNames) {
        val all = events.values.flatten()
        fun rail(phase: String, newestFirst: Boolean): List<SportsFixture> {
            val inPhase = all.filter { it.phaseAt(now) == phase }
            val sorted =
                if (newestFirst) inPhase.sortedByDescending { it.event.startMs }
                else inPhase.sortedBy { it.event.startMs }
            val kept =
                if (teamFilterOn) sorted.filter { SportsRepository.followsAny(it, teamNames) }
                else sorted
            // Followed fixtures to the top of every rail — which Settings has
            // promised in writing for several versions while only the Live
            // sport finder did it.
            return SportsRepository.pinFollowed(kept, teamNames)
        }
        Triple(rail("LIVE", false), rail("UPCOMING", false), rail("FINISHED", true))
    }
    val live = rails.first
    val upcoming = rails.second
    val finished = rails.third

    // The two rails that come from TheSportsDB rather than the EPG get the
    // same treatment: a followed team first. They are at the top of the
    // screen and they are what gets scanned, so leaving them in the feed's own
    // order is where "I follow Arsenal and nothing changed" came from.
    val scoreRail = remember(liveScores, teamNames) { pinByTeam(liveScores, teamNames) }
    val todayRail = remember(todaysFixtures, teamNames) { pinByTeam(todaysFixtures, teamNames) }

    // A reminder for a match that has finished is clutter; one for a match
    // already in play is still the answer to "where is it on".
    val reminders = remember(allReminders, minuteTick) { allReminders.filter { it.endMs > now } }
    val remindedKeys = remember(reminders) { reminders.mapTo(HashSet()) { it.key } }

    /**
     * Does the viewer follow every side of this fixture?
     *
     * Every side rather than any, which is what the rails use for pinning: a
     * star is a control, and a filled one that still has something to add
     * ("you follow Arsenal, press to also follow Chelsea") is a control
     * lying about its own state.
     */
    fun followsAllSides(sides: List<String>): Boolean =
        sides.isNotEmpty() && sides.all { t -> teamNames.any { FixtureTeams.mentions(t, it) } }

    /**
     * Follow — or stop following — both sides of a fixture, from its card.
     *
     * Following lived in Settings behind a text field you had to spell
     * correctly, which is why the hub's my-teams filter was a button most
     * people never saw: it only appears once something is followed. The teams
     * are on the card already; this makes them pressable.
     */
    fun toggleFollow(ev: SportsEvent) {
        // The cards hide the star when this is empty, so reaching here means
        // something changed under the press; nothing to do rather than a
        // message about it.
        val teams = FixtureTeams.parse(ev.title)
        if (teams.isEmpty()) return
        scope.launch {
            try {
                val dao = graph.db.sportsDao()
                val missing = teams.filterNot { t -> teamNames.any { FixtureTeams.mentions(t, it) } }
                if (missing.isEmpty()) {
                    followed
                        .filter { f -> teams.any { FixtureTeams.mentions(it, f.name) } }
                        .forEach { dao.unfollow(it.name) }
                    toaster.info("Stopped following ${teams.joinToString(" and ")}")
                } else {
                    missing.forEach { t ->
                        dao.follow(
                            FollowedTeam(name = t.lowercase().trim(), displayName = t.trim()),
                        )
                    }
                    toaster.success("Following ${missing.joinToString(" and ")}")
                }
            } catch (t: Throwable) {
                toaster.error(t.message ?: "Could not change who you follow")
            }
        }
    }

    /** Set the reminder, or clear the one that is already set. */
    fun toggleReminder(ev: SportsEvent) {
        val key = MatchReminderScheduler.keyFor(ev.channel.key, ev.startMs)
        scope.launch {
            try {
                if (key in remindedKeys) {
                    MatchReminderScheduler.cancel(context, key)
                    toaster.info("Reminder cleared")
                } else {
                    MatchReminderScheduler.schedule(
                        context, ev.channel.key, ev.channel.name, ev.title, ev.startMs, ev.endMs,
                    )
                    toaster.success("Reminder set — ${MatchReminderScheduler.LEAD_MIN} min before kick-off")
                }
            } catch (t: Throwable) {
                toaster.error(t.message ?: "Reminder failed")
            }
        }
    }

    // Where the screen was, so coming back from a match lands there.
    //
    // The count is the restore's "the rails have arrived" signal: the EPG scan
    // takes a second or two to come back, and a position applied to an empty
    // list is measured away before the fixtures land. See rememberRailPosition.
    val contentCount = live.size + upcoming.size + finished.size +
        liveScores.size + todaysFixtures.size + highlightClips.size
    val railState = rememberRailPosition(
        signature = "${sportFilter.orEmpty()}\u0001$teamFilterOn",
        itemCount = contentCount,
    )

    LazyColumn(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(EnktelSurface.copy(0.4f), EnktelSurface.copy(0.0f)))
        ),
        state = railState,
        contentPadding = PaddingValues(top = 20.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            GlassCard(padHoriz = padHoriz) {
                // Actions beside the title on a television, under it on a
                // phone.
                //
                // One Row held the title, three badges and up to three
                // buttons. On a 411dp handset the buttons alone want ~230dp,
                // which left the title column too narrow for "Sports Hub" and
                // its counts: the badge row clipped mid-word, and on the
                // narrowest devices the refresh control was pushed off the
                // edge entirely — the one button you press when the hub looks
                // wrong.
                val actions: @Composable () -> Unit = {
                    // Smart Channel Finder: the "it kicks off in two minutes
                    // and I can't find the channel" escape hatch.
                    FocusIconButton(
                        Icons.Rounded.TravelExplore, "Find the channel showing a match",
                        label = "On now", accent = true,
                        onClick = { nav.navigate("sportsFinder") },
                    )
                    if (followed.isNotEmpty()) {
                        FocusIconButton(
                            Icons.Rounded.Star,
                            if (teamFilterOn) "Show every match" else "Show only my teams",
                            label = if (teamFilterOn) "My teams" else null,
                            accent = teamFilterOn,
                            onClick = { teamFilterOn = !teamFilterOn },
                        )
                    }
                    // Refresh the sources, not just the query.
                    //
                    // This used to only bump refreshTick, which re-ran a scan
                    // over the local EPG — data that had not changed, so the
                    // fixtures list came back identical and the button read as
                    // dead. The guide is the thing that goes stale, so pull it
                    // first and then rescan; the score and schedule feeds are
                    // already live calls and re-run off the same tick.
                    FocusIconButton(
                        Icons.Rounded.Refresh,
                        if (refreshing) "Refreshing the guide" else "Refresh the guide",
                        onClick = {
                            if (refreshing) return@FocusIconButton
                            scope.launch {
                                refreshing = true
                                try { graph.epg.refresh(p) } catch (_: Throwable) { /* rescan anyway */ }
                                refreshing = false
                                refreshTick++
                            }
                        },
                    )
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Sports Hub", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            Row(
                                Modifier.padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Badge("${live.size} LIVE", EnktelLive)
                                Badge("${upcoming.size} UPCOMING", EnktelBlue)
                                Badge("${finished.size} REPLAYS", EnktelOk)
                            }
                        }
                        if (!isMobile) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { actions() }
                        }
                    }
                    if (isMobile) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
                    }
                }
            }
        }
        // Say so when the scan didn't cover the whole playlist, rather than
        // letting a truncated list read as "your match isn't on". Picking a
        // sport narrows the scan instead of truncating it, so that's the
        // actionable advice.
        if (scanCoverage.truncated && loadError == null) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = padHoriz, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        buildString {
                            append("Showing a partial scan")
                            if (scanCoverage.channelsMatched > scanCoverage.channelsScanned) {
                                append(" — ${scanCoverage.channelsScanned} of ")
                                append("${scanCoverage.channelsMatched} sports channels")
                            }
                            append(". Pick a sport to search the rest.")
                        },
                        color = EnktelTextDim, fontSize = 11.sp,
                    )
                }
            }
        }
        if (allSports.isNotEmpty()) {
            item {
                tv.enktel.app.ui.components.ChipRowLabel(
                    "Sport",
                    modifier = Modifier.padding(start = padHoriz, bottom = 4.dp),
                )
            }
            item {
                LazyRow(
                    modifier = Modifier.tvRailFocus(),
                    contentPadding = PaddingValues(horizontal = padHoriz),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        tv.enktel.app.ui.components.GlassChip(
                            "All", selected = sportFilter == null,
                            accent = EnktelBlue,
                            onClick = { sportFilter = null },
                        )
                    }
                    items(allSports, key = { it }) { sport ->
                        tv.enktel.app.ui.components.GlassChip(
                            sport, selected = sportFilter == sport,
                            accent = EnktelBlue,
                            onClick = { sportFilter = if (sportFilter == sport) null else sport },
                        )
                    }
                }
            }
        }
        // What the viewer asked to be told about, and the only place they can
        // take it back.
        //
        // The bell on an UPCOMING card wrote a row and scheduled a
        // notification, and that was the end of it: no list of what you had
        // set, no way to cancel one, and a second press meant a second alert.
        // This rail is also the only part of the hub that survives a playlist
        // with no guide data, because it comes out of the database.
        if (reminders.isNotEmpty()) {
            item {
                SectionHeader(
                    Icons.Rounded.NotificationsActive, "REMINDERS", EnktelPurple, padHoriz,
                    count = reminders.size,
                    subtitle = "${MatchReminderScheduler.LEAD_MIN} min before kick-off",
                )
            }
            item {
                LazyRow(
                    modifier = Modifier.tvRailFocus(),
                    contentPadding = PaddingValues(horizontal = padHoriz),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(reminders, key = { it.key }) { r ->
                        ReminderChip(
                            reminder = r,
                            now = now,
                            onTap = {
                                toaster.info("Tuning to ${r.channelName}")
                                nav.navigate("live?ch=${r.channelKey}")
                            },
                            onCancel = {
                                scope.launch {
                                    try {
                                        MatchReminderScheduler.cancel(context, r.key)
                                        toaster.info("Reminder cleared")
                                    } catch (t: Throwable) {
                                        toaster.error(t.message ?: "Could not clear it")
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
        when {
            loadError != null -> item {
                GlassCard(padHoriz = padHoriz) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Couldn't load sports", color = EnktelLive, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(loadError!!, color = EnktelTextDim, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        FocusButton("Try again", accent = true, onClick = { refreshTick++ })
                    }
                }
            }
            loading && events.isEmpty() -> item {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    Text("Scanning EPG for sports events…", color = EnktelTextDim, fontSize = 14.sp)
                }
            }
            live.isEmpty() && upcoming.isEmpty() && finished.isEmpty() -> item {
                GlassCard(padHoriz = padHoriz) {
                    Column(Modifier.padding(20.dp)) {
                        Text("No sports events found yet.", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "If you just added the playlist, wait for the EPG download to finish, or refresh it manually.",
                            color = EnktelTextDim, fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FocusButton("Refresh EPG", accent = true, onClick = {
                                scope.launch {
                                    try { graph.epg.refresh(p) } catch (_: Throwable) {}
                                    refreshTick++
                                }
                            })
                            FocusButton("Rescan", onClick = { refreshTick++ })
                        }
                    }
                }
            }
        }
        // Live-scores ticker: shows every match currently in progress across all channels,
        // even ones our EPG scan missed. Tap → jump straight to whichever channel is
        // carrying that match if we recognise the team names in a live channel title.
        if (scoresEnabled && liveScores.isEmpty() && scoresStatus.isNotBlank()) {
            item {
                Text(
                    scoresStatus,
                    color = EnktelTextDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = padHoriz, vertical = 6.dp),
                )
            }
        }
        if (scoresEnabled && scoreRail.isNotEmpty()) {
            item { SectionHeader(Icons.Rounded.Scoreboard, "LIVE SCORES", EnktelOk, padHoriz, count = scoreRail.size) }
            item {
                LazyRow(
                    modifier = Modifier.tvRailFocus(),
                    contentPadding = PaddingValues(horizontal = padHoriz),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(scoreRail, key = { "${it.home}-${it.away}-${it.league}" }) { s ->
                        LiveScoreChip(
                            score = s,
                            // A scoreline we can identify opens the Match Centre;
                            // one we can't still tunes to a matching channel.
                            onStats = s.eventId.takeIf { it.isNotBlank() && matchCenterEnabled }?.let { id ->
                                { nav.navigate(matchCenterRoute(id, "${s.home} v ${s.away}")) }
                            },
                            onTap = {
                                // A channel whose name carries one of the teams —
                                // the pay-per-view and event lines, which is where
                                // this actually pays off ("UK: PPV 04 | Arsenal vs
                                // Chelsea").
                                //
                                // Matched on whole words against the list this
                                // screen already holds. It used to re-query the
                                // channel table on every press and test for a bare
                                // substring, so following Roma tuned to a Romanian
                                // channel and a 15,000-row query ran for a tap.
                                val matchChannel = allChannelsForMatch.firstOrNull { ch ->
                                    FixtureTeams.mentions(ch.name, s.home) ||
                                        FixtureTeams.mentions(ch.name, s.away)
                                }
                                if (matchChannel != null) {
                                    toaster.info("Tuning to ${matchChannel.name}")
                                    nav.navigate("live?ch=${matchChannel.key}")
                                } else {
                                    toaster.info("No live channel matched for that match")
                                }
                            },
                        )
                    }
                }
            }
        }
        // Official broadcast guide — the published schedule for today, straight
        // from TheSportsDB rather than the user's EPG. It answers "what's on and
        // where do I tune in" even for fixtures the playlist doesn't carry, and
        // each row opens the Match Centre where the broadcaster list lives.
        if (matchCenterEnabled && todayRail.isNotEmpty()) {
            item {
                SectionHeader(
                    Icons.Rounded.Podcasts, "ON TODAY", EnktelBlue, padHoriz,
                    count = todayRail.size,
                    subtitle = "Published schedule",
                )
            }
            item {
                LazyRow(
                    modifier = Modifier.tvRailFocus(),
                    contentPadding = PaddingValues(horizontal = padHoriz),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        todayRail.take(40),
                        key = { "${it.eventId}-${it.home}-${it.away}" },
                    ) { f ->
                        val casts = broadcastsByEvent[f.eventId].orEmpty()
                        // The published broadcaster, resolved against this
                        // playlist. Remembered per fixture because it walks
                        // every channel, and the rail recomposes as it scrolls.
                        val mine = remember(f.eventId, casts, allChannelsForMatch) {
                            tv.enktel.app.data.repo.BroadcastMatcher.findAny(
                                broadcasters = casts.map { it.channel },
                                channels = allChannelsForMatch,
                                key = { it.key },
                                name = { it.name },
                            )
                        }
                        FixtureChip(
                            fixture = f,
                            broadcasters = casts.map { it.channel },
                            tuneTo = mine.firstOrNull()?.channel,
                            onTap = {
                                // Tuning beats reading about it. Only when the
                                // published broadcaster resolved to a real line
                                // on this playlist — otherwise the Match Centre,
                                // which is still the place to read the rest.
                                val ch = mine.firstOrNull()?.channel
                                when {
                                    ch != null -> {
                                        toaster.info("Tuning to ${ch.name}")
                                        nav.navigate("live?ch=${ch.key}")
                                    }
                                    f.eventId.isNotBlank() ->
                                        nav.navigate(matchCenterRoute(f.eventId, "${f.home} v ${f.away}"))
                                    else -> toaster.info("No match data published for that fixture yet")
                                }
                            },
                        )
                    }
                }
            }
        }
        // Published highlight packages for fixtures that have already finished.
        if (matchCenterEnabled && highlightClips.isNotEmpty()) {
            item { SectionHeader(Icons.Rounded.Movie, "HIGHLIGHTS", EnktelOk, padHoriz, subtitle = "Latest packages") }
            item {
                LazyRow(
                    modifier = Modifier.tvRailFocus(),
                    contentPadding = PaddingValues(horizontal = padHoriz),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(highlightClips.take(30), key = { it.videoUrl }) { clip ->
                        HighlightCard(
                            clip = clip,
                            onPlay = { openHighlight(context, nav, toaster, clip.videoUrl, clip.title) },
                        )
                    }
                }
            }
        }
        if (live.isNotEmpty()) {
            item { SectionHeader(Icons.Rounded.FiberManualRecord, "LIVE NOW", EnktelLive, padHoriz, count = live.size) }
            items(live, key = { "L-${it.event.channel.key}-${it.event.program.id}" }) { fx ->
                val ev = fx.event
                val sides = remember(ev.title) { FixtureTeams.parse(ev.title) }
                // Remembered, not recomputed. This walks the whole scoreboard
                // per card, and the screen recomposes every thirty seconds off
                // the ticker — forty cards times a scoreboard, twice a minute,
                // for an answer that only changes when the scores do.
                val matchedScore = remember(ev.title, liveScores, scoresEnabled) {
                    try {
                        if (scoresEnabled) graph.scores.matchByTitle(ev.title, liveScores) else null
                    } catch (_: Throwable) { null }
                }
                LiveEventCard(
                    ev, score = matchedScore, padHoriz = padHoriz, now = now,
                    alsoOn = fx.alsoOn,
                    following = followsAllSides(sides),
                    onFollow = if (sides.isEmpty()) null else { { toggleFollow(ev) } },
                    onTap = {
                        toaster.info("Tuning to ${ev.channel.name}")
                        nav.navigate("live?ch=${ev.channel.key}")
                    },
                    // Only offer the Match Centre when we actually resolved this
                    // programme to a real fixture — an empty stats screen is
                    // worse than no button.
                    onStats = matchedScore?.eventId
                        ?.takeIf { it.isNotBlank() && matchCenterEnabled }
                        ?.let { id -> { nav.navigate(matchCenterRoute(id, ev.title)) } },
                )
            }
        }
        if (upcoming.isNotEmpty()) {
            item { SectionHeader(Icons.Rounded.CalendarMonth, "UPCOMING", EnktelBlue, padHoriz, count = upcoming.size) }
            items(upcoming, key = { "U-${it.event.channel.key}-${it.event.program.id}" }) { fx ->
                val ev = fx.event
                val sides = remember(ev.title) { FixtureTeams.parse(ev.title) }
                UpcomingEventCard(
                    ev, padHoriz = padHoriz, now = now,
                    alsoOn = fx.alsoOn,
                    following = followsAllSides(sides),
                    reminded = MatchReminderScheduler.keyFor(ev.channel.key, ev.startMs) in remindedKeys,
                    onFollow = if (sides.isEmpty()) null else { { toggleFollow(ev) } },
                    onSchedule = {
                        scope.launch {
                            try {
                                val url = if (p.kind == "m3u") ev.channel.url
                                    else XtreamClient.liveUrl(p, ev.channel.streamId, hls = false)
                                RecordScheduler.schedule(context, p.id, ev.title, ev.channel.name, url, ev.startMs, ev.endMs)
                                toaster.success("Recording scheduled")
                            } catch (t: Throwable) { toaster.error(t.message ?: "Schedule failed") }
                        }
                    },
                    onRemind = { toggleReminder(ev) },
                    onOpen = { nav.navigate("live?ch=${ev.channel.key}") },
                )
            }
        }
        // Split "finished" into fresh Highlights (last 6h — likely still trending) and
        // older Replays (>6h ago). Same tap behaviour: pull from catch-up if the channel
        // supports it.
        val highlightWindow = 6 * 60 * 60_000L
        val highlights = finished.filter { now - it.event.endMs <= highlightWindow }
        val olderReplays = finished.filter { now - it.event.endMs > highlightWindow }
        if (highlights.isNotEmpty()) {
            item {
                SectionHeader(
                    Icons.Rounded.HistoryToggleOff, "CATCH-UP", EnktelOk, padHoriz,
                    count = highlights.size,
                    subtitle = "Finished in the last 6 hours",
                )
            }
            items(highlights, key = { "H-${it.event.channel.key}-${it.event.program.id}" }) { fx ->
                FinishedEventCard(
                    fx.event, padHoriz = padHoriz,
                    alsoOn = fx.alsoOn,
                    onReplay = { replay(fx.event) },
                )
            }
        }
        if (olderReplays.isNotEmpty()) {
            item { SectionHeader(Icons.Rounded.Replay, "REPLAYS", EnktelBlue, padHoriz, count = olderReplays.size) }
            items(olderReplays, key = { "F-${it.event.channel.key}-${it.event.program.id}" }) { fx ->
                FinishedEventCard(
                    fx.event, padHoriz = padHoriz,
                    alsoOn = fx.alsoOn,
                    onReplay = { replay(fx.event) },
                )
            }
        }
    }
}

/**
 * A followed team's fixtures first, in a feed that knows nothing about them.
 *
 * The live scoreboard and the published schedule come from TheSportsDB, which
 * has its own idea of order — alphabetical by league, broadly — and they are
 * the two rails at the top of the screen, which makes them the ones people
 * scan. Leaving them alone while the EPG rails below pinned was half an
 * answer, and half an answer to "I followed my team and nothing changed" is
 * the same as none.
 *
 * A stable partition, like the rails': within each half the feed keeps its own
 * order, which is the only order it has published.
 */
private fun pinByTeam(
    fixtures: List<tv.enktel.app.data.repo.LiveScore>,
    teams: List<String>,
): List<tv.enktel.app.data.repo.LiveScore> {
    if (teams.isEmpty() || fixtures.isEmpty()) return fixtures
    val (mine, rest) = fixtures.partition { f ->
        val haystack = "${f.home} ${f.away} ${f.league}"
        teams.any { FixtureTeams.mentions(haystack, it) }
    }
    return mine + rest
}

/**
 * A clock that moves.
 *
 * Every card here derived its timings from one `System.currentTimeMillis()`
 * read at composition, so the live progress bar, the "37m in" caption and the
 * "in 2h 14m" countdown were all photographs of the moment the rail was drawn.
 * On a television left on the Sports Hub — which is exactly what happens while
 * waiting for a kick-off — the bar sat still through an entire half, and the
 * countdown to a match that had already started still said it was upcoming.
 *
 * One ticker for the whole screen rather than one per card: forty cards each
 * holding their own coroutine to observe the same clock is forty wake-ups a
 * minute to compute the same number.
 *
 * The period is a compromise. A second would move the bar smoothly and
 * recompose every visible card sixty times a minute for a bar that advances
 * less than a pixel; thirty seconds is under half a minute of staleness on a
 * caption whose smallest unit is the minute.
 */
@Composable
private fun rememberNowTicker(periodMs: Long = 30_000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            kotlinx.coroutines.delay(periodMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
private fun GlassCard(padHoriz: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = padHoriz)
            .clip(RoundedCornerShape(16.dp))
            .background(EnktelSurface.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
    ) { content() }
}

/**
 * A rail heading: stripe, icon, title, and an optional count.
 *
 * This and the cards below it are `internal` rather than `private` so
 * `SportsHubScreenshotTest` can draw them with fabricated fixtures. The screen
 * itself needs a profile, an EPG scan and three network feeds before it will
 * render anything, which is not a thing a Robolectric test can stand up — and
 * a card nobody has looked at is how a stretched logo and an invisible play
 * marker survived this long.
 *
 * The titles used to carry their own emoji — "⏪ CATCH-UP — FINISHED IN THE
 * LAST 6 HOURS" — which put a full sentence in 13sp black caps and wrapped it
 * on a phone. The icon says the same thing in a fixed 15dp, tinted to match
 * the stripe, so the words can go back to being a label; what the six hours
 * meant now lives in [subtitle], at a weight a heading should not be using.
 */
@Composable
internal fun SectionHeader(
    icon: ImageVector,
    text: String,
    color: Color,
    padHoriz: androidx.compose.ui.unit.Dp,
    count: Int? = null,
    subtitle: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = padHoriz, end = padHoriz, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(4.dp, 20.dp).background(color, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(10.dp))
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Black)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text("$count", color = color.copy(alpha = 0.65f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (subtitle != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                subtitle, color = EnktelTextDim, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Shared focus treatment for the Sports cards.
 *
 * These three are the only list cards in the app that do not go through
 * PosterCard, so they missed the depth pass and stayed flat: a Surface with a
 * container colour, no shadow, no scale, no focus border. Against rails that
 * now lift and glow, they read as a different, cheaper app.
 *
 * Kept as one modifier rather than copied into each card so the language stays
 * identical — the whole point is that Sports should feel like the same product
 * as Home.
 */
@Composable
private fun Modifier.sportsCardFocus(
    focused: Boolean,
    accent: Color,
): Modifier {
    val elevation by animateDpAsState(
        targetValue = if (focused) 14.dp else 2.dp,
        animationSpec = tween(if (focused) 170 else 250),
        label = "sportsCardElevation",
    )
    val lift by animateDpAsState(
        targetValue = if (focused) (-4).dp else 0.dp,
        animationSpec = tween(if (focused) 170 else 250),
        label = "sportsCardLift",
    )
    return this
        // See PosterCard: the lambda overload keeps an animated offset in the
        // layout phase rather than recomposing on every frame.
        .offset { IntOffset(0, lift.roundToPx()) }
        .shadow(
            elevation = elevation,
            shape = RoundedCornerShape(16.dp),
            clip = false,
            spotColor = if (focused) accent else Color.Black,
        )
}

/**
 * Focus scale for a full-width card: none.
 *
 * tv-material's Surface defaults to `focusedScale = 1.1f`, and it applies that
 * to the Surface's own graphics layer. [sportsCardFocus] draws its shadow on
 * the modifier chain *outside* that layer, so on focus the glow kept drawing
 * at 100 % while the card drew at 110 % — a tinted rectangle sitting visibly
 * offset from the card, and the wider the card the further out it sat. These
 * are full-width rows, so they have nowhere to grow into anyway: they would
 * scale straight off both edges of the screen.
 *
 * Focus is carried by the shadow, the accent tint and the border instead, all
 * of which share one set of bounds. Same conclusion NavRailItem reached.
 */
@Composable
private fun noGrowScale() = ClickableSurfaceDefaults.scale(focusedScale = 1f)

@Composable
internal fun LiveEventCard(
    ev: SportsEvent,
    score: tv.enktel.app.data.repo.LiveScore?,
    padHoriz: androidx.compose.ui.unit.Dp,
    /** From the screen's ticker, so the bar and the caption keep moving. */
    now: Long,
    onTap: () -> Unit,
    onStats: (() -> Unit)? = null,
    /** How many other lines carry this same fixture. See SportsFixture. */
    alsoOn: Int = 0,
    /** True when one of the viewer's followed teams is in it. */
    following: Boolean = false,
    /** Follow or unfollow the sides of this fixture; absent when it has none. */
    onFollow: (() -> Unit)? = null,
) {
    val frac = ((now - ev.startMs).toFloat() / (ev.endMs - ev.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
    var cardFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onTap,
        modifier = Modifier.fillMaxWidth().padding(horizontal = padHoriz)
            .sportsCardFocus(cardFocused, EnktelLive)
            .onFocusChanged { cardFocused = it.isFocused }.tapClick(onTap),
        scale = noGrowScale(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        // Was EnktelLive.copy(0.14f) — a 14 % red wash over the near-black
        // background, which rendered as muddy maroon on a phone and made a row
        // of live fixtures look like a row of error states. Live-ness is now
        // carried by the accent stripe and the LIVE badge, against the same
        // neutral elevated surface every other card in the app uses, so the
        // red reads as signal instead of tinting the whole panel.
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EnktelSurfaceHigh.copy(0.45f),
            focusedContainerColor = EnktelLive.copy(0.32f),
            focusedContentColor = Color.White,
            contentColor = Color.White,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Left accent stripe — the live marker, matching the section
                // headers' stripe so the visual language stays consistent.
                Box(
                    Modifier
                        .size(width = 3.dp, height = 44.dp)
                        .background(EnktelLive, RoundedCornerShape(4.dp)),
                )
                Spacer(Modifier.width(10.dp))
                ChannelLogo(ev.channel.logo, ev.channel.name)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Badge("LIVE", EnktelLive)
                        SportBadge(ev.sport)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(ev.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        ev.channel.name, color = EnktelTextDim, fontSize = 11.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                FollowStar(following, onFollow)
                if (onStats != null) {
                    FocusIconButton(Icons.Rounded.Insights, "Match statistics", onClick = onStats)
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    Icons.Rounded.PlayArrow, contentDescription = null,
                    tint = Color.White, modifier = Modifier.size(26.dp),
                )
            }
            if (score != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "${score.home} ${score.homeScore} — ${score.awayScore} ${score.away}   ${score.minute}",
                    color = EnktelOk, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            ProgressBarThin(frac, Modifier.fillMaxWidth())
            Text(
                "${hhmm(ev.startMs)} — ${hhmm(ev.endMs)}  ·  ${(now - ev.startMs) / 60_000}m in" +
                    feedsSuffix(alsoOn),
                color = EnktelTextDim, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
internal fun UpcomingEventCard(
    ev: SportsEvent,
    padHoriz: androidx.compose.ui.unit.Dp,
    /** From the screen's ticker, so the countdown actually counts down. */
    now: Long,
    onSchedule: () -> Unit,
    onRemind: () -> Unit,
    onOpen: () -> Unit,
    /** How many other lines carry this same fixture. See SportsFixture. */
    alsoOn: Int = 0,
    /** True when one of the viewer's followed teams is in it. */
    following: Boolean = false,
    /**
     * True when a reminder is already set for this fixture.
     *
     * The bell had no state: it looked identical before and after, so the only
     * way to find out whether it had worked was to press it again — which set
     * a second one.
     *
     * Carried by the bell alone — struck through and accented — rather than
     * also by a "REMINDER SET" badge, which is where this started: a third
     * badge on a row that already holds the sport and the countdown has no
     * room left on a 411dp handset, and it rendered one letter per line.
     */
    reminded: Boolean = false,
    /** Follow or unfollow the sides of this fixture; absent when it has none. */
    onFollow: (() -> Unit)? = null,
) {
    val inMs = ev.startMs - now
    val eta = when {
        inMs < 60 * 60_000L -> "in ${(inMs / 60_000L).coerceAtLeast(0)}m"
        inMs < 24 * 3600_000L -> "in ${inMs / 3600_000L}h ${inMs / 60_000L % 60}m"
        else -> "in ${inMs / 86_400_000L}d"
    }
    var cardFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = padHoriz)
            .sportsCardFocus(cardFocused, EnktelBlue)
            .onFocusChanged { cardFocused = it.isFocused }.tapClick(onOpen),
        scale = noGrowScale(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EnktelSurfaceHigh.copy(0.5f),
            focusedContainerColor = EnktelBlue.copy(0.4f),
            focusedContentColor = Color.White,
            contentColor = Color.White,
        ),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ChannelLogo(ev.channel.logo, ev.channel.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SportBadge(ev.sport)
                    Badge(eta, EnktelBlue)
                }
                Spacer(Modifier.height(3.dp))
                Text(ev.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${ev.channel.name}  ·  ${TimeFormat.format("EEE d MMM · HH:mm", ev.startMs)}" +
                        feedsSuffix(alsoOn),
                    color = EnktelTextDim, fontSize = 11.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            FollowStar(following, onFollow)
            Column(horizontalAlignment = Alignment.End) {
                FocusIconButton(
                    if (reminded) Icons.Rounded.NotificationsOff else Icons.Rounded.NotificationsActive,
                    if (reminded) "Clear the reminder for this match" else "Remind me before kick-off",
                    accent = reminded,
                    onClick = onRemind,
                )
                Spacer(Modifier.height(4.dp))
                // Was a bare "●", which is a dot. Nothing on the card said it
                // meant "record this", and a dot beside a bell reads as a
                // second, unexplained notification setting.
                FocusIconButton(Icons.Rounded.FiberManualRecord, "Record this match", onClick = onSchedule)
            }
        }
    }
}

@Composable
internal fun FinishedEventCard(
    ev: SportsEvent,
    padHoriz: androidx.compose.ui.unit.Dp,
    onReplay: () -> Unit,
    /** How many other lines carry this same fixture. See SportsFixture. */
    alsoOn: Int = 0,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var cardFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onReplay,
        modifier = Modifier.fillMaxWidth().padding(horizontal = padHoriz)
            .sportsCardFocus(cardFocused, EnktelOk)
            .onFocusChanged { cardFocused = it.isFocused }.tapClick(onReplay),
        scale = noGrowScale(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EnktelSurface.copy(0.5f),
            focusedContainerColor = EnktelOk.copy(0.4f),
            focusedContentColor = Color.White,
            contentColor = Color.White,
        ),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ChannelLogo(ev.channel.logo, ev.channel.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Badge("FT", EnktelOk)
                    SportBadge(ev.sport, EnktelTextDim)
                    // An em-dash where a control belongs is not an answer, and
                    // neither is a 52dp column of text wrapped to "No /
                    // archive" beside the fixture's name. Said here, beside
                    // the badge it replaces, because "why is this card here if
                    // I cannot play it" was the actual question.
                    if (ev.channel.hasArchive) Badge("CATCH-UP", EnktelOk)
                    else Badge("NO ARCHIVE", EnktelTextDim)
                }
                Spacer(Modifier.height(3.dp))
                Text(ev.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${ev.channel.name} · ${TimeFormat.format("EEE d MMM HH:mm", ev.startMs)}" +
                        feedsSuffix(alsoOn),
                    color = EnktelTextDim, fontSize = 11.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                // Highlights link-out — YouTube search intent using the event
                // title + "highlights". Free, keyless, and opens the native
                // YouTube app on TV / phones when available.
                FocusIconButton(Icons.Rounded.Movie, "Search YouTube for highlights", label = "Highlights", onClick = {
                    val query = "${ev.title} highlights"
                    val webUri = ("https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode(query, "UTF-8")).toUri()
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, webUri)
                    runCatching { ctx.startActivity(intent) }
                })
            }
            if (ev.channel.hasArchive) {
                Icon(
                    Icons.Rounded.Replay, contentDescription = "Play from the start",
                    tint = EnktelOk, modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * "· on 2 more" — the other lines carrying this same fixture.
 *
 * The hub used to show one card per feed, so a playlist that carries a match
 * on Main Event, on the league channel and again in SD filled LIVE NOW with
 * three identical rows; on a real line, with SD/HD/FHD, a backup and one per
 * country prefix, the rail was five rows deep before it reached the second
 * match. They are folded into one card now, and this says so — both because a
 * silent fold looks like missing channels, and because when a feed stalls the
 * next thing anyone wants to know is whether there is another one.
 *
 * On a caption rather than in a badge beside LIVE and CRICKET, which is where
 * this started: "+3 FEEDS" is too long for a row that already holds two badges
 * on a 411dp handset, and it wrapped to "+3 FEED / S". The live card hangs it
 * off the timing line rather than the channel name for the same reason — the
 * timing line is full width, and the channel name shares its row with three
 * controls.
 *
 * Empty at zero rather than "on 0 more": one feed is the common case, and a
 * caption that usually ends in a zero is noise on every card.
 */
private fun feedsSuffix(alsoOn: Int): String =
    if (alsoOn <= 0) "" else "  ·  +$alsoOn more ${if (alsoOn == 1) "feed" else "feeds"}"

/**
 * Follow the teams in this fixture, from the card they are printed on.
 *
 * Following was reachable only through Settings → Sports → a text field you
 * had to spell the way the sports database does. The hub's own "my teams"
 * filter then only appears once something is followed, so the feature was
 * invisible from the one screen it exists for: you had to already know it was
 * there to find out it was there.
 *
 * Absent rather than disabled when the title has no sides in it ("Matchday
 * Live", "NFL RedZone"), because a control that does nothing is a worse answer
 * than no control — see [FinishedEventCard]'s "No archive".
 */
@Composable
private fun FollowStar(following: Boolean, onFollow: (() -> Unit)?) {
    if (onFollow == null) return
    FocusIconButton(
        if (following) Icons.Rounded.Star else Icons.Rounded.StarBorder,
        if (following) "Stop following these teams" else "Follow these teams",
        accent = following,
        onClick = onFollow,
    )
    Spacer(Modifier.width(8.dp))
}

/**
 * One reminder the viewer has set: the fixture, when it starts, and the way out.
 *
 * Tapping tunes to the channel, which is the point of having asked — by the
 * time the alert arrives, "where was it on again" is the whole question. The ✕
 * is here because it was nowhere: the bell wrote a row and scheduled a
 * notification, and there was no screen in the app that could list or undo it.
 */
@Composable
internal fun ReminderChip(
    reminder: tv.enktel.app.data.db.MatchReminder,
    now: Long,
    onTap: () -> Unit,
    onCancel: () -> Unit,
) {
    val inMs = reminder.startMs - now
    // One word. "ON NOW" wrapped to two lines inside the badge the moment the
    // chip was anything but its widest, which is a two-line badge above a
    // two-line title on a card meant to be read at a glance.
    val eta = when {
        inMs <= 0 -> "now"
        inMs < 60 * 60_000L -> "in ${(inMs / 60_000L).coerceAtLeast(1)}m"
        inMs < 24 * 3600_000L -> "in ${inMs / 3600_000L}h ${inMs / 60_000L % 60}m"
        else -> "in ${inMs / 86_400_000L}d"
    }
    val accent = if (inMs <= 0) EnktelLive else EnktelPurple
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onTap,
        modifier = Modifier
            // Fixed rather than a range: these sit in a rail, and a chip that
            // gives up width to its neighbour hyphenates the fixture it is
            // naming — "Arsen / al v …" — which is the one thing on it that
            // has to be readable.
            .width(238.dp)
            .onFocusChanged { focused = it.isFocused }
            .sportsCardFocus(focused, accent)
            .tapClick(onTap),
        scale = noGrowScale(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EnktelSurfaceHigh.copy(0.5f),
            focusedContainerColor = accent.copy(0.32f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(0.35f)),
                shape = RoundedCornerShape(12.dp),
            ),
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White),
                shape = RoundedCornerShape(12.dp),
            ),
        ),
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Badge(eta.uppercase(), accent)
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    reminder.title, color = Color.White, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${TimeFormat.format("EEE HH:mm", reminder.startMs)}  ·  ${reminder.channelName}",
                    color = EnktelTextDim, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            FocusIconButton(
                Icons.Rounded.Close, "Clear this reminder",
                onClick = onCancel,
            )
        }
    }
}

/**
 * Small team crest. Silently absent when the API has no badge for a side.
 *
 * `ContentScale.Fit`, because crests are not square. The default is
 * [ContentScale.Fit] for `AsyncImage` only when a painter reports its size the
 * way Compose expects; stated here so a tall club badge is letterboxed into
 * the 18dp box rather than squashed to fit it.
 */
@Composable
private fun TeamCrest(url: String) {
    if (url.isBlank()) return
    coil3.compose.AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(18.dp).padding(end = 5.dp),
    )
}

/**
 * Compact live-score chip for the Sports ticker.
 *
 * Renders three distinct states — in play, not yet kicked off, finished — so
 * the live ones are findable at a glance rather than uniform with the rest.
 */
@Composable
internal fun LiveScoreChip(
    score: tv.enktel.app.data.repo.LiveScore,
    onTap: () -> Unit,
    onStats: (() -> Unit)? = null,
) {
    // The strip carries three different things — matches in play, kick-offs
    // still to come, and finished results. Painting them identically made the
    // live ones impossible to pick out, which defeats the point of a live
    // ticker. Colour and the pulse are reserved for genuinely in-play games.
    val accent = when {
        score.inPlay -> EnktelOk
        score.notStarted -> EnktelBlue
        else -> EnktelTextDim
    }

    // Slow breathing pulse on the live dot. Only for in-play: a blinking dot
    // on a finished match is a lie.
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "livePulse")
        .animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable<Float>(
                androidx.compose.animation.core.tween(900),
                androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "livePulseAlpha",
        )

    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onTap,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .sportsCardFocus(focused, accent)
            .tapClick(onTap),
        scale = noGrowScale(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EnktelSurface.copy(0.7f),
            focusedContainerColor = accent.copy(0.28f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(0.5f)),
                shape = RoundedCornerShape(24.dp),
            ),
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White),
                shape = RoundedCornerShape(24.dp),
            ),
        ),
    ) {
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(accent.copy(alpha = if (score.inPlay) pulse else 1f)),
        )
        Spacer(Modifier.width(8.dp))
        TeamCrest(score.homeBadge)
        Text(
            score.home, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 90.dp),
        )
        Spacer(Modifier.width(8.dp))
        if (score.notStarted) {
            // No score exists yet; "– – –" reads as a 0-0 draw, which is wrong.
            Text("vs", color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        } else {
            Text(score.homeScore, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text(" – ", color = EnktelTextDim, fontSize = 12.sp)
            Text(score.awayScore, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(8.dp))
        TeamCrest(score.awayBadge)
        Text(
            score.away, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 90.dp),
        )
        if (score.minute.isNotBlank()) {
            Spacer(Modifier.width(10.dp))
            if (score.notStarted) {
                Icon(
                    Icons.Rounded.Schedule, contentDescription = "Kicks off at",
                    tint = accent, modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                score.minute,
                color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            )
        } else if (score.finished) {
            Spacer(Modifier.width(10.dp))
            Text("FT", color = EnktelTextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        if (onStats != null) {
            Spacer(Modifier.width(10.dp))
            FocusIconButton(Icons.Rounded.Insights, "Match statistics", onClick = onStats)
        }
    }
    }
}

/**
 * One fixture from the official schedule. Deliberately compact — this row is a
 * "what's on today" strip, and the detail lives one tap away in the Match
 * Centre alongside the broadcaster list.
 */
@Composable
internal fun FixtureChip(
    fixture: tv.enktel.app.data.repo.LiveScore,
    /** Officially published broadcasters, best first. May be empty. */
    broadcasters: List<String> = emptyList(),
    /** The line on this playlist that carries it, when one was matched. */
    tuneTo: tv.enktel.app.data.db.Channel? = null,
    onTap: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onTap,
        modifier = Modifier
            .widthIn(min = 150.dp, max = 230.dp)
            .onFocusChanged { focused = it.isFocused }
            .sportsCardFocus(focused, EnktelBlue)
            .tapClick(onTap),
        scale = noGrowScale(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EnktelSurfaceHigh.copy(0.5f),
            focusedContainerColor = EnktelBlue.copy(0.32f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(1.dp, EnktelBlue.copy(0.3f)),
                shape = RoundedCornerShape(12.dp),
            ),
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White),
                shape = RoundedCornerShape(12.dp),
            ),
        ),
    ) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (fixture.minute.isNotBlank()) Badge(fixture.minute, EnktelBlue)
            if (fixture.sport.isNotBlank()) SportBadge(fixture.sport, EnktelTextDim, compact = true)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (fixture.away.isBlank()) fixture.home else "${fixture.home} v ${fixture.away}",
            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (fixture.league.isNotBlank()) {
            Text(
                fixture.league, color = EnktelTextDim, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        // Where it is on. The published broadcaster first, because that is the
        // fact — then the line on this playlist that carries it, which is what
        // the user can actually press. Without this the hub named a fixture and
        // left finding it as an exercise.
        if (broadcasters.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Rounded.Podcasts, contentDescription = "Broadcast on",
                    tint = EnktelTextDim,
                    modifier = Modifier.size(11.dp).padding(top = 2.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    broadcasters.take(2).joinToString(" · "),
                    color = EnktelTextDim, fontSize = 10.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (tuneTo != null) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.PlayArrow, contentDescription = null,
                    tint = EnktelOk, modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    tuneTo.name,
                    color = EnktelOk, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        } else if (broadcasters.isNotEmpty()) {
            // Said plainly rather than left blank. A broadcaster your line does
            // not carry is a real answer — it stops the search before it starts.
            Spacer(Modifier.height(6.dp))
            Text(
                "Not matched on your playlist",
                color = EnktelTextDim, fontSize = 10.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
    }
}

/**
 * A published highlights package: thumbnail, fixture, and a play affordance.
 *
 * Two things were wrong with the old one. It was a `Column` with `tapClick`,
 * so on a television it could not be focused and the whole rail was
 * unreachable by remote — see [FixtureChip]. And the play marker was a white
 * "▶" drawn straight onto the thumbnail: highlight stills are bright, mostly
 * of a floodlit pitch, and a white glyph on a white background is not a
 * marker. It now sits in a filled disc over a scrim that also rescues the
 * title from whatever the image is doing behind it.
 */
@Composable
internal fun HighlightCard(clip: tv.enktel.app.data.repo.HighlightClip, onPlay: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onPlay,
        modifier = Modifier
            .width(220.dp)
            .onFocusChanged { focused = it.isFocused }
            .sportsCardFocus(focused, EnktelOk)
            .tapClick(onPlay),
        scale = noGrowScale(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = EnktelSurface.copy(0.6f),
            focusedContainerColor = EnktelOk.copy(0.26f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White),
                shape = RoundedCornerShape(12.dp),
            ),
        ),
    ) {
    Column {
        Box(
            Modifier.fillMaxWidth().height(124.dp).background(EnktelSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (clip.thumb.isNotBlank()) {
                AsyncImage(
                    model = clip.thumb, contentDescription = clip.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Bottom-weighted scrim. Enough to seat the play disc and keep the
            // sport badge legible; not so much that it dims the still.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Black.copy(alpha = 0.15f),
                        1f to Color.Black.copy(alpha = 0.65f),
                    ),
                ),
            )
            // percent = 50 rather than half the size in dp: the radius scale
            // is 4/8/12/16/24, and a circle is a pill, not a sixth radius.
            // DesignTokensTest enforces exactly this.
            val disc = RoundedCornerShape(percent = 50)
            Box(
                Modifier
                    .size(42.dp)
                    .clip(disc)
                    .background(Color.Black.copy(alpha = if (focused) 0.30f else 0.55f))
                    .border(
                        1.5.dp,
                        if (focused) EnktelOk else Color.White.copy(alpha = 0.85f),
                        disc,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow, contentDescription = null,
                    tint = if (focused) EnktelOk else Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            if (clip.sport.isNotBlank()) {
                Box(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                    Badge(clip.sport, EnktelOk)
                }
            }
        }
        Column(Modifier.padding(10.dp)) {
            Text(
                clip.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            // The sport moved onto the thumbnail, so this is the league alone
            // rather than "Premier League · Soccer" saying it twice.
            if (clip.league.isNotBlank()) {
                Text(
                    clip.league, color = EnktelTextDim, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    }
}

/**
 * The icon for a sport tag.
 *
 * Paired with [sportsDbName] deliberately: both map the same internal tags,
 * and a sport that gains a tag needs a row in each. A tag with no icon of its
 * own gets the generic whistle rather than nothing — a badge that sometimes
 * has a picture and sometimes does not is worse than one that always does.
 */
private fun sportIcon(sport: String): ImageVector = when (sport) {
    "Football" -> Icons.Rounded.SportsSoccer
    "American Football" -> Icons.Rounded.SportsFootball
    "Basketball" -> Icons.Rounded.SportsBasketball
    "Baseball" -> Icons.Rounded.SportsBaseball
    "Hockey" -> Icons.Rounded.SportsHockey
    "MMA/Boxing", "Combat" -> Icons.Rounded.SportsMma
    "Tennis" -> Icons.Rounded.SportsTennis
    "Cricket" -> Icons.Rounded.SportsCricket
    "Motor Racing" -> Icons.Rounded.SportsMotorsports
    "Cycling" -> Icons.AutoMirrored.Rounded.DirectionsBike
    "Golf" -> Icons.Rounded.SportsGolf
    "Rugby" -> Icons.Rounded.SportsRugby
    "Volleyball" -> Icons.Rounded.SportsVolleyball
    "Handball" -> Icons.Rounded.SportsHandball
    "Esports" -> Icons.Rounded.SportsEsports
    "Athletics", "Olympics" -> Icons.Rounded.EmojiEvents
    else -> Icons.Rounded.Sports
}

/**
 * A [Badge] with the sport's icon in front of its name.
 *
 * Sport is the one field on these cards that is scanned rather than read —
 * someone looking for the football is not reading forty titles, they are
 * looking for the football. A word in 9sp caps has to be read; a shape does
 * not, which is what makes a rail of mixed sports sortable at a glance.
 */
@Composable
internal fun SportBadge(
    sport: String,
    color: Color = EnktelBlue,
    /**
     * Drop the word and keep the shape.
     *
     * For the schedule chips, which are 150dp at their narrowest and already
     * carrying a kick-off time on the same line: "BASKETBALL" spelled out
     * there truncated to "BASKE…", which is neither the word nor a shape. The
     * icon alone still answers "which sport", and [contentDescription] keeps
     * the name for anyone who cannot see it.
     */
    compact: Boolean = false,
) {
    Row(
        Modifier
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = if (compact) 5.dp else 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            sportIcon(sport),
            contentDescription = if (compact) sport else null,
            tint = color,
            modifier = Modifier.size(11.dp),
        )
        if (!compact) {
            Text(
                sport.uppercase(), color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Maps our internal sport tags onto the names TheSportsDB uses in its `s=`
 * query parameter. Anything we don't have a mapping for returns blank, which
 * the API reads as "all sports" — a wider result set is a better failure than
 * an empty one.
 */
private fun sportsDbName(sport: String): String = when (sport) {
    "Football" -> "Soccer"
    "American Football" -> "American Football"
    "Basketball" -> "Basketball"
    "Baseball" -> "Baseball"
    "Hockey" -> "Ice Hockey"
    "MMA/Boxing", "Combat" -> "Fighting"
    "Tennis" -> "Tennis"
    "Cricket" -> "Cricket"
    "Motor Racing" -> "Motorsport"
    "Cycling" -> "Cycling"
    "Golf" -> "Golf"
    "Rugby" -> "Rugby"
    "Volleyball" -> "Volleyball"
    "Handball" -> "Handball"
    "Esports" -> "ESports"
    else -> ""
}

/**
 * A channel's logo in a fixed square, or its initials when it has none.
 *
 * The image used to fill the box edge to edge with no content scale, so a wide
 * broadcaster logo — which is most of them — was drawn stretched to a square.
 * Fitted and inset instead: the logo keeps its proportions, and the 5dp of
 * padding stops a light logo from touching the tile's rounded corner, which
 * read as a rendering fault.
 */
@Composable
internal fun ChannelLogo(url: String, fallback: String) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(EnktelSurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(5.dp),
            )
        } else {
            Text(fallback.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

private fun hhmm(ms: Long): String = TimeFormat.format("HH:mm", ms)
