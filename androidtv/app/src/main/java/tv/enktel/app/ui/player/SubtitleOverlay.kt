package tv.enktel.app.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.ui.SubtitleView
import tv.enktel.app.player.Subtitles

/**
 * Subtitles for a Compose-rendered player.
 *
 * ### Why this exists
 *
 * `media3-ui-compose` renders video and nothing else. Its whole published
 * surface is `PlayerSurface`, `ContentFrame`, transport buttons and state
 * holders — there is not one subtitle or cue class in the artifact. So
 * migrating a player from `PlayerView` to `PlayerSurface` silently drops
 * captions, along with the four settings behind them.
 *
 * The alternative to this file was writing a cue renderer from scratch:
 * positioning from `cue.position` / `cue.line` / `cue.size`, `Spanned` styling
 * out of `cue.text` into an `AnnotatedString`, and edge treatments drawn by
 * hand. That is a lot of subtitle-standard surface area to reimplement, and
 * every part of it is already correct inside `SubtitleView`.
 *
 * So: keep `SubtitleView`, and bridge Media3's callback world into Compose's
 * declarative one. The View does the typography; Compose owns the lifecycle.
 */

/**
 * The cues currently on screen, as Compose state.
 *
 * The listener is removed on dispose *and* re-registered when the player
 * identity changes — this app swaps ExoPlayer instances when buffer settings
 * change, and a listener left on a released engine is both a leak and a source
 * of captions from the previous stream.
 */
@Composable
fun rememberCues(player: Player?): List<Cue> {
    var cues by remember(player) { mutableStateOf(emptyList<Cue>()) }
    DisposableEffect(player) {
        val p = player ?: return@DisposableEffect onDispose { }
        // Seed from the player rather than waiting for the next emission: a
        // caption already on screen when this composable mounts (rotating the
        // device, expanding the dock) would otherwise vanish until the next
        // cue change, which on a slow line is several seconds of silence.
        cues = p.currentCues.cues
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                cues = cueGroup.cues
            }
        }
        p.addListener(listener)
        onDispose {
            p.removeListener(listener)
            cues = emptyList()
        }
    }
    return cues
}

/**
 * Draws [player]'s captions over whatever is beneath, in the user's chosen
 * style.
 *
 * Sizing is fractional, not `sp`. `setFractionalTextSize` scales against the
 * view's own height, so one setting reads correctly on a 6-inch phone and on a
 * 65-inch panel driven by a Fire Cube without a density branch — which is what
 * a fixed `sp` value cannot do across a 10-foot UI and a held device.
 *
 * The view is non-focusable and non-clickable so a Fire TV remote's Select
 * passes straight through to the player controls underneath. An overlay that
 * quietly eats D-pad input is the kind of bug that presents as "the remote
 * stopped working during a subtitled film".
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun SubtitleOverlay(
    player: Player?,
    scalePct: Int,
    color: String,
    edge: String,
    bgAlpha: Int,
    modifier: Modifier = Modifier,
) {
    val cues = rememberCues(player)
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SubtitleView(ctx).apply {
                isClickable = false
                isFocusable = false
                isFocusableInTouchMode = false
            }
        },
        update = { view ->
            view.setCues(cues)
            // One place decides how a stored preference becomes a caption
            // style. Re-deriving the colour and edge mappings here would be a
            // second copy to drift against the settings screen.
            Subtitles.apply(view, scalePct, color, edge, bgAlpha)
        },
    )
}
