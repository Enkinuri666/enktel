package tv.enktel.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SouthEast
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Stadium
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Upcoming
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A pictograph written at the front of a label, and the drawing that replaces it.
 *
 * ## The problem
 *
 * The app labelled its controls with characters: `"▶ Play"`, `"⬇ Download"`,
 * `"🩺 Run connection diagnostics"`, `"📺 TV"`. Around three hundred string
 * literals, in buttons, chips, rail headings and menus.
 *
 * A character is not an icon. It is a glyph from whichever font the device
 * happens to carry, and that has four consequences, all of them visible:
 *
 *  - **It cannot take the content colour.** `🩺` is a full-colour bitmap. A
 *    focused button turns its label dark-on-accent and the stethoscope stays
 *    exactly as it was, so the two halves of one label disagree about whether
 *    the button is focused.
 *  - **It is a different drawing on every device.** Fire OS, One UI and stock
 *    Android each ship their own emoji set. `📺` is a retro wooden set on one
 *    and a flat-panel on another.
 *  - **Half of them are not emoji at all, sometimes.** `▶`, `⏸`, `⏪` and `⚙`
 *    are text-presentation by default and emoji-presentation on several
 *    vendors, so the same "▶ Play" is a thin outline on one box and a blue
 *    rounded square on the next.
 *  - **It sits on the text baseline**, not the optical centre, so a row of
 *    buttons that each begin with a different pictograph is a row of labels
 *    starting at slightly different heights.
 *
 * `FocusIconButton` was written to fix this for the Sports Hub, and its doc
 * comment says as much. It fixed eleven call sites. This fixes the rest.
 *
 * ## Why at the component, not at the call site
 *
 * The honest alternative is to rewrite each literal as a label plus an `icon =`
 * argument. Many are not literals: `if (fav) "★ Unstar" else "☆ Star"`,
 * `"★ Favourites (${n})"`, strings that arrive from the translation table in
 * two languages. Splitting each by hand is three hundred edits, every one a
 * chance to drop a state or mistranslate a label, for a result that is
 * pixel-identical to doing it here.
 *
 * So the shared controls — [FocusButton], [GlassChip], [ContentRail] — pass
 * their label through [split], and a leading pictograph this table knows is
 * drawn as its vector instead. A label with no pictograph is untouched, and so
 * is one this table has never heard of, which is what keeps it safe: the worst
 * case is the old behaviour. `GlyphsTest` scans the sources so a new literal
 * with an unmapped pictograph fails the build rather than quietly shipping as
 * an emoji.
 *
 * Characters that are fine as text are deliberately absent: `★` in front of a
 * rating, `●` in front of LIVE, `→` in a sentence. Those are typography, they
 * take the text colour, and they are only swapped where a control leads with
 * them.
 */
object Glyphs {

    /** What a pictograph becomes, and how to describe it when it stands alone. */
    data class Mark(val icon: ImageVector, val name: String)

