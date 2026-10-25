package pe.chihuan.app.listener

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import pe.chihuan.app.core.announce.AnnouncementComposer
import pe.chihuan.app.core.dedup.DuplicateSuppressor
import pe.chihuan.app.core.dedup.NotificationIdentity
import pe.chihuan.app.core.notification.NotificationExtras
import pe.chihuan.app.core.notification.NotificationTextExtractor
import pe.chihuan.app.core.parser.PaymentParser
import pe.chihuan.app.core.source.SourceId
import pe.chihuan.app.core.source.SourceMatcher
import pe.chihuan.app.core.source.SourceRegistry
import pe.chihuan.app.data.settings.AppSettings
import pe.chihuan.app.speech.SpeechQueue
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong

/**
 * Turns one posted notification into speech, or into deliberate silence.
 *
 * The steps are fixed by the specs and run in this order:
 *
 * 1. attribute the package to a registry source, using the posting app's label only for the
 *    unverified sources ([SourceMatcher]); an unknown package is ignored entirely;
 * 2. stop when that source is off — no parse, no announcement, no fallback and no record;
 * 3. stop when this exact post was already handled (in-memory deduplication);
 * 4. extract the text, parse it with the source's own pattern set;
 * 5. compose the utterance, including the fixed fallback phrase when the outcome is unparsed;
 * 6. hand it to the shared [SpeechQueue].
 *
 * Everything expensive (label lookup, parsing) runs on the pipeline executor, never on the thread
 * that delivers `onNotificationPosted`. Failures are contained per notification: one bad
 * notification can never take the listener down.
 */
class NotificationPipeline(
    context: Context,
    private val settings: AppSettings,
    private val speechQueue: SpeechQueue,
    private val executor: Executor,
) {

    private val appContext: Context = context.applicationContext
    private val duplicates = DuplicateSuppressor()
    private val sampleSequence = AtomicLong()

    /** Queues [notification] for processing. Returns immediately, whichever thread it is called on. */
    fun submit(notification: IncomingNotification) {
        executor.execute {
            try {
                process(notification)
            } catch (throwable: Throwable) {
                // Containment: an unreadable notification is reported, never rethrown into the service.
                Log.w(LOG_TAG, "Notification could not be processed: ${throwable.javaClass.simpleName}")
            }
        }
    }

    /**
     * Debug-only entry point: pushes a sample Yape payment through [submit], the same door a real
     * notification uses. Nothing here bypasses filtering, parsing or composition.
     */
    fun submitSamplePayment() {
        val yapePackage = SourceRegistry.byId(SourceId.YAPE).packages.firstOrNull()
        if (yapePackage == null) {
            Log.w(LOG_TAG, "Sample payment skipped; the Yape package is not declared")
            return
        }

        submit(
            IncomingNotification(
                packageName = yapePackage,
                statusBarKey = "$SAMPLE_KEY_PREFIX${sampleSequence.incrementAndGet()}",
                postTimeMillis = System.currentTimeMillis(),
                extras = NotificationExtras(title = SAMPLE_TITLE, text = SAMPLE_TEXT),
            ),
        )
    }

    private fun process(notification: IncomingNotification) {
        val sourceId = SourceMatcher.match(
            packageName = notification.packageName,
            appLabel = applicationLabel(notification.packageName),
        ) ?: return

        val snapshot = settings.snapshot()
        if (!snapshot.isArmed(sourceId)) return

        val identity = NotificationIdentity.of(
            statusBarKey = notification.statusBarKey,
            postTimeMillis = notification.postTimeMillis,
        )
        if (duplicates.shouldSuppress(identity, System.currentTimeMillis())) return

        val selection = snapshot.contentSelection()
        val text = NotificationTextExtractor.extract(notification.extras).orEmpty()
        val outcome = PaymentParser.parse(sourceId, text, selection)

        speechQueue.announce(AnnouncementComposer.compose(outcome, selection))
    }

    /** The posting app's label, used only by the unverified-source fragment bridge. */
    private fun applicationLabel(packageName: String): String? = try {
        val packageManager = appContext.packageManager
        @Suppress("DEPRECATION")
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (exception: PackageManager.NameNotFoundException) {
        null
    }

    private companion object {
        const val LOG_TAG = "ChihuanPipeline"

        /** Fresh key per debug tap so the deduplication gate genuinely lets each sample through. */
        const val SAMPLE_KEY_PREFIX = "chihuan-debug-sample-"

        /** Deliberately the wording from the feature's own sample, not captured production text. */
        const val SAMPLE_TITLE = "Yape"
        const val SAMPLE_TEXT = "Juan Pérez te envió S/ 6,50. Código: 123"
    }
}
