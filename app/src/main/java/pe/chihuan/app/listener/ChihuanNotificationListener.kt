package pe.chihuan.app.listener

import android.app.Notification
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import pe.chihuan.app.ChihuanApplication
import pe.chihuan.app.core.notification.NotificationExtras
import pe.chihuan.app.core.notification.NotificationTextExtractor

/**
 * Receives posted notifications and hands each one to the process-wide
 * [NotificationPipeline][pe.chihuan.app.listener.NotificationPipeline].
 *
 * The service stays thin: it reads the four text extras (a cheap bundle read) and returns
 * immediately. Filtering, parsing, composition and speech run on the pipeline's own worker, so
 * `onNotificationPosted` never blocks the system's delivery thread.
 *
 * In debug builds the raw text is also logged, unchanged, under the `ChihuanCapture` tag: that seam
 * is how real Yape and bank wording gets captured from logcat and turned into parser fixtures.
 * Release installs take no capture code path.
 */
class ChihuanNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val notification = sbn.notification ?: return

        try {
            val extras = readExtras(notification)
            logCaptureIfDebugBuild(sbn.packageName, extras)

            val pipeline = (applicationContext as? ChihuanApplication)?.pipeline
            if (pipeline == null) {
                Log.w(LOG_TAG, "Pipeline unavailable; notification was not processed")
                return
            }

            pipeline.submit(
                IncomingNotification(
                    packageName = sbn.packageName,
                    statusBarKey = sbn.key,
                    postTimeMillis = sbn.postTime,
                    extras = extras,
                ),
            )
        } catch (throwable: Throwable) {
            // One unreadable notification must never take the listener down.
            Log.w(LOG_TAG, "Notification could not be read: ${throwable.javaClass.simpleName}")
        }
    }

    /** Debug-only capture: logcat only, never storage, and it never alters processing. */
    private fun logCaptureIfDebugBuild(packageName: String, extras: NotificationExtras) {
        if (!isDebugBuild()) return

        Log.d(
            CAPTURE_TAG,
            buildCaptureRecord(
                packageName = packageName,
                extras = extras,
                extracted = NotificationTextExtractor.extract(extras),
            ),
        )
    }

    /**
     * Reads the four text extras. Every constant used here exists since API 16, so no
     * `Build.VERSION.SDK_INT` guard is needed at `minSdk 24`; a missing bundle or missing key
     * simply yields null and never throws.
     */
    private fun readExtras(notification: Notification): NotificationExtras {
        val extras: Bundle? = notification.extras
        return NotificationExtras(
            title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            textLines = readTextLines(extras),
        )
    }

    /**
     * `EXTRA_TEXT_LINES` is documented as `CharSequence[]`, but some posters put nested arrays
     * there, so each element is flattened defensively. The typed `getCharSequenceArray` accessor
     * would throw on those, so the untyped `get` is used on purpose.
     */
    @Suppress("DEPRECATION")
    private fun readTextLines(extras: Bundle?): List<String>? {
        val raw = extras?.get(Notification.EXTRA_TEXT_LINES) as? Array<*> ?: return null
        val lines = raw.mapNotNull { element -> element.asLineText() }
        return lines.takeIf { it.isNotEmpty() }
    }

    private fun Any?.asLineText(): String? = when (this) {
        null -> null
        is CharSequence -> toString().takeIf { it.isNotEmpty() }
        is Array<*> -> mapNotNull { element -> element.asLineText() }
            .joinToString(" ")
            .takeIf { it.isNotEmpty() }

        else -> toString().takeIf { it.isNotEmpty() }
    }

    private fun isDebugBuild(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun buildCaptureRecord(
        packageName: String,
        extras: NotificationExtras,
        extracted: String?,
    ): String = buildString {
        append("pkg=").append(packageName.escaped())
        append("|title=").append(extras.title.escaped())
        append("|extracted=").append(extracted.escaped())
        append("|bigText=").append(extras.bigText.escaped())
        append("|textLines=").append(extras.textLines.escapedLines())
        append("|text=").append(extras.text.escaped())
    }

    /** Keeps every capture record on a single logcat line so it stays greppable and copyable. */
    private fun String?.escaped(): String =
        this?.replace("\n", "\\n")?.replace("\r", "\\r")?.replace("|", "\\|") ?: "-"

    private fun List<String>?.escapedLines(): String =
        this?.joinToString(";") { it.escaped() } ?: "-"

    private companion object {
        /** Stable tag used by the documented capture workflow (`adb logcat -s ChihuanCapture`). */
        const val CAPTURE_TAG = "ChihuanCapture"
        const val LOG_TAG = "ChihuanListener"
    }
}
