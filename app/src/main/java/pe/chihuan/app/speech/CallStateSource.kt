package pe.chihuan.app.speech

import android.content.Context
import android.media.AudioManager

/**
 * Detects whether a call is in progress.
 *
 * `AudioManager.mode` is the only signal used: any mode other than [AudioManager.MODE_NORMAL] means
 * the audio path belongs to a call, and it covers both telephony calls and VoIP calls. It needs no
 * permission, so the app never asks for phone state.
 */
class CallStateSource(context: Context) {

    private val audioManager: AudioManager? =
        context.applicationContext.getSystemService(AudioManager::class.java)

    /** True while the device is in any call mode. A missing audio service reads as "no call". */
    fun isCallActive(): Boolean {
        val mode = audioManager?.mode ?: return false
        return mode != AudioManager.MODE_NORMAL
    }
}
