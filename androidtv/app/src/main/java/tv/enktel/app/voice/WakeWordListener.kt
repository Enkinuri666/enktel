package tv.enktel.app.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * "Hey Enki" wake-word listener.
 *
 * Architecture — why this is not just a SpeechRecognizer restart loop:
 *
 * The naïve approach ("restart SpeechRecognizer every time it times out")
 * chirps the mic-in-use indicator on every restart.  Even at 20-second
 * intervals that is a beep-beep-beep for anyone who leaves the toggle on.
 *
 * This class instead treats the problem as two stages:
 *
 *   Stage 1 — SILENT VAD GATE.  A raw [AudioRecord] stream stays open at
 *     16 kHz on a background dispatcher, reading ~100 ms buffers and
 *     computing RMS energy.  AudioRecord does not play any OEM
 *     "listening" chirp; the mic privacy indicator does light up, but no
 *     audible sound is triggered.  In a silent room this is the ONLY
 *     thing running — zero SpeechRecognizer calls, zero beeps.
 *
 *   Stage 2 — RECOGNIZE ON SPEECH.  When the VAD sees a run of frames
 *     above the speech threshold (default: 300 ms of energy above the
 *     noise floor), it releases AudioRecord and hands off to
 *     SpeechRecognizer for exactly one utterance.  If that utterance
 *     contains the wake phrase, we fire onWake and stop.  Otherwise we
 *     release SpeechRecognizer and re-open the VAD gate.
 *
 * Net result: the audible chirp fires at most once per real speech
 * event, and never during silence.  Playback is still guarded by the
 * three-layer suppression path (paused flag + AudioManager.isMusicActive
 * + stale-callback drop) that catches self-triggering from the app's own
 * TV audio.
 *
 * A dedicated on-device engine (Porcupine, openWakeWord) would remove
 * even that single chirp; noted as future work.
 */
