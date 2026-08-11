package tv.enktel.app.data.repo

import tv.enktel.app.data.db.EpgProgram

/**
 * The viewer's EPG timezone correction, applied to guide times.
 *
 * ### The bug this exists to fix
 *
 * Settings offers "EPG timezone offset" — a row of chips from -3h to +3h,
 * captioned *"Shifts the XMLTV guide times when the panel's clock doesn't
 * match yours."* The chosen value was written to preferences and read by
 * nothing. A viewer whose guide was an hour out could pick -60m, watch the
 * chip highlight, and see the guide sit exactly where it was.
 *
 * ### Why the correction happens on read, not on import
 *
 * Shifting the times as the XMLTV is parsed would be less arithmetic — the
 * numbers in the database would already be right, and every reader could stay
 * as it was. It would also mean the setting does nothing until the next guide
 * download, which on a large XMLTV is minutes away and on a panel that only
 * publishes once a day is tomorrow. A correction you cannot see is
 * indistinguishable from the bug it is correcting, so the shift is applied on
 * the way out and takes effect on the next frame.
 *
 * ### The part that is easy to get backwards
 *
 * The correction has to be applied to the *query bounds* as well as to the
 * results, in opposite directions. Asking "what is on at eight o'clock" with a
 * +1h correction means asking the database for what it has stored at seven,
 * then presenting it as eight. Shifting only the results returns the right
 * programmes labelled correctly but selected by the wrong window — an hour of
 * the guide near the edges is simply missing, which looks like patchy EPG data
 * rather than like an off-by-one.
 *
 * Pure arithmetic, so both directions are pinned by [EpgShiftTest] rather than
 * discovered by a viewer whose guide is now two hours out instead of one.
 */
object EpgShift {

    /** Minutes to milliseconds. */
    fun offsetMs(offsetMin: Int): Long = offsetMin * 60_000L

    /**
     * A wall-clock instant expressed in the database's frame of reference.
     *
     * Use for every `from`, `to` and `now` bound handed to a DAO query.
     */
    fun toStored(wallMs: Long, offsetMin: Int): Long = wallMs - offsetMs(offsetMin)

    /** A stored instant expressed in the viewer's frame of reference. */
    fun toWall(storedMs: Long, offsetMin: Int): Long = storedMs + offsetMs(offsetMin)

    /**
     * [p] with its start and end moved into the viewer's frame.
     *
     * Returns the same instance when there is nothing to correct, which is the
     * overwhelmingly common case — the default is zero, and a guide list is
     * hundreds of rows redrawn on every tick.
     */
    fun shift(p: EpgProgram, offsetMin: Int): EpgProgram =
        if (offsetMin == 0) p
        else p.copy(startMs = toWall(p.startMs, offsetMin), endMs = toWall(p.endMs, offsetMin))

    /** [list] with every programme moved into the viewer's frame. */
    fun shift(list: List<EpgProgram>, offsetMin: Int): List<EpgProgram> =
        if (offsetMin == 0) list else list.map { shift(it, offsetMin) }
}
