package com.justspeak.keyboard.asr

import android.content.Context
import com.justspeak.keyboard.BuildConfig

/**
 * Single place the IME asks for an [AsrEngine].
 *
 * D1 defaults to the stub (`BuildConfig.USE_STUB_ASR`). D3 should construct
 * [WhisperCppEngine] and only fall back if the ggml model is absent.
 */
object AsrProvider {
    fun create(context: Context): AsrEngine {
        val app = context.applicationContext
        return if (BuildConfig.USE_STUB_ASR) {
            StubAsrEngine()
        } else {
            WhisperCppEngine(
                context = app,
                modelFileName = BuildConfig.WHISPER_MODEL_NAME,
            )
        }
    }
}
