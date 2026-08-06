package tv.enktel.app.data.catchup

import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.diag.CatchupScheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Turns "play this programme from Tuesday" into URLs a panel will answer.
 *
 * The app already knew how to *recognise* every catch-up scheme in the wild —
 * [tv.enktel.app.data.diag.M3uAttrs] parses `catchup`, `catchup-source` and
 * `catchup-days`, and Panel Doctor classifies what it finds. None of that
 * reached playback. Catch-Up built one URL, in one shape, for Xtream profiles
 * only:
 *
 *     {server}/timeshift/{user}/{pass}/{minutes}/{Y-m-d:H-M}/{id}.ts
 *
 * A panel that serves the same archive from `streaming/timeshift.php` got a
 * player error with no explanation, and an M3U line carrying a perfectly good
 * `catchup-source` template was refused before it was tried — every catch-up
 * call site was gated on `kind == "xtream"`. Knowing the answer in the
 * diagnostics screen and not using it in the player is the same bug this app
 * has now hit in four different places.
 *
 * So: one ordered candidate list, same contract as
 * [tv.enktel.app.data.xtream.StreamUrlResolver] — first entry is tried first,
 * caller walks the list until one answers.
 */
object CatchupUrls {

    /** Xtream's start format, and it is not negotiable — panels parse it strictly. */
    private const val XTREAM_START = "yyyy-MM-dd:HH-mm"

    /**
     * Ordered URL candidates for the archived programme running
     * [startMs]..[endMs].
     *
     * Empty when the channel advertises no archive, or when nothing about the
     * profile lets a URL be built — an empty list is the caller's cue to say so
     * rather than to navigate to a player that will fail silently.
     *
     * @param tz the timezone the *panel* thinks in. Xtream timeshift paths
     *   carry a wall-clock time, not an instant, so the two ends have to agree
     *   about what "14:00" means. Device-local is the right default (panels
     *   normally publish EPG in the line's own timezone and most users watch
     *   from there), but a user abroad needs the panel's.
     */
    fun candidates(
        p: Profile,
        ch: Channel,
        startMs: Long,
        endMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        tz: TimeZone = TimeZone.getDefault(),
    ): List<String> {
        if (endMs <= startMs) return emptyList()
        val durationSec = (endMs - startMs) / 1000
        val durationMin = (durationSec / 60).coerceAtLeast(1)
        val startSec = startMs / 1000
        val endSec = endMs / 1000
        val nowSec = nowMs / 1000

        val out = mutableListOf<String>()

        // 1. An explicit template from the playlist always wins. The provider
        //    wrote it; guessing over the top of it is how catch-up ends up
        //    working on one line and 404ing on another that looks identical.
        if (ch.catchupSource.startsWith("http", ignoreCase = true)) {
            out += fillTemplate(
                template = ch.catchupSource,
                startSec = startSec, endSec = endSec, nowSec = nowSec,
                durationSec = durationSec, tz = tz,
            )
        }

        val scheme = schemeOf(p, ch)
        val live = ch.url.trim()

        when (scheme) {
            CatchupScheme.XTREAM_TIMESHIFT, CatchupScheme.DEFAULT, CatchupScheme.UNKNOWN ->
                out += xtreamShapes(p, ch, startMs, durationMin, tz)

            CatchupScheme.APPEND, CatchupScheme.SHIFT -> {
                if (live.isNotBlank()) {
                    // The two spellings providers actually use. `utc`/`lutc` is
                    // the near-universal pair; `timeshift`/`archive` shows up on
                    // a minority of nginx-based setups.
                    out += appendQuery(live, "utc=$startSec&lutc=$nowSec")
                    out += appendQuery(live, "timeshift=$startSec&timenow=$nowSec")
                }
                out += xtreamShapes(p, ch, startMs, durationMin, tz)
            }

            CatchupScheme.FLUSSONIC -> {
                if (live.isNotBlank()) out += flussonicShapes(live, startSec, durationSec)
                out += xtreamShapes(p, ch, startMs, durationMin, tz)
            }
        }

        return out.filter { it.isNotBlank() }.distinct()
    }

