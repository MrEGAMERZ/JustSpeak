package com.justspeak.keyboard.asr

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * D1 stand-in for Whisper. Emits a fixed English sentence so Insert / InputConnection
 * can be exercised without a ggml model or NDK.
 *
 * D3 deletes the default wiring in [AsrProvider] once [WhisperCppEngine] is native-ready.
 */
class StubAsrEngine(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val partialDelayMs: Long = 280,
    private val finalDelayMs: Long = 620,
    private val transcript: String = DEFAULT_TRANSCRIPT,
) : AsrEngine {

    override val backendName: String = "stub"

    private val running = AtomicBoolean(false)
    private val jobLock = Any()
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    override val isRunning: Boolean
        get() = running.get()

    override fun start(listener: AsrListener) {
        if (!running.compareAndSet(false, true)) return
        listener.onState(AsrState.Listening)
        synchronized(jobLock) {
            job?.cancel()
            job = scope.launch {
                try {
                    delay(partialDelayMs)
                    if (!running.get()) return@launch
                    listener.onPartialTranscript(PARTIAL_TRANSCRIPT)
                    listener.onState(AsrState.Transcribing)
                    delay(finalDelayMs)
                    if (!running.get()) return@launch
                    listener.onFinalTranscript(transcript)
                    listener.onState(AsrState.Idle)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    listener.onError(
                        AsrError(AsrError.Code.Cancelled, "Stub ASR cancelled", cancelled),
                    )
                    listener.onState(AsrState.Idle)
                    throw cancelled
                } finally {
                    running.set(false)
                }
            }
        }
    }

    override fun stop() {
        running.set(false)
        synchronized(jobLock) {
            job?.cancel()
            job = null
        }
    }

    override fun release() {
        stop()
    }

    companion object {
        const val PARTIAL_TRANSCRIPT: String = "JustSpeak stub…"
        const val DEFAULT_TRANSCRIPT: String =
            "JustSpeak stub transcript — insert path OK."
    }
}
