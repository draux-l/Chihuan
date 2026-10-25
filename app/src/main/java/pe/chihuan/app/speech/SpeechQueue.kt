package pe.chihuan.app.speech

import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Feeds announcements to the single [TtsAnnouncer] engine, in arrival order.
 *
 * The platform engine *is* the queue: every accepted utterance is handed over with
 * `TextToSpeech.QUEUE_ADD`, so it starts only after the previous one finishes and is never spoken
 * over. This class adds the three policies the engine has no opinion about:
 *
 * - transient ducking audio focus for the duration of a burst, abandoned when it drains;
 * - deferral while a call is in progress (an utterance must not *start* during a call), retried
 *   until the audio mode returns to normal so a payment is never dropped;
 * - parking while the engine is still initializing, and dropping only when it is unusable.
 *
 * All state is owned by the main thread. Public entry points may be called from any thread; they
 * post their work to the main looper, which also serialises concurrent notifications.
 */
class SpeechQueue(
    private val announcer: TtsAnnouncer,
    private val audioPolicy: AudioPolicy,
    private val callState: CallStateSource,
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Utterances accepted but not yet handed to the engine, in arrival order. */
    private val parked = ArrayDeque<String>()

    /** Utterances handed to the engine whose completion callback has not arrived yet. */
    private var outstanding = 0

    private var focusHeld = false
    private var retryScheduled = false

    /** Accepted announcements that are not finished yet, for display in the control panel. */
    @Volatile
    var pendingCount: Int = 0
        private set

    /** Invoked on the main thread whenever [pendingCount] changes. */
    var onPendingChanged: ((Int) -> Unit)? = null

    init {
        announcer.onUtteranceFinished = ::handleUtteranceFinished
    }

    /**
     * Accepts one utterance. It is spoken after everything already queued, and it is never dropped
     * because of a call or because the engine was still starting.
     */
    fun announce(text: String) {
        if (text.isBlank()) return
        onMain {
            parked.addLast(text)
            notifyPendingChanged()
            pump()
        }
    }

    /**
     * Hands parked utterances to the engine while both gates are open: no call is in progress and
     * the engine can speak. Anything held keeps its arrival order because it stays in [parked].
     */
    private fun pump() {
        while (parked.isNotEmpty()) {
            if (callState.isCallActive()) {
                scheduleRetry()
                return
            }

            when (announcer.status) {
                is TtsStatus.Ready -> startNext()
                TtsStatus.Initializing -> {
                    scheduleRetry()
                    return
                }

                is TtsStatus.Unavailable -> {
                    // The engine cannot speak at all; a parked payment would never produce audio.
                    Log.w(LOG_TAG, "Announcements dropped; the speech engine is unusable")
                    parked.clear()
                    notifyPendingChanged()
                    return
                }
            }
        }
    }

    private fun startNext() {
        val text = parked.removeFirst()

        if (!focusHeld) {
            // A denied request is ignored on purpose: a payment confirmation outranks audio manners.
            audioPolicy.requestDuckFocus()
            focusHeld = true
        }

        val utteranceId = announcer.speak(text)
        if (utteranceId == null) {
            // The engine refused a ready utterance: advance instead of retrying the same text.
            notifyPendingChanged()
            if (outstanding == 0) releaseFocus()
            return
        }

        outstanding++
        notifyPendingChanged()
    }

    private fun handleUtteranceFinished() {
        onMain {
            if (outstanding > 0) outstanding--
            notifyPendingChanged()

            if (outstanding == 0) {
                pump()
                if (outstanding == 0) releaseFocus()
            }
        }
    }

    private fun releaseFocus() {
        if (!focusHeld) return
        focusHeld = false
        audioPolicy.abandonFocus()
    }

    private fun notifyPendingChanged() {
        val count = parked.size + outstanding
        pendingCount = count
        onPendingChanged?.invoke(count)
    }

    private val retryGate = object : Runnable {
        override fun run() {
            retryScheduled = false
            pump()
        }
    }

    /**
     * Re-checks the gates after [GATE_RECHECK_MILLIS]. Polling the audio mode is what makes
     * deferral independent of any call-state callback, which the platform does not provide without
     * extra permissions.
     */
    private fun scheduleRetry() {
        if (retryScheduled) return
        retryScheduled = true
        mainHandler.postDelayed(retryGate, GATE_RECHECK_MILLIS)
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private companion object {
        const val LOG_TAG = "ChihuanSpeech"

        /** Two seconds: long enough to be cheap, short enough that a call's end is barely delayed. */
        const val GATE_RECHECK_MILLIS = 2_000L
    }
}
