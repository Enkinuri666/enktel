package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import tv.enktel.app.player.AudioDecoding

class AudioDecodingTest {

    /**
     * The mirrored constants have to keep matching Media3, because
     * [tv.enktel.app.player.PlayerEngine] passes them straight into
     * `DefaultRenderersFactory.setExtensionRendererMode` and the parameter is
     * a bare `int` — a drift here is silently the wrong decoder, not a
     * compile error.
     *
     * Verified against media3 1.11.0's `DefaultRenderersFactory`:
     * `EXTENSION_RENDERER_MODE_OFF = 0`, `_ON = 1`, `_PREFER = 2`. Asserted as
     * literals rather than by importing the class so this stays a plain JVM
     * test with no Android on the classpath, matching BufferProfilesTest.
     */
    @Test
    fun `mirrored constants match Media3's values`() {
        assertEquals(0, AudioDecoding.EXTENSIONS_OFF)
        assertEquals(1, AudioDecoding.EXTENSIONS_AFTER_PLATFORM)
        assertEquals(2, AudioDecoding.EXTENSIONS_BEFORE_PLATFORM)
    }

    /**
     * The regression this whole file exists for.
     *
     * The default must leave the platform decoder ahead of the FFmpeg
     * extension. When it was PREFER, FFmpeg claimed Opus (and AAC, MP3, FLAC,
     * Vorbis, ALAC…) ahead of decoders every Fire TV already has, which put a
     * software audio decode on the same cores feeding a 4K HEVC stream — the
     * stutter — and stopped AC-3/E-AC-3 ever reaching a receiver untouched.
     */
    @Test
    fun `the default keeps the platform decoder first`() {
        assertEquals(
            AudioDecoding.EXTENSIONS_AFTER_PLATFORM,
            AudioDecoding.extensionRendererMode(AudioDecoding.HW_PLUS),
        )
    }

    @Test
    fun `hardware-only builds no extension renderer`() {
        assertEquals(
            AudioDecoding.EXTENSIONS_OFF,
            AudioDecoding.extensionRendererMode(AudioDecoding.HW_ONLY),
        )
    }

    /** The escape hatch for a box that advertises a decoder and plays silence. */
    @Test
    fun `software-first is still reachable, but only on request`() {
        assertEquals(
            AudioDecoding.EXTENSIONS_BEFORE_PLATFORM,
            AudioDecoding.extensionRendererMode(AudioDecoding.SOFTWARE_FIRST),
        )
    }

    /**
     * This reads off a DataStore that shipped builds have already written to.
     * "on" is the legacy spelling of hwplus; anything else is a value from a
     * future build, a corrupted preference, or a typo, and none of those
     * should be a crash on the first frame or a silent trip through software.
     */
    @Test
    fun `legacy and unknown values land on the default`() {
        for (mode in listOf("on", "off", "", "HWPLUS", "prefer", "nonsense")) {
            assertEquals(
                "mode=$mode",
                AudioDecoding.EXTENSIONS_AFTER_PLATFORM,
                AudioDecoding.extensionRendererMode(mode),
            )
        }
    }

    /**
     * Float output is off for every mode, including the software-first escape
     * hatch — `FfmpegAudioRenderer.shouldOutputFloat` returns true for
     * everything except AC-3 the moment the sink advertises direct float
     * support, so leaving it on would put exactly the codecs that mode exists
     * to rescue onto the float AudioTrack path.
     */
    @Test
    fun `float PCM output stays off`() {
        assertFalse(AudioDecoding.floatOutput())
    }
}