    /**
     * What kind of catch-up this channel has, from what the playlist declared
     * and what the profile is.
     *
     * Deliberately not gated on `kind == "xtream"`: an M3U line that declares
     * `catchup="append"` has working catch-up, and refusing to try it because
     * the profile came from a playlist file is a decision about paperwork
     * rather than about the stream.
     */
    fun schemeOf(p: Profile, ch: Channel): CatchupScheme = when {
        ch.catchupType.equals("flussonic", true) -> CatchupScheme.FLUSSONIC
        ch.catchupType.equals("append", true) -> CatchupScheme.APPEND
        ch.catchupType.equals("shift", true) -> CatchupScheme.SHIFT
        ch.catchupType.equals("xc", true) || ch.catchupType.equals("xtream", true) ->
            CatchupScheme.XTREAM_TIMESHIFT
        ch.catchupSource.contains("archive-", true) -> CatchupScheme.FLUSSONIC
        ch.catchupSource.contains("timeshift", true) -> CatchupScheme.XTREAM_TIMESHIFT
        ch.catchupSource.isNotBlank() -> CatchupScheme.DEFAULT
        p.kind == "xtream" -> CatchupScheme.XTREAM_TIMESHIFT
        else -> CatchupScheme.UNKNOWN
    }

    /** True when [candidates] would return something for this channel. */
    fun isSupported(p: Profile, ch: Channel): Boolean {
        if (!ch.hasArchive) return false
        if (ch.catchupSource.startsWith("http", true)) return true
        return when (schemeOf(p, ch)) {
            CatchupScheme.UNKNOWN -> false
            CatchupScheme.APPEND, CatchupScheme.SHIFT, CatchupScheme.FLUSSONIC ->
                ch.url.isNotBlank() || p.kind == "xtream"
            else -> p.kind == "xtream"
        }
    }

    /** How far back this channel's archive reaches, in whole days. */
    fun archiveDays(ch: Channel): Int = ch.archiveDays.coerceAtLeast(1)

    /** True when [startMs] is inside the channel's advertised archive window. */
    fun isWithinWindow(ch: Channel, startMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean =
        startMs < nowMs && startMs >= nowMs - archiveDays(ch) * 86_400_000L

    // --- shapes ------------------------------------------------------------

    private fun xtreamShapes(
        p: Profile,
        ch: Channel,
        startMs: Long,
        durationMin: Long,
        tz: TimeZone,
    ): List<String> {
        if (p.kind != "xtream" || p.server.isBlank()) return emptyList()
        val base = p.server.trimEnd('/')
        val start = formatStart(startMs, tz)
        val id = ch.streamId
        return listOf(
            "$base/timeshift/${p.username}/${p.password}/$durationMin/$start/$id.ts",
            // The same archive, on panels that never exposed the path form.
            // This is the shape whose absence made Catch-Up "not work" on
            // lines where the feature was switched on the whole time.
            "$base/streaming/timeshift.php?username=${p.username}&password=${p.password}" +
                "&stream=$id&start=$start&duration=$durationMin",
            "$base/timeshift/${p.username}/${p.password}/$durationMin/$start/$id.m3u8",
        )
    }

    /**
     * Flussonic serves an archive segment off the channel's own path:
     * `.../channel/index.m3u8` → `.../channel/archive-<unix>-<seconds>.m3u8`.
     */
    private fun flussonicShapes(liveUrl: String, startSec: Long, durationSec: Long): List<String> {
        val query = liveUrl.substringAfter('?', "").let { if (it.isBlank()) "" else "?$it" }
        val path = liveUrl.substringBefore('?')
        val last = path.substringAfterLast('/')
        // Drop the playlist file if there is one; keep the channel directory.
        val dir = if (last.contains('.')) path.substringBeforeLast('/') else path.trimEnd('/')
        return listOf(
            "$dir/archive-$startSec-$durationSec.m3u8$query",
            "$dir/archive-$startSec-$durationSec.ts$query",
        )
    }

    private fun appendQuery(url: String, params: String): String =
        if (url.contains('?')) "$url&$params" else "$url?$params"

    /**
     * The first candidate the panel actually answers, or null when none do.
     *
     * Probing costs one short request per shape, on a button press the user
     * made deliberately. It buys the difference between "the provider has no
     * recording of that programme" and a player that spins for fifteen seconds
     * before reporting a decode failure — which is what every catch-up entry
     * point used to do, because each navigated straight to one guessed URL.
     */
    suspend fun resolve(
        http: okhttp3.OkHttpClient,
        p: Profile,
        ch: Channel,
        startMs: Long,
        endMs: Long,
    ): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val urls = candidates(p, ch, startMs, endMs)
        for (url in urls) {
            val ok = runCatching {
                val req = okhttp3.Request.Builder().url(url)
                    .header("Range", "bytes=0-1").get().build()
                http.newCall(req).execute().use { it.code == 200 || it.code == 206 }
            }.getOrDefault(false)
            if (ok) return@withContext url
        }
        // Nothing answered a ranged GET. When there was only one shape to try,
        // hand it back anyway — some panels refuse Range on archive paths while
        // streaming them perfectly well, and failing in the player beats
        // refusing to try at all. With several shapes, silence means silence.
        if (urls.size == 1) urls.first() else null
    }