    /**
     * Keys are matched longest-first, so `▤▤` wins over `▤` and `❚❚` over `❚`.
     * The emoji variation selector (U+FE0F) is skipped after any match, so
     * `☀` and `☀️` are one entry.
     */
    private val table: Map<String, Mark> = linkedMapOf(
        // transport
        "▶" to Mark(Icons.Rounded.PlayArrow, "Play"),
        "⏸" to Mark(Icons.Rounded.Pause, "Pause"),
        "❚❚" to Mark(Icons.Rounded.Pause, "Pause"),
        "■" to Mark(Icons.Rounded.Stop, "Stop"),
        "●" to Mark(Icons.Rounded.FiberManualRecord, "Record"),
        "⏺" to Mark(Icons.Rounded.FiberManualRecord, "Record"),
        "🔴" to Mark(Icons.Rounded.Sensors, "Live"),
        "⏪" to Mark(Icons.Rounded.FastRewind, "Rewind"),
        "⏩" to Mark(Icons.Rounded.FastForward, "Fast forward"),
        "⏮" to Mark(Icons.Rounded.SkipPrevious, "Restart"),
        "⏭" to Mark(Icons.Rounded.SkipNext, "Next"),
        "↻" to Mark(Icons.Rounded.Refresh, "Refresh"),
        "↺" to Mark(Icons.Rounded.RestartAlt, "Restart"),
        // state
        "★" to Mark(Icons.Rounded.Star, "Favourite"),
        "⭐" to Mark(Icons.Rounded.Star, "Favourite"),
        "☆" to Mark(Icons.Rounded.StarBorder, "Add favourite"),
        "✓" to Mark(Icons.Rounded.Check, "Done"),
        "✕" to Mark(Icons.Rounded.Close, "Close"),
        "🔒" to Mark(Icons.Rounded.Lock, "Locked"),
        "🚫" to Mark(Icons.Rounded.Block, "Hidden"),
        "👁" to Mark(Icons.Rounded.Visibility, "Shown"),
        "⏳" to Mark(Icons.Rounded.HourglassTop, "Working"),
        "⚠" to Mark(Icons.Rounded.WarningAmber, "Warning"),
        "❓" to Mark(Icons.Rounded.HelpOutline, "Unknown"),
        // direction
        "←" to Mark(Icons.AutoMirrored.Rounded.ArrowBack, "Back"),
        "→" to Mark(Icons.AutoMirrored.Rounded.ArrowForward, "Forward"),
        "↑" to Mark(Icons.Rounded.ArrowUpward, "Up"),
        "↓" to Mark(Icons.Rounded.ArrowDownward, "Down"),
        "▲" to Mark(Icons.Rounded.KeyboardArrowUp, "Up"),
        "▼" to Mark(Icons.Rounded.KeyboardArrowDown, "Down"),
        "◀" to Mark(Icons.Rounded.ChevronLeft, "Previous"),
        "▸" to Mark(Icons.Rounded.ChevronRight, "Next"),
        "↗" to Mark(Icons.Rounded.NorthEast, "Top right"),
        "↖" to Mark(Icons.Rounded.NorthWest, "Top left"),
        "↘" to Mark(Icons.Rounded.SouthEast, "Bottom right"),
        "↙" to Mark(Icons.Rounded.SouthWest, "Bottom left"),
        "⬇" to Mark(Icons.Rounded.Download, "Download"),
        // places and things
        "📺" to Mark(Icons.Rounded.Tv, "TV"),
        "📻" to Mark(Icons.Rounded.Radio, "Radio"),
        "🎬" to Mark(Icons.Rounded.Movie, "Film"),
        "🎞" to Mark(Icons.Rounded.Theaters, "Film library"),
        "⚽" to Mark(Icons.Rounded.SportsSoccer, "Sport"),
        "🏟" to Mark(Icons.Rounded.Stadium, "Sports hub"),
        "📡" to Mark(Icons.Rounded.Podcasts, "On air"),
        "🗓" to Mark(Icons.Rounded.CalendarMonth, "Guide"),
        "🗂" to Mark(Icons.Rounded.Category, "Categories"),
        "📁" to Mark(Icons.Rounded.Folder, "Folder"),
        "📂" to Mark(Icons.Rounded.FolderOpen, "Browse"),
        "🔗" to Mark(Icons.Rounded.Link, "Link"),
        "📋" to Mark(Icons.Rounded.ContentCopy, "Copy"),
        "📤" to Mark(Icons.Rounded.Upload, "Send"),
        "🧸" to Mark(Icons.Rounded.ChildCare, "Kids"),
        "🎙" to Mark(Icons.Rounded.Mic, "Voice"),
        "🎧" to Mark(Icons.Rounded.Headphones, "Listen along"),
        "💬" to Mark(Icons.Rounded.ClosedCaption, "Subtitles"),
        "🌐" to Mark(Icons.Rounded.Language, "Network"),
        "🎨" to Mark(Icons.Rounded.Palette, "Appearance"),
        "🕶" to Mark(Icons.Rounded.Devices, "Device identity"),
        "🎟" to Mark(Icons.Rounded.Upcoming, "Coming soon"),
        // tools
        "⚙" to Mark(Icons.Rounded.Settings, "Settings"),
        "☰" to Mark(Icons.AutoMirrored.Rounded.List, "List"),
        "▤▤" to Mark(Icons.Rounded.GridView, "Multi-view"),
        "▤" to Mark(Icons.Rounded.PictureInPictureAlt, "Dock"),
        "⛶" to Mark(Icons.Rounded.Fullscreen, "Full screen"),
        "⧉" to Mark(Icons.Rounded.PictureInPicture, "Picture in picture"),
        "＋" to Mark(Icons.Rounded.Add, "Add"),
        "⋯" to Mark(Icons.Rounded.MoreHoriz, "More"),
        "⟲" to Mark(Icons.Rounded.RestartAlt, "Start over"),
        "🔎" to Mark(Icons.Rounded.Search, "Search"),
        "🔍" to Mark(Icons.Rounded.Search, "Search"),
        "🩺" to Mark(Icons.Rounded.MonitorHeart, "Diagnostics"),
        "📈" to Mark(Icons.AutoMirrored.Rounded.ShowChart, "Monitor"),
        "📊" to Mark(Icons.Rounded.Insights, "Stats"),
        "📶" to Mark(Icons.Rounded.NetworkCheck, "Speed test"),
        "⏱" to Mark(Icons.Rounded.Schedule, "Now"),
        "⏰" to Mark(Icons.Rounded.Alarm, "Reminder"),
        // volume and light
        "🔊" to Mark(Icons.AutoMirrored.Rounded.VolumeUp, "Volume"),
        "🔇" to Mark(Icons.AutoMirrored.Rounded.VolumeOff, "Muted"),
        "☀" to Mark(Icons.Rounded.BrightnessHigh, "Brightness"),
        "☾" to Mark(Icons.Rounded.Bedtime, "Sleep timer"),
        // rail headings — only ever stripped or shown small, never a control
        "🆕" to Mark(Icons.Rounded.NewReleases, "New"),
        "🔥" to Mark(Icons.Rounded.LocalFireDepartment, "Trending"),
        "⚡" to Mark(Icons.Rounded.Bolt, "Just added"),
        "📚" to Mark(Icons.AutoMirrored.Rounded.MenuBook, "Documentaries"),
        "🧠" to Mark(Icons.Rounded.Psychology, "Mind-bending"),
        "🌒" to Mark(Icons.Rounded.Bedtime, "Gritty"),
        "🌙" to Mark(Icons.Rounded.Bedtime, "Late night"),
        "☀️" to Mark(Icons.Rounded.WbSunny, "Feel-good"),
    )

