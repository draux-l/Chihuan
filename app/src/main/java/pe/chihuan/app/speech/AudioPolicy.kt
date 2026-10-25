package pe.chihuan.app.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * Transient ducking audio focus around one burst of announcements.
 *
 * `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` asks other apps to lower their playback instead of stopping
 * it, which is exactly what a payment confirmation should do. A denied request is reported to the
 * caller and deliberately ignored: the announcement is spoken anyway.
 *
 * `AudioFocusRequest` exists from API 26; on API 24–25 the deprecated three-argument request is the
 * only option, so both paths are kept behind one mechanical version guard.
 */
class AudioPolicy(context: Context) {

    private val audioManager: AudioManager? =
        context.applicationContext.getSystemService(AudioManager::class.java)

    private val focusListener = AudioManager.OnAudioFocusChangeListener { }

    private val focusRequest: AudioFocusRequest? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
    } else {
        null
    }

    /** Requests transient ducking focus. Returns whether it was granted; never throws. */
    fun requestDuckFocus(): Boolean {
        val manager = audioManager ?: return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = focusRequest ?: return false
            manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    /** Abandons focus once the burst has drained. Safe to call without a prior request. */
    fun abandonFocus() {
        val manager = audioManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let(manager::abandonAudioFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(focusListener)
        }
    }
}
