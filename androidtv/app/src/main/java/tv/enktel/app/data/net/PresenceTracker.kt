package tv.enktel.app.data.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Session-wide "what am I watching right now" state model.  Player screens
 * push into it as their current asset changes; publishers (Discord webhook,
 * Android share intent, the PC app's real RPC bridge) read from it.
 *
 * The three variants map to the three visible surfaces of the app so a
 * publisher can render presence cards that actually match Discord Rich
 * Presence conventions:
 *
 *   - LIVE: channel name + "now on" program from the EPG + elapsed
 *   - VOD:  title + year + progress bar
 *   - SPORT: teams/title + league + LIVE indicator
 *
 * [PresenceTracker] is a singleton (like ActivePlayerRef).  Nothing here
 * knows about Discord specifically; that's a publisher's job.
 */
object PresenceTracker {

    sealed class State {
        data object Idle : State()

        data class Live(
            val channelName: String,
            val channelLogo: String = "",
            val programTitle: String? = null,
            /** Program end time in ms since epoch — used to render Discord's
             *  "N minutes left" countdown when the current EPG entry has one. */
            val programEndMs: Long = 0L,
            /** Elapsed millis in the current viewing session. */
            val startedAt: Long = System.currentTimeMillis(),
        ) : State()

        data class Vod(
            val title: String,
            val year: Int = 0,
            val genre: String = "",
            val poster: String = "",
            val positionMs: Long = 0,
            val durationMs: Long = 0,
        ) : State()

        data class Sport(
            val eventTitle: String,
            val league: String = "",
            val channelName: String = "",
            val isLive: Boolean = true,
            val startedAt: Long = System.currentTimeMillis(),
        ) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun setLive(
        channelName: String,
        channelLogo: String = "",
        programTitle: String? = null,
        programEndMs: Long = 0L,
    ) {
        _state.value = State.Live(channelName, channelLogo, programTitle, programEndMs)
    }

    fun setVod(
        title: String, year: Int = 0, genre: String = "", poster: String = "",
        positionMs: Long = 0, durationMs: Long = 0,
    ) {
        _state.value = State.Vod(title, year, genre, poster, positionMs, durationMs)
    }

    fun updateVodPosition(positionMs: Long, durationMs: Long) {
        val cur = _state.value
        if (cur is State.Vod) {
            _state.value = cur.copy(positionMs = positionMs, durationMs = durationMs)
        }
    }

    fun setSport(eventTitle: String, league: String = "", channelName: String = "", isLive: Boolean = true) {
        _state.value = State.Sport(eventTitle, league, channelName, isLive)
    }

    fun clear() {
        _state.value = State.Idle
    }

    /**
     * Render the current state as a short one-liner suitable for a Discord
     * status message, a share intent, or a toast preview.  Returns null when
     * the user isn't watching anything.
     */
    fun renderShareText(): String? = when (val s = _state.value) {
        is State.Idle -> null
        is State.Live -> buildString {
            append("📺 Watching ").append(s.channelName)
            s.programTitle?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
            append(" on EnkTel")
        }
        is State.Vod -> buildString {
            append("🎬 Watching ").append(s.title)
            if (s.year > 0) append(" (").append(s.year).append(")")
            if (s.durationMs > 0) {
                val pct = ((s.positionMs.toDouble() / s.durationMs) * 100).toInt().coerceIn(0, 100)
                append(" · ").append(pct).append("%")
            }
            append(" on EnkTel")
        }
        is State.Sport -> buildString {
            append("⚽ ")
            if (s.isLive) append("LIVE · ")
            append(s.eventTitle)
            if (s.league.isNotBlank()) append(" · ").append(s.league)
            append(" on EnkTel")
        }
    }
}
