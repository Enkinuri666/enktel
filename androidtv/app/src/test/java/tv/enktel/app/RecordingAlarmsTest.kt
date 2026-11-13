package tv.enktel.app

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import tv.enktel.app.data.db.Recording
import tv.enktel.app.dvr.RecordingAlarmReceiver
import tv.enktel.app.dvr.RecordingAlarms
import tv.enktel.app.dvr.RecordingOutcome
import tv.enktel.app.dvr.RecordingService
import java.io.File

/**
 * A scheduled recording starts at its time with the app closed.
 *
 * From Android 12 an app that is not on screen may not start a foreground
 * service, and recording is one. The scheduler was a WorkManager job that
 * asked for the service at the right moment and was refused, on every recent
 * device, whenever the viewer was not looking at the app — which is when a
 * scheduled recording happens.
 *
 * An exact alarm is one of the things Android does let start a foreground
 * service from the background. These pin how one is set, when one is not, and
 * what the viewer is told in between.
 *
 * What cannot be tested here is the part that matters most: that Android
 * honours the alarm and admits the service. That is the platform's behaviour
 * on a real device, and nothing on a desktop JVM stands in for it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RecordingAlarmsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val now = 1_800_000_000_000L
    private val inAnHour = now + 3_600_000

    // ---- setting one ----

    @Test
    fun `with the permission, an exact alarm stands for the recording`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertTrue(RecordingAlarms.arm(context, recordingId = 7, atMs = inAnHour, nowMs = now))

        val alarm = alarms.scheduledAlarms.single()
        assertEquals(inAnHour, alarm.triggerAtMs)
        assertEquals("wakes the device: a recording at 3 a.m. is still a recording", AlarmManager.RTC_WAKEUP, alarm.type)
        assertTrue("fires while dozing, which the WorkManager job never promised", alarm.isAllowWhileIdle)
    }

    @Test
    fun `without the permission nothing is set and nothing throws`() {
        // Android 14: off until the viewer turns it on. setExact would throw
        // SecurityException here; the caller is told false and the WorkManager
        // job remains the way the recording starts.
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(RecordingAlarms.exactAllowed(context))
        assertFalse(RecordingAlarms.arm(context, 7, inAnHour, now))
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test
    fun `before Android 12 there is no permission to ask for`() {
        // Asked of the rule rather than of an emulated Android 11, so this
        // suite does not need a second platform image downloaded to run.
        assertFalse(RecordingAlarms.needsPermission(23))
        assertFalse(RecordingAlarms.needsPermission(30))
        assertTrue(RecordingAlarms.needsPermission(31))
        assertTrue(RecordingAlarms.needsPermission(36))
    }

    @Test
    fun `a start time already past gets no alarm`() {
        // "Record this", pressed on a programme that is on now. The job starts
        // it at once with the app on screen; an alarm in the past would fire
        // immediately too and race it.
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertFalse(RecordingAlarms.arm(context, 7, atMs = now - 1, nowMs = now))
        assertFalse(RecordingAlarms.arm(context, 7, atMs = now, nowMs = now))
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test
    fun `two recordings keep two alarms`() {
        // Two intents that differ only in their extras are the same intent to
        // the alarm manager: the second would replace the first.
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        RecordingAlarms.arm(context, 7, inAnHour, now)
        RecordingAlarms.arm(context, 8, inAnHour + 60_000, now)
        assertEquals(listOf(inAnHour, inAnHour + 60_000), alarms.scheduledAlarms.map { it.triggerAtMs }.sorted())
    }

    @Test
    fun `setting one again moves it rather than adding another`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        RecordingAlarms.arm(context, 7, inAnHour, now)
        RecordingAlarms.arm(context, 7, inAnHour + 600_000, now)
        assertEquals(listOf(inAnHour + 600_000), alarms.scheduledAlarms.map { it.triggerAtMs })
    }

    @Test
    fun `cancelling removes only that recording's alarm`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        RecordingAlarms.arm(context, 7, inAnHour, now)
        RecordingAlarms.arm(context, 8, inAnHour + 60_000, now)
        RecordingAlarms.cancel(context, 7)
        assertEquals(listOf(inAnHour + 60_000), alarms.scheduledAlarms.map { it.triggerAtMs })
    }

    // ---- when it goes off ----

    @Test
    fun `the alarm asks for the recorder, with the recording's id`() {
        RecordingAlarmReceiver().onReceive(
            context,
            Intent(RecordingAlarms.ACTION_START).putExtra(RecordingService.EXTRA_ID, 7L),
        )
        val started = shadowOf(context as Application).nextStartedService
        assertNotNull("nothing was started", started)
        assertEquals(RecordingService::class.java.name, started.component?.className)
        assertEquals(RecordingService.ACTION_START, started.action)
        assertEquals(7L, started.getLongExtra(RecordingService.EXTRA_ID, -1))
    }

    @Test
    fun `an alarm with no recording in it starts nothing`() {
        RecordingAlarmReceiver().onReceive(context, Intent(RecordingAlarms.ACTION_START))
        RecordingAlarmReceiver().onReceive(context, Intent("something.else"))
        RecordingAlarmReceiver().onReceive(context, null)
        assertNull(shadowOf(context as Application).nextStartedService)
    }

    // ---- which rows the recorder acts on ----

    @Test
    fun `only a waiting or just-created recording is started`() {
        // The alarm and the WorkManager job both exist, on purpose, and both
        // ask. The second ask, or one that arrives after a cancel, must find
        // a row it is not allowed to touch.
        assertTrue(RecordingOutcome.recordable("SCHEDULED"))
        assertTrue("'record now' inserts its row already marked", RecordingOutcome.recordable("RECORDING"))
        for (status in listOf("DONE", "FAILED", "CANCELLED", "", "scheduled")) {
            assertFalse(status, RecordingOutcome.recordable(status))
        }
    }

    // ---- putting them back ----

    /**
     * A graph whose start-up work has finished.
     *
     * Building an AppGraph starts the same sweep the app runs at launch, on a
     * background thread, and that sweep ends by re-arming every scheduled
     * recording — against the real clock. These tests use a fixed "now" in
     * the future, so a row they call already due is, by the real clock, still
     * to come. When the sweep happened to run after the rows were inserted it
     * armed a third alarm and the count below was wrong: green on the machine
     * this was written on, red twice running on CI. Waiting for the sweep
     * first leaves nothing in the background to race.
     */
    private suspend fun settledGraph(): AppGraph =
        AppGraph(context as Application).also { it.staleRecordingsClosed.join() }

    @Test
    fun `re-arming covers every recording still waiting, and only those`() = runBlocking {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val dao = settledGraph().db.recordingDao()
        fun row(status: String, startMs: Long) = Recording(
            profileId = 1, title = "t", channelName = "c", streamUrl = "http://example.invalid/1.ts",
            status = status, startMs = startMs, endMs = startMs + 3_600_000,
        )
        dao.insert(row("SCHEDULED", inAnHour))
        dao.insert(row("SCHEDULED", inAnHour + 7_200_000))
        dao.insert(row("SCHEDULED", now - 60_000))      // due already: the job's business
        dao.insert(row("CANCELLED", inAnHour))
        dao.insert(row("DONE", now - 86_400_000))

        assertEquals(2, RecordingAlarms.rearmAll(context, dao, nowMs = now))
        assertEquals(
            listOf(inAnHour, inAnHour + 7_200_000),
            alarms.scheduledAlarms.map { it.triggerAtMs }.sorted(),
        )
        // Run twice — on app start and again when the screen opens — it must
        // leave the same two, not four.
        RecordingAlarms.rearmAll(context, dao, nowMs = now)
        assertEquals(2, alarms.scheduledAlarms.size)
    }

    @Test
    fun `the start-up sweep re-arms what is waiting when the app starts`() = runBlocking {
        // What a reboot looks like from here: rows in the database, no alarms.
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val first = settledGraph()
        val soon = System.currentTimeMillis() + 3_600_000
        first.db.recordingDao().insert(
            Recording(
                profileId = 1, title = "t", channelName = "c", streamUrl = "http://example.invalid/1.ts",
                status = "SCHEDULED", startMs = soon, endMs = soon + 3_600_000,
            ),
        )
        assertTrue("nothing has armed it yet", alarms.scheduledAlarms.isEmpty())

        // The app starts again. Nobody opens Recordings; nobody schedules anything.
        settledGraph()
        assertEquals(listOf(soon), alarms.scheduledAlarms.map { it.triggerAtMs })
    }

    @Test
    fun `re-arming without the permission sets nothing`() = runBlocking {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val dao = settledGraph().db.recordingDao()
        dao.insert(
            Recording(
                profileId = 1, title = "t", channelName = "c", streamUrl = "http://example.invalid/1.ts",
                status = "SCHEDULED", startMs = inAnHour, endMs = inAnHour + 3_600_000,
            ),
        )
        assertEquals(0, RecordingAlarms.rearmAll(context, dao, nowMs = now))
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    // ---- what the viewer is sent to, and what the manifest promises ----

    @Test
    fun `the settings screen asked for is this app's own`() {
        val intent = RecordingAlarms.settingsIntent(context)!!
        assertEquals(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
        assertTrue(
            "launched from wherever the button is, which may not be an activity",
            intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0,
        )
    }

    @Test
    fun `the manifest asks for the permission and keeps the receiver private`() {
        val manifest = listOf(
            "src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml",
            "androidtv/app/src/main/AndroidManifest.xml",
        ).map(::File).firstOrNull { it.isFile } ?: return
        val xml = manifest.readText()
        assertTrue(
            "without this, canScheduleExactAlarms() is false for ever and no alarm is ever set",
            xml.contains("""<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />"""),
        )
        assertFalse(
            "USE_EXACT_ALARM is for alarm clocks and calendars; asking for it invites a store rejection",
            xml.contains("android.permission.USE_EXACT_ALARM"),
        )
        val receiver = Regex("""<receiver\s[^>]*android:name="\.dvr\.RecordingAlarmReceiver"[^>]*>""").find(xml)
        assertNotNull("the alarm has nowhere to land", receiver)
        assertTrue(
            "anything on the device could start a recording through an exported receiver",
            receiver!!.value.contains("""android:exported="false""""),
        )
    }
}
