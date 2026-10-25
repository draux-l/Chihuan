package pe.chihuan.app

import android.app.Application
import pe.chihuan.app.data.settings.AppSettings
import pe.chihuan.app.listener.NotificationPipeline
import pe.chihuan.app.speech.AudioPolicy
import pe.chihuan.app.speech.CallStateSource
import pe.chihuan.app.speech.SpeechQueue
import pe.chihuan.app.speech.TtsAnnouncer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Process-scoped owner of the notification pipeline.
 *
 * The listener service and the control panel both reach the same singletons through
 * [Application], which is what makes one speech engine and one announcement queue shared by real
 * payments and `Probar voz`. It survives Activity destruction trivially; it does not survive process
 * death, and by design nothing here is persisted or restored — the system rebinds the listener and
 * this class rebuilds every collaborator.
 *
 * Manual wiring on purpose: no DI framework, no static state.
 */
class ChihuanApplication : Application() {

    lateinit var settings: AppSettings
        private set

    lateinit var ttsAnnouncer: TtsAnnouncer
        private set

    lateinit var speechQueue: SpeechQueue
        private set

    lateinit var pipeline: NotificationPipeline
        private set

    private lateinit var pipelineExecutor: ExecutorService

    override fun onCreate() {
        super.onCreate()

        settings = AppSettings(this)

        ttsAnnouncer = TtsAnnouncer(this).also { announcer -> announcer.initialize() }

        speechQueue = SpeechQueue(
            announcer = ttsAnnouncer,
            audioPolicy = AudioPolicy(this),
            callState = CallStateSource(this),
        )

        // One worker keeps notification processing off the delivery thread and in arrival order.
        pipelineExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, PIPELINE_THREAD_NAME).apply { isDaemon = true }
        }

        pipeline = NotificationPipeline(
            context = this,
            settings = settings,
            speechQueue = speechQueue,
            executor = pipelineExecutor,
        )
    }

    private companion object {
        const val PIPELINE_THREAD_NAME = "chihuan-pipeline"
    }
}
