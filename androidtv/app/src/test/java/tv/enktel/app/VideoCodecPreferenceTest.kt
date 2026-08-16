package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.enktel.app.player.VideoCodecPreference
import tv.enktel.app.player.VideoCodecPreference.AV1
import tv.enktel.app.player.VideoCodecPreference.H264
import tv.enktel.app.player.VideoCodecPreference.HEVC
import tv.enktel.app.player.VideoCodecPreference.VP9

class VideoCodecPreferenceTest {

    /**
     * These are passed to `setPreferredVideoMimeTypes` as bare strings, so a
     * typo does not fail to compile — it silently stops matching any track and
     * the preference quietly does nothing. Pinned against media3 1.11.0's
     * `MimeTypes`: VIDEO_H264 = "video/avc", VIDEO_H265 = "video/hevc",
     * VIDEO_VP9 = "video/x-vnd.on2.vp9", VIDEO_AV1 = "video/av01".
     *
     * Asserted as literals rather than by importing MimeTypes so this stays a
     * plain JVM test, matching BufferProfilesTest and AudioDecodingTest.
     */
    @Test
    fun `mime constants match media3's spelling`() {
        assertEquals("video/av01", AV1)
        assertEquals("video/hevc", HEVC)
        assertEquals("video/x-vnd.on2.vp9", VP9)
        assertEquals("video/avc", H264)
    }

    /**
     * The regression. A box whose only HEVC path is software must not be told
     * to prefer HEVC — omitting it is what lets a listed H.264 outrank it and
     * hands the decision back to `usesHardwareAcceleration`.
     */
    @Test
    fun `software-only HEVC is left out so hardware H264 wins`() {
        assertEquals(listOf(H264), VideoCodecPreference.order(setOf(H264)))
    }

    /** A box that decodes everything in hardware keeps exactly the old list. */
    @Test
    fun `a fully capable box is unchanged from the legacy order`() {
        val all = setOf(AV1, HEVC, VP9, H264)
        assertEquals(VideoCodecPreference.LEGACY_ORDER, VideoCodecPreference.order(all))
    }

    /**
     * Unknown is not the same as none. A probe that could not read the codec
     * list must not demote a Shield to H.264 — the only safe reading of "I
     * don't know" is what shipped before there was anything to know.
     */
    @Test
    fun `an unreadable codec list keeps the legacy order`() {
        assertEquals(VideoCodecPreference.LEGACY_ORDER, VideoCodecPreference.order(null))
        // …and is emphatically not the same answer as an empty set.
        assertEquals(listOf(H264), VideoCodecPreference.order(emptySet()))
    }

    /** Modern codecs keep their relative order; H.264 is always the floor. */
    @Test
    fun `partial hardware support keeps ranking and always ends in H264`() {
        assertEquals(listOf(HEVC, H264), VideoCodecPreference.order(setOf(HEVC, H264)))
        assertEquals(listOf(AV1, H264), VideoCodecPreference.order(setOf(AV1, H264)))
        assertEquals(
            listOf(AV1, HEVC, H264),
            VideoCodecPreference.order(setOf(HEVC, AV1, H264)),
        )
        assertEquals(listOf(VP9, H264), VideoCodecPreference.order(setOf(VP9, H264)))
    }

    /**
     * H.264 is appended whether or not the probe reported it. A device with no
     * hardware AVC is not a real device, but the floor should not depend on
     * that being true, and it must never be listed twice — a duplicate would
     * shift every match index after it.
     */
    @Test
    fun `H264 is the floor exactly once`() {
        assertEquals(listOf(HEVC, H264), VideoCodecPreference.order(setOf(HEVC)))
        val withH264 = VideoCodecPreference.order(setOf(HEVC, H264))
        assertEquals(1, withH264.count { it == H264 })
        assertEquals(1, VideoCodecPreference.LEGACY_ORDER.count { it == H264 })
    }

    /** MediaCodec reports types in mixed case on some devices. */
    @Test
    fun `mime matching is case-insensitive`() {
        assertEquals(
            listOf(HEVC, H264),
            VideoCodecPreference.order(setOf("Video/HEVC", "VIDEO/AVC")),
        )
    }

    /** Anything the device reports that we do not rank is simply ignored. */
    @Test
    fun `unranked codecs do not leak into the list`() {
        assertEquals(
            listOf(HEVC, H264),
            VideoCodecPreference.order(setOf(HEVC, H264, "video/mp4v-es", "video/3gpp")),
        )
    }
}