    private val byLength: List<Map.Entry<String, Mark>> =
        table.entries.sortedByDescending { it.key.length }

    /** Every pictograph the table can replace — for the source scan. */
    val known: Set<String> get() = table.keys

    /** A label taken apart: the drawing it led with, if any, and the words. */
    data class Parts(val mark: Mark?, val text: String)

    /**
     * Split a leading pictograph off [label].
     *
     * Returns the label unchanged, with a null mark, when it does not begin
     * with a pictograph this table knows. Whitespace between the pictograph
     * and the words is dropped — the control supplies its own gap — and so is
     * a trailing emoji variation selector.
     *
     * A label that is *only* a pictograph comes back with empty text and the
     * mark, and the caller draws an icon-only control described by
     * [Mark.name].
     */
    fun split(label: String): Parts {
        val start = label.indexOfFirst { !it.isWhitespace() }
        if (start < 0) return Parts(null, label)
        for ((glyph, mark) in byLength) {
            if (!label.startsWith(glyph, start)) continue
            var i = start + glyph.length
            if (i < label.length && label[i] == VARIATION_SELECTOR) i++
            // "▶▶" or "★★" is somebody's own typography, not a leading icon.
            if (i < label.length && label.startsWith(glyph, i)) return Parts(null, label)
            return Parts(mark, label.substring(i).trim())
        }
        return Parts(null, label)
    }

    /** [split], keeping only the words. For headings that want no icon. */
    fun strip(label: String): String = split(label).let { if (it.mark == null) label else it.text }

    private const val VARIATION_SELECTOR = '️'
}
