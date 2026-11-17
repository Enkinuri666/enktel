package tv.enktel.app.ui.discover

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.combine
import tv.enktel.app.AppGraph
import tv.enktel.app.data.db.Movie
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.db.Series
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.GlassChip
import tv.enktel.app.ui.components.PosterCard
import tv.enktel.app.ui.components.SectionHeader
import tv.enktel.app.ui.components.StreamingBadges
import tv.enktel.app.ui.components.StreamingPlatform
import tv.enktel.app.ui.components.rememberScreenShape
import tv.enktel.app.ui.theme.EnktelBg
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelBrandGradient
import tv.enktel.app.ui.theme.EnktelRadius
import tv.enktel.app.ui.theme.EnktelText
import tv.enktel.app.ui.theme.EnktelTextDim
import tv.enktel.app.ui.theme.EnktelTextOnArt
import tv.enktel.app.ui.theme.EnktelType

/**
 * Discover — one place to find something to watch across every streaming
 * platform your line carries.
 *
 * ## The reason this screen exists
 *
 * A viewer with a full Xtream line has, in effect, every streaming service in
 * one app. The problem is that the browsing UIs those services taught people
 * are per-service: Netflix shows you what's new on Netflix, Disney+ shows you
 * what's new on Disney+, and so on. Here, the catalogue is one bucket called
 * "Movies" and another called "Series", organised by the provider's own
 * category labels (which are ad hoc, uneven, and name the platform at best in
 * passing). Someone who used to sit down and scroll Netflix for twenty minutes
 * before giving up now has no idea where to start.
 *
 * Discover restacks the same catalogue around the services people already
 * think in. The screen detects the platform from each title's category label
 * via the shared StreamingBadges registry (forty-plus brands, same colours and
 * wordmarks the badges elsewhere already use), groups titles under their
 * platform, and sorts each platform's row newest-first. A viewer sees
 * "Netflix: here's what's new, Disney+: here's what's new" the way they would
 * on the services' own apps — but all at once, and without closing one app to
 * open another.
 *
 * ## Shape of the screen
 *
 * - **New this week** rail across every platform, drawn from firstSeenAt in
 *   the last seven days. The viewer's own newness, not the panel's.
 * - **Mood chips** — a quiet row of single-word genre filters that narrows
 *   every rail below without moving anyone off the screen.
 * - **Surprise me** — one well-rated title, chosen at random, opens its
 *   details. For the moments when the choice *is* the problem.
 * - **Per-platform rows**, in order of how much of that platform the viewer's
 *   line carries. Each row leads with a hero brand tile and a rail of the
 *   newest titles from that service. Platforms this line does not carry are
 *   simply absent.
 *
 * ## Self-updating
 *
 * The screen reads catalogue Flows directly, so it recomposes the moment a
 * sync finishes. The v1.82 auto-refresh (re-sync the playlist on every open
 * with a 30-minute floor) already keeps the data under it current — nothing
 * extra polls, nothing new runs in the background, and nothing has to be
 * typed to feel refreshed. The screen also recomposes on focus-return, so a
 * sync that completes while this screen is up shows up without a tap.
 *
 * ## Why not actual logo files
 *
 * The brand tiles render each service as its *wordmark on its brand colour*
 * — NETFLIX in red, DISNEY+ in blue, PRIME in cyan — at hero size. Shipping
 * copyrighted logo files inside the APK is a distribution risk nobody here has
 * cleared, and the wordmarks on brand colours are what every TV launcher from
 * Fire TV to Apple TV uses when it does not have its own licensed art. They
 * read unmistakably as the brand from across a room; the registry is central
 * so swapping to real logos later is one drawable per brand.
 */
