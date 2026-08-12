package tv.enktel.app.player

/**
 * Which channels to warm ahead of a zap, and whether to warm any at all.
 *
 * ### What warming actually buys, and what it costs
 *
 * The player and the app share one OkHttp client, so the connection carrying
 * the channel now playing is already pooled and hot. That connection is busy,
 * though — a zap needs a *second* one, and paying for DNS, TCP and TLS at the
 * moment the viewer presses the button is routinely 300–800 ms of the delay
 * they feel. Opening that second connection early is the whole trick.
 *
 * The cost is that on an Xtream panel a request to a stream URL is a session,
 * and lines are sold with a cap on those. Warming is therefore not free and
 * not always right:
 *
 * | line permits | playing | warm | during a zap | verdict |
 * | :--- | :--- | :--- | :--- | :--- |
 * | 1 | 1 | — | 2 | never warm; the zap itself already overlaps |
 * | 2 | 1 | 1 | 3 | over cap during the zap |
 * | 3+ | 1 | 1 | 3 | fits |
 *
 * That middle row is the bug this replaces. The old rule excluded only
 * `maxConnections == 1`, so a two-connection line warmed two neighbours —
 * three sessions against a cap of two before the viewer touched anything, and
 * a fourth the moment they zapped. Panels answer that by refusing the new
 * session or by dropping the one in progress, and the second presents as the
 * stream cutting out while the viewer sits perfectly still.
 *
 * ### One neighbour, not two
 *
 * Warming both directions doubles the session cost to cover a guess. Somebody
 * who has just pressed channel-up is far more likely to press it again than to
 * reverse, so the direction they are already travelling is the better bet at
 * half the price. Before any zap has happened there is no direction to follow
 * and up is the convention.
 *
 * Pure arithmetic, so the cap rule and the wrap-around are pinned by
 * [ZapPlanTest] rather than discovered on a two-connection line at a weekend.
 */
object ZapPlan {

    /**
     * Sessions a zap needs at its peak: the outgoing stream has not always let
     * go by the time the incoming one opens.
     */
    const val SESSIONS_DURING_ZAP = 2

    /**
     * Whether there is room on this line to hold a warm connection open.
     *
     * @param maxConnections the panel's own figure. **0 means it did not say**
     *   — every M3U line and plenty of Xtream panels report nothing — and the
     *   benefit of the doubt goes to warming, because disabling it for every
     *   profile that declines to answer would be the common case.
     */
    fun shouldWarm(maxConnections: Int): Boolean =
        maxConnections == 0 || maxConnections > SESSIONS_DURING_ZAP

    /**
     * The index to warm, or null when there is nothing sensible to warm.
     *
     * @param direction the last zap: positive for up, negative for down, and
     *   zero before the viewer has zapped at all.
     */
    fun target(size: Int, currentIndex: Int, direction: Int): Int? {
        if (size <= 1 || currentIndex < 0 || currentIndex >= size) return null
        val step = if (direction < 0) -1 else 1
        return ((currentIndex + step) % size + size) % size
    }

    /**
     * Whether a channel change has to let go of the old stream before opening
     * the new one.
     *
     * A zap wants two sessions at its peak. A line that permits fewer than
     * three has no room for that overlap, and the panel resolves the shortfall
     * by refusing the new stream or dropping the old one — on a cap of one,
     * every single channel change. Those lines trade a fraction of a second of
     * zap latency for a picture that actually arrives.
     *
     * Lines with room are left alone: forcing a full teardown there would slow
     * every zap to fix a problem they do not have.
     *
     * Complementary to [shouldWarm] across every cap a panel can actually
     * report — a line either has room to hold a spare connection open, or it
     * needs the old one gone first. The one exception is a negative figure,
     * which no well-behaved panel produces: there both answers are no, because
     * garbage is not evidence that a line is capped and not evidence that it
     * has room either.
     */
    fun needsReleaseBeforeAcquire(maxConnections: Int): Boolean =
        maxConnections in 1..SESSIONS_DURING_ZAP

    /**
     * How long to let the socket close and the panel notice, before asking it
     * for the next stream.
     *
     * A guess, and flagged as one. Panels free a session somewhere between
     * immediately on socket close and several seconds later, and there is no
     * way to ask which. Long enough to cover the close propagating, short
     * enough that a viewer holding channel-up does not feel it as a stall.
     */
    const val RELEASE_GRACE_MS = 300L

    /**
     * True when the URL is worth a full warm-up request.
     *
     * An HLS playlist is a few hundred bytes of text and every panel serves it.
     * A raw MPEG-TS URL is an endless response: the request opens, the panel
     * starts pushing video, and the only thing that ends it is the client
     * hanging up. Warming one is still worth doing — it is the same handshake
     * either way — but it has to be cut off the instant the socket is
     * established rather than read, which is why the caller uses a HEAD and a
     * short timeout for these. See [ZapPreloader].
     */
    fun isPlaylist(url: String): Boolean =
        url.substringBefore('?').substringBefore('#').endsWith(".m3u8", ignoreCase = true)
}