    internal fun formatStart(startMs: Long, tz: TimeZone): String {
        val fmt = SimpleDateFormat(XTREAM_START, Locale.US)
        fmt.timeZone = tz
        return fmt.format(Date(startMs))
    }

    /**
     * Substitutes the placeholders providers actually put in `catchup-source`.
     *
     * Both the `${'$'}{name}` and `{name}` spellings are in circulation, often in
     * the same playlist, and the bare date components appear in Kodi-style
     * templates. Anything unrecognised is left alone rather than blanked —
     * a URL with a stray `${'$'}{foo}` in it fails visibly, whereas one silently
     * emptied to `.../archive--.ts` fails looking like our bug.
     */
    internal fun fillTemplate(
        template: String,
        startSec: Long,
        endSec: Long,
        nowSec: Long,
        durationSec: Long,
        tz: TimeZone,
    ): String {
        val cal = java.util.Calendar.getInstance(tz).apply { timeInMillis = startSec * 1000 }
        fun two(v: Int) = v.toString().padStart(2, '0')
        val replacements = linkedMapOf(
            "\${start}" to startSec.toString(),
            "\${end}" to endSec.toString(),
            "\${timestamp}" to nowSec.toString(),
            "\${duration}" to durationSec.toString(),
            "\${offset}" to (nowSec - startSec).coerceAtLeast(0).toString(),
            "{utc}" to startSec.toString(),
            "{utcend}" to endSec.toString(),
            "{lutc}" to nowSec.toString(),
            "{start}" to startSec.toString(),
            "{end}" to endSec.toString(),
            "{now}" to nowSec.toString(),
            "{duration}" to durationSec.toString(),
            "{offset}" to (nowSec - startSec).coerceAtLeast(0).toString(),
            "{Y}" to cal.get(java.util.Calendar.YEAR).toString(),
            "{m}" to two(cal.get(java.util.Calendar.MONTH) + 1),
            "{d}" to two(cal.get(java.util.Calendar.DAY_OF_MONTH)),
            "{H}" to two(cal.get(java.util.Calendar.HOUR_OF_DAY)),
            "{M}" to two(cal.get(java.util.Calendar.MINUTE)),
            "{S}" to two(cal.get(java.util.Calendar.SECOND)),
        )
        var out = template
        replacements.forEach { (k, v) -> out = out.replace(k, v) }
        return out
    }
}