@Composable
fun DiscoverScreen(graph: AppGraph, nav: NavHostController) {
    val profile by produceState<Profile?>(initialValue = null) {
        value = graph.playlists.activeProfile()
    }
    val p = profile ?: return
    val scope = rememberCoroutineScope()

    val moviesFlow = remember(p.id) { graph.content.movies(p.id) }
    val seriesFlow = remember(p.id) { graph.content.series(p.id) }
    val vodCatsFlow = remember(p.id) { graph.content.categories(p.id, "vod") }
    val seriesCatsFlow = remember(p.id) { graph.content.categories(p.id, "series") }

    // Single derived flow so one combine() collects everything and the screen
    // recomputes once per catalogue tick rather than four times. No background
    // thread work here beyond what the Flows themselves run.
    val plan by remember(p.id) {
        combine(moviesFlow, seriesFlow, vodCatsFlow, seriesCatsFlow) { mv, sr, vc, sc ->
            DiscoverPlan.from(mv, sr, vc, sc, nowMs = System.currentTimeMillis())
        }
    }.collectAsStateWithLifecycle(initialValue = DiscoverPlan.EMPTY)

    var mood: String by rememberSaveable(stateSaver = MOOD_SAVER) { mutableStateOf(Mood.ALL) }
    val filtered = remember(plan, mood) { plan.filteredBy(mood) }

    val shape = rememberScreenShape()
    val padHoriz = shape.padH
    val isMobile = tv.enktel.app.BuildConfig.FLAVOR == "mobile"

    Box(Modifier.fillMaxSize().background(EnktelBg)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 20.dp, bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                DiscoverHeader(
                    isMobile = isMobile,
                    padHoriz = padHoriz,
                    platformCount = plan.platforms.size,
                    titleCount = plan.totalTitles,
                    onSurprise = {
                        plan.surpriseMe()?.let { pick ->
                            nav.navigate(if (pick.isSeries) "seriesDetails/${pick.key}" else "movie/${pick.key}")
                        }
                    },
                )
            }

            // Mood chip row — single-word genre filter. "All" clears it.
            item {
                Spacer(Modifier.height(4.dp))
                SectionHeader(
                    title = "What are you in the mood for?",
                    modifier = Modifier.fillMaxWidth().padding(start = padHoriz, end = padHoriz, bottom = 2.dp),
                )
            }
            item {
                LazyRow(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = padHoriz),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(Mood.ALL_MOODS, key = { it }) { m ->
                        GlassChip(
                            text = m,
                            selected = m == mood,
                            onClick = { mood = m },
                        )
                    }
                }
            }

            // "New this week" — cross-platform freshness, so the viewer sees
            // what the catalogue added in the last week before diving into any
            // particular service.
            if (filtered.freshThisWeek.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "New this week",
                        note = "across every service",
                        icon = Icons.Rounded.Whatshot,
                        modifier = Modifier.fillMaxWidth().padding(start = padHoriz, end = padHoriz, top = 10.dp, bottom = 4.dp),
                    )
                }
                item {
                    LazyRow(
                        Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = padHoriz),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(filtered.freshThisWeek, key = { it.key }) { pick ->
                            PosterCard(
                                title = pick.name, imageUrl = pick.poster,
                                tmdbId = pick.tmdbId, isSeries = pick.isSeries,
                                onClick = {
                                    nav.navigate(if (pick.isSeries) "seriesDetails/${pick.key}" else "movie/${pick.key}")
                                },
                            )
                        }
                    }
                }
            }

            // One row per platform the line actually carries, in order of how
            // many titles the user has from that service. A platform with
            // nothing in it after the mood filter is skipped; the row would
            // otherwise be a brand tile next to nothing.
            filtered.platforms.forEach { row ->
                item(key = "head-${row.id}") {
                    PlatformSectionHeader(
                        platform = row.brand,
                        count = row.titles.size,
                        padHoriz = padHoriz,
                    )
                }
                item(key = "rail-${row.id}") {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.width(padHoriz))
                        PlatformHero(row.brand, modifier = Modifier.size(width = if (isMobile) 180.dp else 240.dp, height = 158.dp))
                        Spacer(Modifier.width(12.dp))
                        LazyRow(
                            Modifier.weight(1f),
                            contentPadding = PaddingValues(end = padHoriz),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(row.titles, key = { it.key }) { pick ->
                                PosterCard(
                                    title = pick.name, imageUrl = pick.poster,
                                    tmdbId = pick.tmdbId, isSeries = pick.isSeries,
                                    onClick = {
                                        nav.navigate(if (pick.isSeries) "seriesDetails/${pick.key}" else "movie/${pick.key}")
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Empty state: either the catalogue hasn't loaded yet, or the mood
            // filter narrowed the whole screen to nothing. Give the viewer the
            // button to put it back rather than leaving them staring at a
            // blank page.
            if (filtered.platforms.isEmpty() && filtered.freshThisWeek.isEmpty()) {
                item {
                    EmptyState(
                        padHoriz = padHoriz,
                        hasAnyPlan = plan.platforms.isNotEmpty(),
                        mood = mood,
                        onClearMood = { mood = Mood.ALL },
                    )
                }
            }
        }
    }
}

/* ── Header ─────────────────────────────────────────────────────────── */

@Composable
private fun DiscoverHeader(
    isMobile: Boolean,
    padHoriz: androidx.compose.ui.unit.Dp,
    platformCount: Int,
    titleCount: Int,
    onSurprise: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = padHoriz),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Discover",
                color = EnktelText,
                fontSize = if (isMobile) 24.sp else 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.4).sp,
            )
            if (platformCount > 0) {
                Text(
                    "Across $platformCount services  ·  $titleCount titles on your line",
                    color = EnktelTextDim, style = EnktelType.subtitle,
                )
            } else {
                Text(
                    "Finding something to watch, in one place.",
                    color = EnktelTextDim, style = EnktelType.subtitle,
                )
            }
        }
        if (titleCount > 0) {
            FocusButton(
                "Surprise me",
                icon = Icons.Rounded.Shuffle,
                accent = true,
                onClick = onSurprise,
            )
        }
    }
}

