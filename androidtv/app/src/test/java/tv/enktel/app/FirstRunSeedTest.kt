package tv.enktel.app

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.db.ProfileDao
import tv.enktel.app.data.prefs.SettingsStore
import tv.enktel.app.data.repo.DefaultLine
import tv.enktel.app.data.repo.PlaylistRepository
import tv.enktel.app.data.xtream.XtreamClient

/**
 * What a fresh install starts with: nothing.
 *
 * A build with no baked-in line used to seed the free-to-air playlist, so the
 * app opened on a few thousand public channels. Everyone installing this has
 * just paid for a subscription, and channels that are not theirs — which they
 * then have to work out how to replace — is a worse first screen than the one
 * asking for the line they are holding.
 *
 * The regression is silent: put the fallback back and nothing throws, the app
 * simply stops showing the sign-in screen to the people who need it. So it is
 * pinned here rather than left to a reading of `seedDefaultProfile`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirstRunSeedTest {

    /** Enough of the DAO for the two branches `seedDefaultProfile` takes. */
    private class FakeProfileDao(private val existing: Profile?) : ProfileDao {
        override suspend fun insert(p: Profile): Long = 1L
        override suspend fun update(p: Profile) = Unit
        override suspend fun delete(id: Long) = Unit
        override fun all(): Flow<List<Profile>> = flowOf(listOfNotNull(existing))
        override suspend fun byId(id: Long): Profile? = existing
        override suspend fun first(): Profile? = existing
    }

    private fun repo(existing: Profile? = null) = PlaylistRepository(
        dao = FakeProfileDao(existing),
        settings = SettingsStore(ApplicationProvider.getApplicationContext()),
        xtream = XtreamClient(OkHttpClient()),
    )

    @Test
    fun `a public build bakes in no credentials to seed with`() {
        // The premise of the test below. If a build ever ships with a line in
        // it, that line is published — see DefaultLine — and this fails first.
        assertFalse(
            "a distributed build must not carry credentials",
            DefaultLine.canSeed,
        )
    }

    @Test
    fun `a first run seeds no playlist at all`() = runBlocking {
        assertNull(
            "first run must land on the sign-in screen, not on somebody else's channels",
            repo().seedDefaultProfile(),
        )
    }

    @Test
    fun `a device that already has a profile is left alone`() = runBlocking {
        val mine = Profile(id = 7, name = "EnkTel", kind = "xtream", server = "https://x-api.cc")
        assertNull(repo(mine).seedDefaultProfile())
    }
}
