package tv.enktel.app.player

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * LibVLC — the automatic fallback engine.
 *
 * Media3 is the primary engine for everything: it owns hardware MediaCodec
 * surfaces, tunnelling, HDR/Dolby Vision and frame-rate matching on Fire TV,
 * none of which LibVLC does as well. LibVLC is only handed a stream once
 * [PlayerEngine] has exhausted its own recovery (every URL shape, the sniff
 * retry, the HLS reading), or immediately when a raw root-path MPEG-TS fails
 * to parse — the malformed PAT/PMT tables on those streams are something
 * TsExtractor rejects and VLC's demuxer shrugs off. It is also the better
 * renderer for DVB bitmap subtitles and Teletext pages.
 *
 * One LibVLC instance per surface: it is created when the fallback surface
 * enters composition and released when it leaves, so nothing native is
 * resident while Media3 is doing the work.
 */
class VlcFallback(context: Context) {
    private val libVlc = LibVLC(
        context.applicationContext,
        arrayListOf(
            // The figure from the routing table: 1.5 s absorbs the jitter of a
            // throttled panel without making a channel change feel slow.
            "--network-caching=$NETWORK_CACHING_MS",
            "--live-caching=$NETWORK_CACHING_MS",
            "--http-reconnect",
            "--clock-jitter=0",
            "--clock-synchro=0",
            // DVB and Teletext subtitles render through the subtitle layer.
            "--sub-autodetect-file",
        ),
    )
    val player = MediaPlayer(libVlc)

    fun play(url: String, userAgent: String, onError: () -> Unit) {
        val media = Media(libVlc, Uri.parse(url)).apply {
            setHWDecoderEnabled(true, false)
            addOption(":http-user-agent=$userAgent")
            addOption(":network-caching=$NETWORK_CACHING_MS")
        }
        player.setEventListener { e ->
            if (e.type == MediaPlayer.Event.EncounteredError) onError()
        }
        player.media = media
        media.release()
        player.play()
    }

    fun release() {
        player.setEventListener(null)
        runCatching { player.stop() }
        runCatching { player.detachViews() }
        player.release()
        libVlc.release()
    }

    companion object {
        const val NETWORK_CACHING_MS = 1500
    }
}

/**
 * Full-bleed video surface for the LibVLC fallback. Composed by the player
 * screens over (in place of) Media3's ContentFrame while
 * [PlayerEngine.vlcFallbackUrl] is set.
 */
@Composable
fun VlcFallbackSurface(
    url: String,
    userAgent: String,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vlc = remember(url) { VlcFallback(context) }
    AndroidView(
        factory = { ctx ->
            VLCVideoLayout(ctx).also { layout ->
                vlc.player.attachViews(layout, null, /* subtitles = */ true, /* textureView = */ false)
                vlc.play(url, userAgent, onError)
            }
        },
        modifier = modifier,
    )
    DisposableEffect(vlc) {
        onDispose { vlc.release() }
    }
}
