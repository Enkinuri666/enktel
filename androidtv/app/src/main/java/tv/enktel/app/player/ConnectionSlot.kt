package tv.enktel.app.player

/**
 * When to hand the panel's session back, as opposed to when to hold it.
 *
 * ### The problem this exists for
 *
 * An Xtream line is sold with a cap on simultaneous sessions, and every plan
 * this app ships against is sold with a cap of one. So "watch on the TV and on
 * the phone" is not a question of watching both at once — it cannot be — it is
 * a question of whether the second device can start when the first has stopped.
 *
 * Today it often cannot. Walking away from the television does not end the
 * session: nothing releases on leaving the app, and even
 * `PlaybackSession.stop()` released the *player* while leaving the socket in
 * OkHttp's pool. A pooled socket still open to a `/live` URL is how a panel
 * decides a session is in use, so the phone is refused by a television nobody
 * is watching, until the panel's own timeout eventually notices.
 *
 * ### Why this is a different rule from [ZapPlan]
 *
 * [ZapPlan.needsReleaseBeforeAcquire] asks the same question at a channel
 * change and answers it only for capped lines, because there it is a *trade*:
 * a full teardown costs a fresh handshake on the next tune, so lines with room
 * should not pay it.
 *
 * Stopping watching has no such trade. The viewer has gone; there is no next
 * tune to slow down. The cost of releasing is zero and the benefit is that the
 * next device can start, so the cap does not enter into it — which is why this
 * rule deliberately does not consult `maxConnections` at all. Being cautious
 * with an unknown cap is right for [ZapPlan] and wrong here.
 *
 * ### What "still watching" means
 *
 * There are three ways playback legitimately continues with the app off the
 * screen, and all three are the viewer's explicit choice rather than an
 * oversight. Releasing under any of them would be breaking a feature to fix a
 * different one.
 *
 * Everything here is pure, and [ConnectionSlotTest] pins it.
 */
object ConnectionSlot {

    /**
     * Whether leaving the app should hand the session back.
     *
     * @param backgroundAudio the Settings toggle that exists precisely so a
     *   news or sport feed keeps playing with the screen off.
     * @param pictureInPicture playback is still on screen in a corner — the
     *   activity has stopped, the viewer has not.
     * @param docked the mini-window, same reasoning as [pictureInPicture].
     */
    fun shouldReleaseOnBackground(
        backgroundAudio: Boolean,
        pictureInPicture: Boolean,
        docked: Boolean,
    ): Boolean = !backgroundAudio && !pictureInPicture && !docked

    /**
     * Whether the line has no room for another device right now.
     *
     * Null means "cannot tell", and the two ways of not being able to tell are
     * worth keeping apart from a confident "no":
     *
     *  - `maxConnections <= 0` — the panel declined to publish a cap. Every
     *    plain M3U line does this and so do plenty of Xtream panels.
     *  - `activeConnections < 0` — the caller has not asked the panel yet, or
     *    the call failed. [tv.enktel.app.ui.screens.AccountBanner] uses -1 for
     *    exactly this.
     *
     * A screen that says "1 of 1 in use" when it does not know is worse than
     * one that says nothing, because it invites the viewer to go hunting for a
     * device that is not streaming.
     */
    fun atCapacity(activeConnections: Int, maxConnections: Int): Boolean? {
        if (maxConnections <= 0 || activeConnections < 0) return null
        return activeConnections >= maxConnections
    }

    /**
     * Whether a session this device is holding could be the one in the way.
     *
     * The honest limit of the whole feature, and the reason it is a separate
     * question. The app can hang up its own socket and nothing else: there is
     * no customer-facing panel call that ends a session on another device, so
     * "the phone is being blocked by the television" is only actionable from
     * the television. When the line is at capacity and this device is not
     * streaming, the blocking session belongs somewhere else and the honest
     * answer is to say so rather than to offer a button that cannot help.
     */
    fun canFreeFromHere(playingHere: Boolean): Boolean = playingHere
}
