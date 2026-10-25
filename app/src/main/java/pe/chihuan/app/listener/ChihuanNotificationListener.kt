package pe.chihuan.app.listener

import android.app.Notification
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import pe.chihuan.app.core.notification.NotificationExtras
import pe.chihuan.app.core.notification.NotificationTextExtractor

/**
 * Receives posted notifications and, in debug builds only, logs their raw text so real
 * notification wording can be captured from logcat and turned into parser fixtures.
 *
 * This slice deliberately announces, parses and stores nothing: it is the capture seam the
 * parser work depends on. The extraction rule itself lives in [NotificationTextExtractor],
 * which is pure Kotlin and unit-tested on the JVM.
 */
class ChihuanNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        // Capture exists only in debug builds; release installs take no capture code path.
        if (!isDebugBuild()) return

        try {
            val extras = readExtras(notification)
            Log.d(
                CAPTURE_TAG,
                buildCaptureRecord(
                    packageName = sbn.packageName,
                    extras = extras,
                    extracted = NotificationTextExtractor.extract(extras),
                ),
            )
        } catch (throwable: Throwable) {
            // One unreadable notification must never take the listener down.
            Log.w(LOG_TAG, "Notification extras could not be read: ${throwable.javaClass.simpleName}")
        }
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
