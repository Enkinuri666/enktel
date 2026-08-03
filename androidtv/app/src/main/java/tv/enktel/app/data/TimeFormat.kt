package tv.enktel.app.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cached `SimpleDateFormat`s.
 *
 * Constructing one is not free — it compiles the pattern and pulls the
 * locale's symbols each time — and the app was doing it in two places where
 * that cost multiplies: once per visible cell in the EPG grid (which
 * recomposes on every minute tick), and twice per programme while parsing
 * XMLTV, where a full guide runs to six figures.
 *
 * `SimpleDateFormat` is not thread-safe, so the cache is per-thread rather
 * than shared. It is also keyed on the locale in force when the entry was
 * made, so switching the device language re-derives the formatters instead of
 * serving stale month names.
 */
object TimeFormat {

    private data class Key(val pattern: String, val locale: Locale)

    // Not ThreadLocal.withInitial — that is API 26 and minSdk here is 21, so on
    // a Fire TV Stick running Lollipop every lookup would have thrown.
    private val cache = object : ThreadLocal<HashMap<Key, SimpleDateFormat>>() {
        override fun initialValue() = HashMap<Key, SimpleDateFormat>(8)
    }

    fun formatter(pattern: String, locale: Locale = Locale.getDefault()): SimpleDateFormat =
        cache.get()!!.getOrPut(Key(pattern, locale)) { SimpleDateFormat(pattern, locale) }

    /** Formats [ms] (epoch millis) with a cached formatter for [pattern]. */
    fun format(pattern: String, ms: Long, locale: Locale = Locale.getDefault()): String =
        formatter(pattern, locale).format(Date(ms))

    /** Formats "now" with a cached formatter for [pattern]. */
    fun now(pattern: String, locale: Locale = Locale.getDefault()): String =
        format(pattern, System.currentTimeMillis(), locale)

    /**
     * The device locale, read so Compose can observe it.
     *
     * `Locale.getDefault()` inside a composable is a static read: change the
     * device language and the already-composed month and weekday names keep
     * the old one until something unrelated forces a recomposition. Compose's
     * own Locale is configuration-backed, so reading through it invalidates
     * properly. (This is what Compose 1.11's NonObservableLocale check is for.)
     */
    @Composable
    fun currentLocale(): Locale {
        val tag = androidx.compose.ui.text.intl.Locale.current.toLanguageTag()
        return remember(tag) { Locale.forLanguageTag(tag) }
    }
}
