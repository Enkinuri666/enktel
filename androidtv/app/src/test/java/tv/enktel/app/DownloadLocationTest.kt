package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.download.DownloadLocation
import tv.enktel.app.data.download.DownloadLocation.Reveal

class DownloadLocationTest {

    private val privatePath =
        "/storage/emulated/0/Android/data/tv.enktel.app.mobile/files/Movies/The Weight.mkv"

    @Test fun `a folder the viewer picked can be opened`() {
        val r = DownloadLocation.reveal(
            "content://com.android.externalstorage.documents/tree/primary%3AMovies/document/x",
            36,
        )
        assertTrue(r.toString(), r is Reveal.Folder)
    }

    @Test fun `the app's own directory is sealed on Android 11 and later`() {
        // The whole reason the button cannot simply open a folder.
        val r = DownloadLocation.reveal(privatePath, 36)
        assertTrue(r.toString(), r is Reveal.Sealed)
        val why = (r as Reveal.Sealed).because
        assertTrue(why, why.contains("no file manager"))
        // It has to say what to do about it, not only that it cannot be done.
        assertTrue(why, why.contains("Pick a download folder"))
    }

    @Test fun `the same path is reachable on Android 10 and earlier`() {
        // A real difference, not a version check for its own sake: before
        // Android 11 a file manager could open this path perfectly well.
        assertTrue(DownloadLocation.reveal(privatePath, 29) is Reveal.Path)
        assertTrue(DownloadLocation.reveal(privatePath, 23) is Reveal.Path)
        assertTrue(DownloadLocation.reveal(privatePath, 30) is Reveal.Sealed)
    }

    @Test fun `an ordinary path is reachable on any version`() {
        val p = "/storage/emulated/0/Movies/The Weight.mkv"
        assertTrue(DownloadLocation.reveal(p, 36) is Reveal.Path)
        assertTrue(DownloadLocation.reveal(p, 23) is Reveal.Path)
    }

    @Test fun `a download with nothing written yet is not offered a folder`() {
        assertEquals(Reveal.NotYet, DownloadLocation.reveal("", 36))
        assertEquals(Reveal.NotYet, DownloadLocation.reveal("   ", 36))
    }

    @Test fun `the sealed case is labelled as a question, not as an action`() {
        // Calling it "Show folder" and then not showing a folder is the fault
        // this label exists to avoid.
        val sealed = DownloadLocation.reveal(privatePath, 36)
        val folder = DownloadLocation.reveal("content://x/tree/y", 36)
        assertEquals("📁 Where is this?", DownloadLocation.buttonLabel(sealed))
        assertEquals("📁 Show folder", DownloadLocation.buttonLabel(folder))
    }
}
