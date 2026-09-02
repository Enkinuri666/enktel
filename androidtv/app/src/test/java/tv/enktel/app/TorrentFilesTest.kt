package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.debrid.RealDebridClient
import tv.enktel.app.data.debrid.TorrentFiles

class TorrentFilesTest {

    private fun file(id: Int, path: String, gb: Double) =
        RealDebridClient.TorrentFile(id, path, (gb * 1_000_000_000).toLong(), false)

    @Test fun `video extensions are recognised regardless of case`() {
        assertTrue(TorrentFiles.isVideo("/A Film/a.film.2019.MKV"))
        assertTrue(TorrentFiles.isVideo("show.s01e01.mp4"))
        assertFalse(TorrentFiles.isVideo("/A Film/a.film.2019.nfo"))
        assertFalse(TorrentFiles.isVideo("/A Film/Subs/english.srt"))
    }

    @Test fun `a dot in the folder name does not supply the extension`() {
        // The folder ends in ".2019.1080p.BluRay.x264-GRP" and the file has no
        // extension at all; reading the whole path would call it an x264 file.
        assertFalse(TorrentFiles.isVideo("Film.2019.1080p.BluRay.x264-GRP/readme"))
        assertTrue(TorrentFiles.isVideo("Film.2019.1080p.BluRay.x264-GRP/film.mkv"))
    }

    @Test fun `sample is a word, not a substring`() {
        assertTrue(TorrentFiles.isSample("/Film/sample.mkv", 50_000_000, 8_000_000_000))
        assertTrue(TorrentFiles.isSample("/Film/Film-SAMPLE.mkv", 50_000_000, 8_000_000_000))
        assertTrue(TorrentFiles.isSample("/Film/Sample/clip.mkv", 50_000_000, 8_000_000_000))
        // A title that happens to contain the word is not a sample, and the
        // folder it sits in is not a sample folder.
        assertFalse(TorrentFiles.isSample("/Free.Samples.2011/film.mkv", 8_000_000_000, 8_000_000_000))
        assertFalse(TorrentFiles.isSample("/Free.Samples.2011/Free.Samples.2011.mkv", 8_000_000_000, 8_000_000_000))
    }

    @Test fun `the biggest video is never the sample`() {
        // Otherwise a film whose own filename carries the word disappears from
        // its own torrent.
        assertFalse(TorrentFiles.isSample("/Sample.Text.2004/sample.text.mkv", 8_000_000_000, 8_000_000_000))
    }

    @Test fun `a small file is only a sample beside a much bigger one`() {
        assertTrue(TorrentFiles.isSample("/Film/extra.mkv", 100_000_000, 8_000_000_000))
        // Same size, but nothing in the torrent is large — so it is the film.
        assertFalse(TorrentFiles.isSample("/Film/extra.mkv", 100_000_000, 120_000_000))
    }

    @Test fun `a film picks the film and drops the clutter`() {
        val files = listOf(
            file(0, "Film.2019/Film.2019.mkv", 8.0),
            file(1, "Film.2019/sample.mkv", 0.05),
            file(2, "Film.2019/Film.2019.nfo", 0.0001),
            file(3, "Film.2019/Subs/en.srt", 0.0001),
        )
        assertEquals(listOf(0), TorrentFiles.suggested(files))
    }

    @Test fun `a season pack keeps every episode`() {
        val files = (1..10).map { file(it, "Show.S01/Show.S01E%02d.mkv".format(it), 2.0) }
        assertEquals((1..10).toList(), TorrentFiles.suggested(files))
    }

    @Test fun `a torrent of unrecognised files falls back to everything`() {
        // Refusing here would leave nothing ticked, and Real-Debrid refuses an
        // empty selection — so the viewer would be stuck rather than merely
        // over-fetching.
        val files = listOf(file(0, "album/track1.flac", 0.3), file(1, "album/track2.flac", 0.3))
        assertEquals(listOf(0, 1), TorrentFiles.suggested(files))
    }

    @Test fun `a torrent whose files all look like samples still offers them`() {
        // Sizes are unknown here, so the size guard cannot save anything and
        // every file matches the name check — the fallback is what stops the
        // picker opening with nothing ticked.
        val files = listOf(file(0, "Film/a/sample.mkv", 0.0), file(1, "Film/b/sample.mkv", 0.0))
        assertEquals(listOf(0, 1), TorrentFiles.suggested(files))
    }

    @Test fun `no files means no selection`() {
        assertTrue(TorrentFiles.suggested(emptyList()).isEmpty())
    }

    @Test fun `the name shown is the filename, not the path`() {
        assertEquals("Film.2019.mkv", file(0, "/Film.2019/Film.2019.mkv", 8.0).name)
    }

    private fun sel(id: Int, path: String, gb: Double, selected: Boolean) =
        RealDebridClient.TorrentFile(id, path, (gb * 1_000_000_000).toLong(), selected)

    @Test fun `links pair with the files that were actually fetched`() {
        // Real-Debrid returns one link per *selected* file, in order — so an
        // unselected episode must not shift the names onto the wrong links.
        val files = listOf(
            sel(0, "Show.S01/Show.S01E01.mkv", 2.0, true),
            sel(1, "Show.S01/Show.S01E02.mkv", 2.0, false),
            sel(2, "Show.S01/Show.S01E03.mkv", 2.0, true),
        )
        val out = TorrentFiles.playable(files, listOf("https://rd/1", "https://rd/3"))
        assertEquals(listOf("Show.S01E01.mkv", "Show.S01E03.mkv"), out.map { it.name })
        assertEquals(listOf("https://rd/1", "https://rd/3"), out.map { it.link })
    }

    @Test fun `a mismatch gives up on names rather than guessing wrong ones`() {
        // Pairing by index anyway would put the wrong episode's name on the
        // wrong link, which looks right and is worse than no name at all.
        val files = listOf(
            sel(0, "Show.S01/Show.S01E01.mkv", 2.0, true),
            sel(1, "Show.S01/Show.S01E02.mkv", 2.0, true),
        )
        val out = TorrentFiles.playable(files, listOf("https://rd/1"))
        assertEquals(listOf("File 1"), out.map { it.name })
        assertEquals(listOf("https://rd/1"), out.map { it.link })
    }

    @Test fun `blank links are not offered as things to play`() {
        val files = listOf(sel(0, "Film/film.mkv", 8.0, true))
        assertEquals(1, TorrentFiles.playable(files, listOf("https://rd/1", "", "  ")).size)
    }

    @Test fun `a single-file torrent pairs as one playable`() {
        val files = listOf(sel(0, "Film.2019/Film.2019.mkv", 8.0, true))
        val out = TorrentFiles.playable(files, listOf("https://rd/1"))
        assertEquals("Film.2019.mkv", out.single().name)
        assertEquals(8_000_000_000L, out.single().bytes)
    }
}
