package com.justspeak.keyboard.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Requests / abandons transient audio focus for IME mic capture so other apps
 * duck or pause while JustSpeak is recording.
 */
class AudioFocusController(
    context: Context,
    private val onFocusLost: () -> Unit = {},
) {
    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val holding = AtomicBoolean(false)
    private var focusRequest: AudioFocusRequest? = null

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> {
                if (holding.compareAndSet(true, false)) {
                    onFocusLost()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Record only while we own focus; do not auto-resume.
            }
        }
    }

    val hasFocus: Boolean
        get() = holding.get()

    fun request(): Boolean {
        if (holding.get()) return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setOnAudioFocusChangeListener(listener)
                .setAcceptsDelayedFocusGain(false)
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                listener,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
            )
        }
        val ok = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        holding.set(ok)
        return ok
    }

    fun abandon() {
        if (!holding.get() && focusRequest == null) return
        holding.set(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(listener)
        }
    }
}