class WakeWordListener(
    private val context: Context,
    private val onWake: (payload: String) -> Unit,
) {
    val active = mutableStateOf(false)
    val hearingMeter = mutableStateOf(0f) // 0..1, updates on RMS

    // ---- VAD tuning ----
    private companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_MS = 100
        const val FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000
        // RMS thresholds (16-bit PCM).  Empirically ~200-400 is quiet room
        // ambient, ~1500+ is speech at typical phone-in-hand distance.
        const val SPEECH_RMS = 1200
        // How many consecutive speech frames before we hand off.  Guards
        // against a door slamming or an ad hitting a peak.
        const val SPEECH_FRAMES_TO_TRIGGER = 3   // ~300 ms
        // How long to wait between the VAD gate seeing silence and being
        // willing to re-arm after a recogniser turn.
        const val COOLDOWN_MS = 400L
    }

    private var scope: CoroutineScope? = null
    private var vadJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var recognizer: SpeechRecognizer? = null
    private var recognizerBusy: Boolean = false
    private var lastUnpauseAt: Long = 0L
    private val audio: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    /** Caller-driven pause. Flipped true when a Player screen is on. */
    @Volatile var paused: Boolean = false

    private fun audioActive(): Boolean = try { audio.isMusicActive } catch (_: Throwable) { false }
    private fun suppressed(): Boolean = paused || audioActive()

    fun start() {
        if (active.value) return
        if (!hasMicPermission()) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        active.value = true
        scope = MainScope()
        startVadGate()
    }

    fun stop() {
        active.value = false
        vadJob?.cancel(); vadJob = null
        releaseAudioRecord()
        releaseRecognizer()
        scope?.cancel(); scope = null
    }

    fun applyPause(p: Boolean) {
        if (paused == p) return
        paused = p
        if (p) {
            releaseAudioRecord()
            releaseRecognizer()
        } else if (active.value) {
            lastUnpauseAt = SystemClock.elapsedRealtime()
            startVadGate()
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun releaseAudioRecord() {
        try { audioRecord?.stop() } catch (_: Throwable) {}
        try { audioRecord?.release() } catch (_: Throwable) {}
        audioRecord = null
    }

    private fun releaseRecognizer() {
        try { recognizer?.stopListening() } catch (_: Throwable) {}
        try { recognizer?.destroy() } catch (_: Throwable) {}
        recognizer = null
        recognizerBusy = false
    }

    /**
     * Background VAD loop.  Reads raw PCM in 100 ms chunks, tracks how
     * many consecutive chunks contain speech-level energy, and hands off
     * to [triggerRecognizer] once the threshold is crossed.  Suppression
     * checks (pause / playback audio) run on every frame so we don't
     * fight the app's own speakers.
     */
    private fun startVadGate() {
        val s = scope ?: return
        if (!active.value || paused) return
        vadJob?.cancel()
        releaseRecognizer()
        vadJob = s.launch(Dispatchers.IO) {
            // Small pause after unpause so the audio pipeline has time to
            // release cleanly before we grab the mic again.
            val since = SystemClock.elapsedRealtime() - lastUnpauseAt
            val grace = (COOLDOWN_MS - since).coerceAtLeast(0L)
            if (grace > 0) delay(grace)
            if (!isActive || !active.value || paused) return@launch

            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuf <= 0) return@launch
            val ar = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    (minBuf * 2).coerceAtLeast(FRAME_SAMPLES * 4),
                )
            } catch (_: Throwable) { return@launch }
            if (ar.state != AudioRecord.STATE_INITIALIZED) {
                try { ar.release() } catch (_: Throwable) {}
                return@launch
            }
            audioRecord = ar
            try { ar.startRecording() } catch (_: Throwable) {
                releaseAudioRecord(); return@launch
            }

            val frame = ShortArray(FRAME_SAMPLES)
            var speechFrames = 0
            while (isActive && active.value && !suppressed()) {
                val n = try { ar.read(frame, 0, frame.size) } catch (_: Throwable) { -1 }
                if (n <= 0) break
                val rms = computeRms(frame, n)
                // Normalise for the UI meter — coarse but responsive.
                hearingMeter.value = (rms / 3000f).coerceIn(0f, 1f)
                if (rms >= SPEECH_RMS) {
                    speechFrames++
                    if (speechFrames >= SPEECH_FRAMES_TO_TRIGGER) {
                        // Real speech detected: release the raw mic and
                        // fire the recogniser exactly once.
                        releaseAudioRecord()
                        withContext(Dispatchers.Main) { triggerRecognizer() }
                        return@launch
                    }
                } else if (speechFrames > 0) {
                    speechFrames = 0
                }
            }
            // Loop exited without triggering — either we were stopped,
            // paused, or the audio pipeline blocked.  Release cleanly.
            releaseAudioRecord()
            // If suppression is what stopped us, wait for the applyPause
            // callback to re-arm; otherwise re-arm ourselves after a
            // small pause.
            if (isActive && active.value && !paused && !audioActive()) {
                delay(COOLDOWN_MS)
                startVadGate()
            }
        }
    }

    /**
     * Fires SpeechRecognizer for exactly one utterance and interprets the
     * result.  This is the ONLY place in the class that opens
     * SpeechRecognizer, so any OEM listen-chirp fires at most once per
     * detected speech event — never in a silent room.
     */
    private fun triggerRecognizer() {
        if (!active.value || paused || audioActive()) {
            // Suppression flipped between the VAD detection and the main
            // thread hop; abort cleanly and re-arm the gate.
            if (active.value && !paused) startVadGate()
            return
        }
        if (recognizerBusy) return
        recognizerBusy = true

        val r = createRecognizer()
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                hearingMeter.value = (rmsdB.coerceIn(-2f, 10f) / 10f).coerceIn(0f, 1f)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    stop(); return
                }
                releaseRecognizer()
                if (active.value && !paused) startVadGate()
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {
                if (suppressed()) return
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                val payload = extractPayload(text) ?: return
                releaseRecognizer()
                handleWake(payload)
            }
            override fun onResults(results: Bundle?) {
                if (suppressed()) {
                    releaseRecognizer(); return
                }
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                val payload = extractPayload(text)
                releaseRecognizer()
                if (payload != null) handleWake(payload)
                else if (active.value && !paused) startVadGate()
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L)
        }
        try { r.startListening(intent) } catch (_: Throwable) {
            releaseRecognizer()
            if (active.value && !paused) startVadGate()
        }
    }

    /**
     * Prefer the on-device recogniser on API 31+ where available — it
     * runs without a cloud round-trip and is quieter on most OEM builds.
     * Fall back to the classic constructor otherwise.
     */
    private fun createRecognizer(): SpeechRecognizer {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val svc = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                if (svc != null) return svc
            } catch (_: Throwable) { /* fall through */ }
        }
        return SpeechRecognizer.createSpeechRecognizer(context)
    }

    private fun computeRms(frame: ShortArray, n: Int): Int {
        if (n <= 0) return 0
        var acc = 0.0
        for (i in 0 until n) {
            val v = frame[i].toInt()
            acc += (v * v).toDouble()
        }
        return sqrt(acc / n).toInt()
    }

    private fun handleWake(payload: String) {
        if (suppressed()) return
        stop()
        onWake(payload)
    }

    private fun extractPayload(text: String): String? {
        val t = text.lowercase().trim()
        if (t.isEmpty()) return null
        val patterns = listOf(
            "hey enki", "hi enki", "hey inky", "hi inky",
            "enkitel", "hey enkitel", "enki",
        )
        for (p in patterns) {
            val idx = t.indexOf(p)
            if (idx >= 0) return t.substring(idx + p.length).trim(',', '.', '!', '?', ' ')
        }
        return null
    }
}
