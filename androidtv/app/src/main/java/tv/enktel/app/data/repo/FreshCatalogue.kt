package tv.enktel.app.data.repo

/**
 * Works out which titles a sync actually introduced.
 *
 * The panel's own `added` field is not good enough to answer "what is new":
 * M3U lines do not carry one at all, and where Xtream does supply it, it is
 * the provider's ingest date, which can be years before the title reached
 * this particular line. A user re-syncing and seeing a 2019 timestamp on
 * something that appeared today is being told the wrong thing.
 *
 * A sync knows the answer exactly, because it has both catalogues in hand.
 * This carries the previous first-seen stamp across, and stamps everything
 * without one as new.
 *
 * ## Identity
 *
 * Xtream stream ids are stable, so the row key works. M3U keys are
 * *positional* (`profileId:index`), so inserting one channel at the top of a
 * playlist would shift every key below it and make the whole catalogue look
 * brand new. For those, identity has to come from the title itself.
 */
object FreshCatalogue {

    /** Anything first seen within this window counts as new. */
    const val NEW_WINDOW_MS = 14L * 24 * 60 * 60 * 1000

    /** Loose title identity — case, spacing and punctuation all ignored. */
    fun titleId(name: String): String = name.lowercase().filter(Char::isLetterOrDigit)

    /**
     * Stamps for the incoming catalogue.
     *
     * @param incoming identity of each row being written, in write order
     * @param previous identity → first-seen stamp from the catalogue being
     *   replaced
     * @param nowMs the stamp to give rows that were not there before
     * @param firstEverSync true on the very first sync of a profile, where
     *   *everything* is technically new and marking it all as such would
     *   drown the "new" rail in the entire library. Those rows get 0.
     */
    fun stamp(
        incoming: List<String>,
        previous: Map<String, Long>,
        nowMs: Long = System.currentTimeMillis(),
        firstEverSync: Boolean = previous.isEmpty(),
    ): List<Long> = incoming.map { id ->
        previous[id] ?: if (firstEverSync) 0L else nowMs
    }

    /** Is [firstSeenAt] recent enough to still be worth flagging? */
    fun isNew(firstSeenAt: Long, nowMs: Long = System.currentTimeMillis()): Boolean =
        firstSeenAt > 0 && nowMs - firstSeenAt <= NEW_WINDOW_MS
}
