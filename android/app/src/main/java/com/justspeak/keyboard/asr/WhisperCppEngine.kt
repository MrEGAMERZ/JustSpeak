package com.justspeak.keyboard.asr

import android.content.Context
import java.io.File

/**
 * D3 target: on-device Whisper English via whisper.cpp (ggml) + JNI.
 *
 * **Dependency choice (locked for D3, not vendored in D1):**
 * - Source: https://github.com/ggml-org/whisper.cpp
 * - Follow `examples/whisper.android` (CMake + `whisper.h` JNI).
 * - Model: quantized `ggml-base.en-q5_1.bin` (try `tiny.en` if RAM/APK size
 *   hurts; `small.en` only if base quality is insufficient).
 * - Audio in: 16 kHz mono PCM (see [com.justspeak.keyboard.audio.AudioCapture]).
 *
 * **Alternate** if NDK packaging blocks D3: ONNX Runtime Mobile + Whisper ONNX.
 * Do not add a cloud ASR to “just demo.”
 *
 * Today this class is a documented facade. When the native lib / model file is
 * missing it reports [AsrError.Code.NativeNotLinked] / [AsrError.Code.ModelMissing]
 * and optionally falls back to [StubAsrEngine] so the IME insert path still works.
 */
class WhisperCppEngine(
    context: Context,
    private val modelFileName: String,
    private val fallback: AsrEngine = StubAsrEngine(),
    private val useFallbackUntilNativeReady: Boolean = true,
) : AsrEngine {

    private val appContext = context.applicationContext

    override val backendName: String
        get() = if (isNativeReady()) "whisper.cpp" else "whisper.cpp-pending"

    override val isRunning: Boolean
        get() = fallback.isRunning

    override fun start(listener: AsrListener) {
        if (!isNativeLibraryLoaded()) {
            listener.onError(
                AsrError(
                    AsrError.Code.NativeNotLinked,
                    "TODO(D3): link libwhisper via CMake/NDK " +
                        "(ggml-org/whisper.cpp examples/whisper.android).",
                ),
            )
            if (useFallbackUntilNativeReady) {
                fallback.start(listener)
            } else {
                listener.onState(AsrState.Error)
            }
            return
        }

        val model = resolveModelFile()
        if (model == null) {
            listener.onError(
                AsrError(
                    AsrError.Code.ModelMissing,
                    "TODO(D3): place $modelFileName in ${modelsDir().absolutePath} " +
                        "or assets/models/.",
                ),
            )
            if (useFallbackUntilNativeReady) {
                fallback.start(listener)
            } else {
                listener.onState(AsrState.Error)
            }
            return
        }

        // TODO(D3):
        //  1. whisper_init_from_file(model.absolutePath)
        //  2. Convert AudioCapture int16 frames → float[-1,1]
        //  3. whisper_full(...) with English, no timestamps required
        //  4. Concatenate segment text onto AsrListener
        listener.onError(
            AsrError(
                AsrError.Code.NativeNotLinked,
                "Model present at ${model.absolutePath} but JNI transcribe is not wired (D3).",
            ),
        )
        if (useFallbackUntilNativeReady) {
            fallback.start(listener)
        }
    }

    override fun stop() = fallback.stop()

    override fun release() = fallback.release()

    fun isNativeReady(): Boolean = isNativeLibraryLoaded() && resolveModelFile() != null

    fun modelsDir(): File = File(appContext.filesDir, MODELS_DIR).apply { mkdirs() }

    fun resolveModelFile(): File? {
        val onDisk = File(modelsDir(), modelFileName)
        if (onDisk.isFile && onDisk.length() > 0L) return onDisk
        return null
    }

    private fun isNativeLibraryLoaded(): Boolean {
        // TODO(D3): System.loadLibrary("whisper") once CMake produces the .so
        return false
    }

    companion object {
        const val MODELS_DIR: String = "models"
    }
}
