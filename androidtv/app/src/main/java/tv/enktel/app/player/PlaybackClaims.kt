package tv.enktel.app.player

import java.util.concurrent.atomic.AtomicLong

/**
 * Which player screen currently owns the session.
 *
 * ### The hand-off this exists for
 *
 * One [PlaybackSession] owns one [PlayerEngine], and every player screen
 * borrows it. A screen that goes away while the session is not docked calls
 * `stop()`, which releases the engine — that rule is what keeps audio from
 * outliving a visible player.
 *
 * It is wrong in one case: when the screen that goes away is being *replaced
 * by another player screen*. Rolling into the next episode navigates from the
 * VOD player to the VOD player, and Compose composes the incoming screen
 * before it disposes the outgoing one. So the new screen took the engine and
 * asked it to play, and then the old screen's `onDispose` released that engine
 * out from under it. The next episode never appeared, and the only way on was
 * to leave playback and start it by hand.
 *
 * The counter settles it without anyone having to reason about disposal order.
 * A screen claims the session as it composes, which is strictly before the
 * outgoing screen is forgotten — Compose dispatches every `onForgotten` after
 * composition, and a `remember` runs during it. A screen that has since been
 * superseded therefore fails [isOwner] and leaves the engine alone; the last
 * screen standing still owns its claim and still tears down, so the invariant
 * that motivated `stop()` survives intact.
 *
 * Kept separate from [PlaybackSession] because that class needs a Context and
 * an OkHttpClient to exist, and this rule is worth testing on its own.
 */
class PlaybackClaims {
    private val seq = AtomicLong(0)

    /** Take the session. Returns the token to check on the way out. */
    fun claim(): Long = seq.incrementAndGet()

    /** True while [token] is the most recent claim — i.e. nobody took over. */
    fun isOwner(token: Long): Boolean = seq.get() == token
}
