package com.justspeak.keyboard.asr

/**
 * On-device speech recognition contract.
 *
 * D1: [StubAsrEngine] returns placeholder text so the IME insert path is testable.
 * D3: [WhisperCppEngine] runs whisper.cpp (ggml) locally — English only.
 *
 * Implementations must not upload audio. There is no INTERNET permission.
 */
interface AsrEngine {
    val backendName: String
    val isRunning: Boolean

    fun start(listener: AsrListener)
    fun stop()
    fun cancel() = stop()
    fun release()

    /**
     * Optional 16 kHz mono float PCM in approximately [-1, 1].
     * Stub ignores this; [WhisperCppEngine] buffers for D3.
     */
    fun feedPcmFloat(samples: FloatArray, sampleCount: Int = samples.size) = Unit
}

enum class AsrState {
    Idle,
    AwaitingPermission,
    Listening,
    Transcribing,
    Error,
}

data class AsrError(
    val code: Code,
    val message: String,
    val cause: Throwable? = null,
) {
    enum class Code {
        PermissionDenied,
        MicUnavailable,
        ModelMissing,
        NativeNotLinked,
        TranscriptionFailed,
        Cancelled,
        Unknown,
    }
}

interface AsrListener {
    fun onPartialTranscript(text: String)
    fun onFinalTranscript(text: String)
    fun onState(state: AsrState)
    fun onError(error: AsrError)
}

open class NoOpAsrListener : AsrListener {
    override fun onPartialTranscript(text: String) = Unit
    override fun onFinalTranscript(text: String) = Unit
    override fun onState(state: AsrState) = Unit
    override fun onError(error: AsrError) = Unit
}
