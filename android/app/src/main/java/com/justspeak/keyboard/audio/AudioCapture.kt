package com.justspeak.keyboard.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 16 kHz mono 16-bit PCM capture — the format whisper.cpp expects (D3 converts
 * int16 → float[-1, 1]).
 *
 * D1 wires start/stop + a read loop so permission and IME hooks are real.
 * D2 should add audio focus, session reuse, silence trimming, and safer
 * start/stop races around IME hide.
 */
class AudioCapture(
    private val config: Config = Config(),
) {
    data class Config(
        val sampleRateHz: Int = 16_000,
        val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
        val encoding: Int = AudioFormat.ENCODING_PCM_16BIT,
        val source: Int = MediaRecorder.AudioSource.VOICE_RECOGNITION,
    )

    fun interface Listener {
        fun onPcmFrame(samples: ShortArray, count: Int)
    }

    private val running = AtomicBoolean(false)
    private var record: AudioRecord? = null
    private var thread: Thread? = null

    val isRunning: Boolean
        get() = running.get()

    fun recommendedBufferSize(): Int {
        val min = AudioRecord.getMinBufferSize(
            config.sampleRateHz,
            config.channelConfig,
            config.encoding,
        )
        return if (min > 0) min * 2 else config.sampleRateHz
    }

    /**
     * Starts a background read loop. Caller must hold [MicPermission].
     */
    @SuppressLint("MissingPermission")
    fun start(listener: Listener): Result<Unit> {
        if (running.get()) return Result.success(Unit)
        val bufferSize = recommendedBufferSize()
        if (bufferSize <= 0) {
            return Result.failure(IllegalStateException("AudioRecord buffer size unavailable"))
        }
        val recorder = try {
            AudioRecord(
                config.source,
                config.sampleRateHz,
                config.channelConfig,
                config.encoding,
                bufferSize,
            )
        } catch (error: SecurityException) {
            return Result.failure(error)
        } catch (error: IllegalArgumentException) {
            return Result.failure(error)
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return Result.failure(IllegalStateException("AudioRecord failed to initialize"))
        }
        record = recorder
        running.set(true)
        try {
            recorder.startRecording()
        } catch (error: IllegalStateException) {
            running.set(false)
            recorder.release()
            record = null
            return Result.failure(error)
        }
        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val buf = ShortArray(bufferSize / 2)
            while (running.get()) {
                val count = try {
                    recorder.read(buf, 0, buf.size)
                } catch (_: IllegalStateException) {
                    break
                }
                if (count > 0) {
                    listener.onPcmFrame(buf, count)
                }
            }
        }, "justspeak-pcm").also { it.start() }
        return Result.success(Unit)
    }

    fun stop() {
        running.set(false)
        thread?.join(500)
        thread = null
        record?.let { rec ->
            try {
                if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    rec.stop()
                }
            } catch (_: IllegalStateException) {
                // already stopped
            }
            rec.release()
        }
        record = null
    }

    fun release() = stop()
}