/* ── The hero brand tile — the "logo" ──────────────────────────────── */

/**
 * The hero brand tile for a platform row. A brand-coloured plate with the
 * wordmark at hero size and a diagonal gradient so it reads as a tile and not
 * a flat block. Fixed size so a row of them stays aligned across platforms —
 * Netflix's N and Prime Video's wordmark sit the same amount off the top edge.
 */
@Composable
internal fun PlatformHero(platform: StreamingPlatform, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(EnktelRadius.shapeLg)
            .background(platform.bg)
            .background(
                // Subtle wash so the tile has depth — darker top-left, lighter
                // bottom-right. Alpha is small on purpose: the brand colour
                // has to stay true.
                Brush.linearGradient(
                    0f to Color.Black.copy(alpha = 0.18f),
                    0.5f to Color.Transparent,
                    1f to Color.White.copy(alpha = 0.06f),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            platform.label,
            color = platform.fg,
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.5).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
    }
}

@Composable
private fun PlatformSectionHeader(
    platform: StreamingPlatform,
    count: Int,
    padHoriz: androidx.compose.ui.unit.Dp,
) {
    // The section bar wears the platform's own colour, so a row of Netflix
    // titles is unmistakably a Netflix row even before the eye reaches the
    // brand tile on its left.
    SectionHeader(
        title = platform.label,
        count = count,
        accent = platform.bg,
        modifier = Modifier.fillMaxWidth().padding(start = padHoriz, end = padHoriz, top = 20.dp, bottom = 4.dp),
    )
}

/* ── Empty state ───────────────────────────────────────────────────── */

@Composable
private fun EmptyState(
    padHoriz: androidx.compose.ui.unit.Dp,
    hasAnyPlan: Boolean,
    mood: String,
    onClearMood: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = padHoriz, vertical = 44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (!hasAnyPlan) {
                Text(
                    "Your catalogue is loading…",
                    color = EnktelTextOnArt, style = EnktelType.title,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Discover fills in the moment your playlist finishes syncing.",
                    color = EnktelTextDim, style = EnktelType.body,
                )
            } else {
                Text(
                    "Nothing in the mood for $mood right now.",
                    color = EnktelTextOnArt, style = EnktelType.title,
                )
                Spacer(Modifier.height(10.dp))
                FocusButton("Show everything", accent = true, onClick = onClearMood)
            }
        }
    }
}

/* ── Moods (plain genre buckets, matched against movie.genre/tags) ──── */

internal object Mood {
    const val ALL = "All"
    val ALL_MOODS = listOf(ALL, "Action", "Drama", "Comedy", "Sci-Fi", "Kids", "Documentary", "Horror", "Romance", "Thriller")
}

private val MOOD_SAVER = listSaver<String, String>(save = { listOf(it) }, restore = { it.first() })

/* ── The derived data ──────────────────────────────────────────────── */

/**
 * One tile in the Discover feed — a movie or an episode-root series,
 * normalised so the UI treats them the same.
 */
internal data class DiscoverPick(
    val key: String,
    val name: String,
    val poster: String,
    val tmdbId: Long,
    val rating: Double,
    val genre: String,
    val firstSeenAt: Long,
    val isSeries: Boolean,
)

internal data class PlatformRow(
    val id: String,
    val brand: StreamingPlatform,
    val titles: List<DiscoverPick>,
)

