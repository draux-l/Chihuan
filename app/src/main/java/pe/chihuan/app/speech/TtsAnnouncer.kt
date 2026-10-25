package pe.chihuan.app.speech

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/** Readiness of the platform text-to-speech engine, reported instead of thrown. */
sealed interface TtsStatus {
    /** The engine is still starting. */
    data object Initializing : TtsStatus

    /** The engine is usable and speaks the reported Spanish locale. */
    data class Ready(val locale: Locale) : TtsStatus

    /** The engine cannot speak Spanish on this device; [reason] drives the guidance copy. */
    data class Unavailable(val reason: Reason) : TtsStatus

    /** Why the wrapper could not reach a usable state. */
    enum class Reason {
        /** `TextToSpeech` failed to initialize, usually because no engine is installed. */
        ENGINE_INIT_FAILED,

        /** The engine reports no Spanish locale at all. */
        NO_SPANISH_LANGUAGE,

        /** A Spanish locale exists but this device has no language data for it. */
        LANGUAGE_DATA_MISSING,
    }
}

/**
 * Process-scoped wrapper around the single platform [TextToSpeech] engine.
 *
 * There is exactly one instance per process, owned by
 * [pe.chihuan.app.ChihuanApplication], so the listener service and the control panel share the same
 * engine and the same platform queue. Nothing releases it: Android reclaims it with the process, and
 * an explicitly released wrapper could never be used again while the Application outlives every
 * Activity.
 *
 * Responsibilities stop at the engine: locale selection, speech parameters, readiness reporting and
 * handing one utterance to the platform queue. Burst ordering, audio focus and call deferral live in
 * [SpeechQueue].
 *
 * Failure is reported through [status] and [onStatusChanged]; nothing here throws into the caller.
 */
class TtsAnnouncer(context: Context) {

    private val appContext: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var utteranceCounter = 0

    /** Invoked on the main thread whenever [status] changes. Replaced by the visible screen. */
    var onStatusChanged: ((TtsStatus) -> Unit)? = null

    /** Invoked on the main thread when the engine finishes, fails or stops an utterance. */
    var onUtteranceFinished: (() -> Unit)? = null

    var status: TtsStatus = TtsStatus.Initializing
        private set(value) {
            field = value
            onStatusChanged?.invoke(value)
        }

    /** Starts the engine once. Safe to call repeatedly. */
    fun initialize() {
        if (engine != null) return

        engine = TextToSpeech(appContext) { initStatus ->
            mainHandler.post { handleInit(initStatus) }
        }
    }

    /**
     * Hands [text] to the platform queue, which speaks it after every utterance already queued.
     * Returns the utterance id, or null when the engine is not ready and nothing was queued.
     */
    fun speak(text: String): String? {
        val tts = engine
        val current = status
        if (tts == null || current !is TtsStatus.Ready) {
            Log.w(LOG_TAG, "Utterance skipped; engine status is $current")
            return null
        }

        val utteranceId = "chihuan-utterance-${utteranceCounter++}"
        val result = tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            Log.w(LOG_TAG, "The engine refused utterance $utteranceId")
            return null
        }
        return utteranceId
    }

    private fun handleInit(initStatus: Int) {
        val tts = engine
        if (initStatus != TextToSpeech.SUCCESS || tts == null) {
            status = TtsStatus.Unavailable(TtsStatus.Reason.ENGINE_INIT_FAILED)
            return
        }

        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        tts.setSpeechRate(SPEECH_RATE)
        tts.setPitch(PITCH)
        tts.setOnUtteranceProgressListener(progressListener)

        val selectedVoice = selectVoice(tts)
        if (selectedVoice != null) {
            // `isLanguageAvailable` reports "supported" for a locale the engine can map, even when
            // only a neighbouring variant has installed data. Selecting the actual Voice is the
            // only way to know which locale will really speak.
            tts.voice = selectedVoice
            status = TtsStatus.Ready(selectedVoice.locale)
            return
        }

        val language = SPANISH_CANDIDATES.firstOrNull { candidate ->
            tts.isLanguageAvailable(candidate) >= TextToSpeech.LANG_AVAILABLE
        }
        if (language == null) {
            status = TtsStatus.Unavailable(TtsStatus.Reason.NO_SPANISH_LANGUAGE)
            return
        }

        if (tts.setLanguage(language) < TextToSpeech.LANG_AVAILABLE) {
            status = TtsStatus.Unavailable(TtsStatus.Reason.LANGUAGE_DATA_MISSING)
            return
        }

        // The engine accepted the request but may still synthesize with another variant, so the
        // reported status is the language the engine actually settled on.
        status = TtsStatus.Ready(tts.voice?.locale ?: language)
    }

    /**
     * Picks the installed Spanish voice to use, preferring the requested locale order and then an
     * offline voice over one that needs a network round trip.
     */
    private fun selectVoice(tts: TextToSpeech): Voice? {
        val voices = try {
            tts.voices
        } catch (exception: RuntimeException) {
            Log.w(LOG_TAG, "Installed voices could not be read: ${exception.javaClass.simpleName}")
            null
        } ?: return null

        for (candidate in SPANISH_CANDIDATES) {
            val sameLanguage = voices.filter { voice ->
                voice.locale.language.equals(candidate.language, ignoreCase = true)
            }
            if (sameLanguage.isEmpty()) continue

            val exact = sameLanguage.filter {
                it.locale.country.equals(candidate.country, ignoreCase = true)
            }
            val pool = exact.ifEmpty { sameLanguage }
            return pool.sortedWith(
                compareBy<Voice> { it.isNetworkConnectionRequired }
                    .thenByDescending { it.quality },
            ).firstOrNull()
        }
        return null
    }

    /**
     * Completion is reported once per utterance. A synthesis error is reported exactly like a
     * completion so the queue advances; the same utterance is never re-spoken.
     */
    private fun notifyUtteranceFinished() {
        mainHandler.post { onUtteranceFinished?.invoke() }
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) = notifyUtteranceFinished()

        @Deprecated("Deprecated by the platform; the two-argument overload carries the error code")
        override fun onError(utteranceId: String?) = notifyUtteranceFinished()

        override fun onError(utteranceId: String?, errorCode: Int) = notifyUtteranceFinished()

        override fun onStop(utteranceId: String?, interrupted: Boolean) = notifyUtteranceFinished()
    }

    private companion object {
        const val LOG_TAG = "ChihuanTts"

        /** Slightly slower than default so amounts and names stay intelligible. */
        const val SPEECH_RATE = 0.9f
        const val PITCH = 1.0f

        val SPANISH_CANDIDATES: List<Locale> = listOf(
            Locale.forLanguageTag("es-PE"),
            Locale.forLanguageTag("es-US"),
            Locale.forLanguageTag("es-ES"),
        )
    }
}
