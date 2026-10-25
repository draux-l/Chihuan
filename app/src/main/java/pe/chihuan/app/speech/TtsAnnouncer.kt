package pe.chihuan.app.speech

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
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
 * Small wrapper around the platform [TextToSpeech] engine.
 *
 * Responsibilities stop at the engine: locale selection, speech parameters, readiness reporting
 * and starting an utterance. Announcement composition and queueing belong to later work.
 *
 * Failure is reported through [status] and [onStatusChanged]; nothing here throws into the UI.
 */
class TtsAnnouncer(context: Context) {

    private val appContext: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var released = false
    private var utteranceCounter = 0

    /** Invoked on the main thread whenever [status] changes. */
    var onStatusChanged: ((TtsStatus) -> Unit)? = null

    var status: TtsStatus = TtsStatus.Initializing
        private set(value) {
            field = value
            onStatusChanged?.invoke(value)
        }

    /** Starts the engine once. Safe to call repeatedly. */
    fun initialize() {
        if (engine != null || released) return

        engine = TextToSpeech(appContext) { initStatus ->
            mainHandler.post { handleInit(initStatus) }
        }
    }

    /**
     * Hands [text] to the engine for the current utterance. Returns false when the engine is not
     * ready, in which case [status] already explains why.
     */
    fun speak(text: String): Boolean {
        val tts = engine
        val current = status
        if (released || tts == null || current !is TtsStatus.Ready) {
            Log.w(LOG_TAG, "Utterance skipped; engine status is $current")
            return false
        }

        val utteranceId = "chihuan-utterance-${utteranceCounter++}"
        val result = tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
        return result == TextToSpeech.SUCCESS
    }

    /** Releases the native engine. The wrapper cannot be reused afterwards. */
    fun release() {
        released = true
        onStatusChanged = null
        engine?.stop()
        engine?.shutdown()
        engine = null
    }

    private fun handleInit(initStatus: Int) {
        val tts = engine
        if (released) return
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

        status = TtsStatus.Ready(language)
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