internal data class DiscoverPlan(
    val platforms: List<PlatformRow>,
    val freshThisWeek: List<DiscoverPick>,
    val allPicks: List<DiscoverPick>,
) {
    val totalTitles: Int get() = allPicks.size

    /** Randomly pick a well-rated title to surface, or null if the plan is empty. */
    fun surpriseMe(): DiscoverPick? {
        if (allPicks.isEmpty()) return null
        // Prefer well-rated titles when we have enough of them, so Surprise Me
        // doesn't hand out the thing you skipped in 2014. Fall back to the
        // whole set when the rated pool is too thin.
        val rated = allPicks.filter { it.rating >= 7.0 }
        val pool = if (rated.size >= 8) rated else allPicks
        return pool.random()
    }

    fun filteredBy(mood: String): DiscoverPlan {
        if (mood == Mood.ALL) return this
        val m = mood.lowercase()
        fun keep(p: DiscoverPick) = p.genre.lowercase().contains(m) ||
            // "Sci-Fi" / "sci fi" / "science fiction" all land here.
            (m == "sci-fi" && (p.genre.contains("sci", ignoreCase = true) || p.genre.contains("science fiction", ignoreCase = true)))
        val fresh = freshThisWeek.filter(::keep)
        val plats = platforms.mapNotNull { row ->
            val kept = row.titles.filter(::keep)
            if (kept.isEmpty()) null else row.copy(titles = kept)
        }
        val all = allPicks.filter(::keep)
        return DiscoverPlan(plats, fresh, all)
    }

    companion object {
        val EMPTY = DiscoverPlan(emptyList(), emptyList(), emptyList())

        /** Maximum titles to carry per platform row — the rail scrolls, but the
         *  work of composing it should not grow unbounded on a catalogue with
         *  tens of thousands of films. */
        private const val MAX_PER_PLATFORM = 24

        /** Window for "new this week", ms. */
        private const val FRESH_MS = 7L * 24L * 60L * 60L * 1000L

        fun from(
            movies: List<Movie>,
            series: List<Series>,
            vodCats: List<tv.enktel.app.data.db.Category>,
            seriesCats: List<tv.enktel.app.data.db.Category>,
            nowMs: Long,
        ): DiscoverPlan {
            if (movies.isEmpty() && series.isEmpty()) return EMPTY
            val vodName = vodCats.associate { it.categoryId to it.name }
            val serName = seriesCats.associate { it.categoryId to it.name }

            // Map each title to its platform (if any), via the shared registry.
            data class Tagged(val pick: DiscoverPick, val platform: StreamingPlatform?)
            val tagged = ArrayList<Tagged>(movies.size + series.size)
            for (m in movies) {
                val catName = vodName[m.categoryId].orEmpty()
                val pick = DiscoverPick(
                    key = m.key, name = m.name, poster = m.poster,
                    tmdbId = m.tmdbId, rating = m.rating, genre = m.genre,
                    firstSeenAt = if (m.firstSeenAt > 0) m.firstSeenAt else m.addedAt * 1000L,
                    isSeries = false,
                )
                tagged += Tagged(pick, StreamingBadges.detect(catName))
            }
            for (s in series) {
                val catName = serName[s.categoryId].orEmpty()
                val pick = DiscoverPick(
                    key = s.key, name = s.name, poster = s.poster,
                    tmdbId = s.tmdbId, rating = s.rating, genre = s.genre,
                    firstSeenAt = if (s.firstSeenAt > 0) s.firstSeenAt else 0L,
                    isSeries = true,
                )
                tagged += Tagged(pick, StreamingBadges.detect(catName))
            }

            // Group by platform id (= brand label), then order platforms by
            // how much of that platform the viewer's line carries: a Netflix-
            // heavy line opens on Netflix, a Disney-heavy line on Disney+.
            val byPlatform = tagged
                .filter { it.platform != null }
                .groupBy { it.platform!!.label }
            val platforms = byPlatform.entries
                .map { (_, items) ->
                    val brand = items.first().platform!!
                    PlatformRow(
                        id = brand.label,
                        brand = brand,
                        titles = items.map { it.pick }
                            .sortedByDescending { it.firstSeenAt }
                            .take(MAX_PER_PLATFORM),
                    )
                }
                .sortedByDescending { it.titles.size }

            val fresh = tagged
                .map { it.pick }
                .filter { it.firstSeenAt > 0 && nowMs - it.firstSeenAt <= FRESH_MS }
                .sortedByDescending { it.firstSeenAt }
                .take(20)

            return DiscoverPlan(
                platforms = platforms,
                freshThisWeek = fresh,
                allPicks = tagged.map { it.pick },
            )
        }
    }
}
