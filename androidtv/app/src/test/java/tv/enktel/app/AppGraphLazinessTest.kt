package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What `Application.onCreate` is allowed to build.
 *
 * [AppGraph] was eighteen eager `val`s, so the database handle, every
 * repository, the download hub and the playback session were all constructed
 * before the first frame — on the main thread, on a Fire TV Stick, whether or
 * not the viewer was ever going to open a film. `DownloadHub` was the worst:
 * its `defaultRoot` runs `getExternalFilesDir(...).mkdirs()`, which is disk
 * I/O, at construction.
 *
 * The failure mode of losing that is invisible. Nothing breaks when a `by
 * lazy` goes back to a plain `val` — start-up just gets slower again, one
 * property at a time, and nobody notices until someone with a slow stick
 * says the app takes a while to open. So the split is pinned here.
 *
 * Read off the synthetic `name$delegate` fields Kotlin emits for a lazy
 * property. That is an implementation detail of the compiler, but it is a
 * stable one, and it is the only way to assert "this was not built" without
 * an instrumented run.
 */
class AppGraphLazinessTest {

    /**
     * Built eagerly, on purpose: `init` uses each of these directly, so
     * deferring them would only move the work a line later.
     */
    private val expectedEager = setOf("settings", "http", "diagHttp", "appScope")

    /** Deferred until a screen asks. Everything expensive lives here. */
    private val expectedLazy = setOf(
        "db", "xtream", "playlists", "content", "epg", "sports", "watchlist",
        "recommendations", "scores", "trailers", "feed", "downloads", "discord",
        "playback",
    )

    private fun lazyPropertyNames(): Set<String> =
        AppGraph::class.java.declaredFields
            .filter { it.type == Lazy::class.java && it.name.endsWith("\$delegate") }
            .map { it.name.removeSuffix("\$delegate") }
            .toSet()

    @Test
    fun `the expensive members are all deferred`() {
        val actual = lazyPropertyNames()
        val notLazy = expectedLazy - actual
        assertTrue(
            "these must stay `by lazy` or they are built before the first frame: $notLazy",
            notLazy.isEmpty(),
        )
    }

    @Test
    fun `nothing eager has quietly joined the graph`() {
        // Any *new* member is either lazy, or one of the four that init needs,
        // or a mistake. Failing on the third case is the point: this test is
        // here to make "I added one more eager val" a conversation.
        val lazyNames = lazyPropertyNames()
        val eager = AppGraph::class.java.declaredFields
            .asSequence()
            .filterNot { it.isSynthetic }
            .map { it.name }
            .filterNot { it.endsWith("\$delegate") }
            // `${'$'}stable` is the Compose compiler's stability marker, a static
            // Int on every class it touches. Not a member of the graph.
            .filterNot { it.startsWith("\$") }
            .filterNot { it in lazyNames }
            // Private @Volatile snapshots are plain fields, not part of the
            // object graph, and cost nothing to initialise.
            .filterNot { it.endsWith("Snapshot") || it.endsWith("Override") || it == "profileUserAgent" }
            .toSet()
        assertEquals(
            "unexpected eager member(s) — make them `by lazy`, or add them to " +
                "expectedEager here with a note saying why init needs them",
            expectedEager,
            eager,
        )
    }

    @Test
    fun `playback is not built at start-up`() {
        // Called out on its own because it is the one that matters most and
        // the one most likely to be "simplified" back: PlaybackSession owns
        // the process's ExoPlayer.
        assertTrue("playback must be lazy", "playback" in lazyPropertyNames())
    }
}
