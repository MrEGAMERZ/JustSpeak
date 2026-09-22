package com.justspeak.keyboard.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 16 kHz mono PCM16 capture for on-device ASR.
 *
 * D2: audio focus, synchronized start / stop / cancel (no double [AudioRecord]),
 * safe teardown on IME hide / destroy, and int16→float conversion for Whisper.
 */
class AudioCapture(
    context: Context,
    private val config: Config = Config(),
) {
    data class Config(
        val sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
        val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
        val encoding: Int = AudioFormat.ENCODING_PCM_16BIT,
        val audioSource: Int = MediaRecorder.AudioSource.VOICE_RECOGNITION,
    )

    fun interface Listener {
        /** Called on the capture thread. Do not retain [samples] — copy if needed. */
        fun onPcmFrame(samples: ShortArray, sampleCount: Int)
    }

    private val appContext = context.applicationContext
    private val lock = Any()
    private val running = AtomicBoolean(false)

    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private var focus: AudioFocusController? = null
    private var listener: Listener? = null
    private var floatConsumer: ((FloatArray, Int) -> Unit)? = null
    private val floatScratch = PcmConverters.FloatScratch()

    val isRunning: Boolean
        get() = running.get()

    val sampleRateHz: Int
        get() = config.sampleRateHz

    /**
     * Starts capture. Idempotent — a second call while running succeeds without
     * opening another [AudioRecord].
     *
     * @param onPcmFloat optional Whisper-ready float[-1,1] frames (recycled buffer)
     */
    @SuppressLint("MissingPermission")
    fun start(
        listener: Listener,
        onPcmFloat: ((FloatArray, Int) -> Unit)? = null,
    ): Result<Unit> = synchronized(lock) {
        if (running.get()) return Result.success(Unit)

        val focusController = AudioFocusController(appContext) {
            // Stop without deadlocking if focus callback re-enters.
            cancelFromFocusLoss()
        }
        if (!focusController.request()) {
            focusController.abandon()
            return Result.failure(IllegalStateException("Audio focus denied"))
        }

        val minBuf = AudioRecord.getMinBufferSize(
            config.sampleRateHz,
            config.channelConfig,
            config.encoding,
        )
        if (minBuf == AudioRecord.ERROR || minBuf == AudioRecord.ERROR_BAD_VALUE) {
            focusController.abandon()
            return Result.failure(IllegalStateException("Invalid AudioRecord buffer size: $minBuf"))
        }
        val bufferBytes = minBuf * 2

        val recorder = try {
            AudioRecord(
                config.audioSource,
                config.sampleRateHz,
                config.channelConfig,
                config.encoding,
                bufferBytes,
            )
        } catch (t: Throwable) {
            focusController.abandon()
            return Result.failure(t)
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            focusController.abandon()
            return Result.failure(IllegalStateException("AudioRecord failed to initialize"))
        }

        try {
            recorder.startRecording()
        } catch (t: Throwable) {
            recorder.release()
            focusController.abandon()
            return Result.failure(t)
        }

        if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            try {
                recorder.stop()
            } catch (_: IllegalStateException) {
            }
            recorder.release()
            focusController.abandon()
            return Result.failure(IllegalStateException("AudioRecord did not enter RECORDING"))
        }

        this.listener = listener
        this.floatConsumer = onPcmFloat
        this.focus = focusController
        this.record = recorder
        running.set(true)

        thread = Thread(
            {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                readLoop(recorder, bufferBytes)
            },
            "justspeak-audio-capture",
        ).also { it.start() }

        Result.success(Unit)
    }

    /** Stops capture and joins the reader thread briefly. Safe to call repeatedly. */
    fun stop() = synchronized(lock) {
        stopInternal(joinThread = true)
    }

    /** Alias for [stop] — cancel in-flight capture from IME hide / destroy. */
    fun cancel() = stop()

    fun release() = stop()

    private fun cancelFromFocusLoss() {
        // May already hold [lock] if stop() triggered abandon; use non-joining path.
        if (Thread.holdsLock(lock)) {
            stopInternal(joinThread = false)
        } else {
            synchronized(lock) {
                stopInternal(joinThread = false)
            }
        }
    }

    private fun stopInternal(joinThread: Boolean) {
        val wasRunning = running.getAndSet(false)
        val reader = thread
        val recorder = record
        thread = null
        record = null
        listener = null
        floatConsumer = null

        if (joinThread && reader != null && reader !== Thread.currentThread()) {
            try {
                reader.join(JOIN_TIMEOUT_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            if (reader.isAlive) {
                reader.interrupt()
                try {
                    reader.join(JOIN_TIMEOUT_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }

        if (recorder != null) {
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
            } catch (_: IllegalStateException) {
            }
            try {
                recorder.release()
            } catch (_: Exception) {
            }
        }

        focus?.abandon()
        focus = null

        if (!wasRunning && recorder == null && reader == null) {
            // Fully idle already.
        }
    }

    private fun readLoop(recorder: AudioRecord, bufferBytes: Int) {
        val shortBuf = ShortArray((bufferBytes / 2).coerceAtLeast(1))
        while (running.get()) {
            val n = try {
                recorder.read(shortBuf, 0, shortBuf.size)
            } catch (_: IllegalStateException) {
                break
            }
            when {
                n > 0 -> {
                    listener?.onPcmFrame(shortBuf, n)
                    val consumer = floatConsumer
                    if (consumer != null) {
                        val (floats, count) = floatScratch.convert(shortBuf, n)
                        consumer(floats, count)
                    }
                }
                n == AudioRecord.ERROR_INVALID_OPERATION ||
                    n == AudioRecord.ERROR_BAD_VALUE ||
                    n == AudioRecord.ERROR_DEAD_OBJECT -> break
            }
        }
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE_HZ: Int = 16_000
        private const val JOIN_TIMEOUT_MS: Long = 500L
    }
}
