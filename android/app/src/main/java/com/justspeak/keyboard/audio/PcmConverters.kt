package com.justspeak.keyboard.audio

/**
 * PCM helpers for Whisper / whisper.cpp.
 *
 * [AudioRecord] delivers signed int16. Whisper expects float samples in [-1, 1]
 * at 16 kHz mono. Stub ASR ignores this path; [com.justspeak.keyboard.asr.WhisperCppEngine]
 * buffers floats for D3.
 */
object PcmConverters {
    const val INT16_MAX_AS_FLOAT: Float = 32768.0f

    /**
     * Converts [count] samples from [src] into a new float buffer.
     * Values are scaled into approximately [-1, 1].
     */
    fun int16ToFloat(src: ShortArray, count: Int = src.size): FloatArray {
        require(count >= 0) { "count must be >= 0" }
        require(count <= src.size) { "count $count exceeds src size ${src.size}" }
        val dst = FloatArray(count)
        int16ToFloat(src, 0, dst, 0, count)
        return dst
    }

    /**
     * Writes into a caller-owned [dst] (avoids alloc in the capture hot path
     * when the buffer is reused).
     *
     * @return number of floats written
     */
    fun int16ToFloat(
        src: ShortArray,
        srcOffset: Int,
        dst: FloatArray,
        dstOffset: Int,
        count: Int,
    ): Int {
        require(srcOffset >= 0 && dstOffset >= 0 && count >= 0)
        require(srcOffset + count <= src.size) { "src range out of bounds" }
        require(dstOffset + count <= dst.size) { "dst range out of bounds" }
        var i = 0
        while (i < count) {
            dst[dstOffset + i] = src[srcOffset + i] / INT16_MAX_AS_FLOAT
            i++
        }
        return count
    }

    /**
     * Reusable scratch buffer for int16 → float on the capture thread.
     * Not thread-safe — one instance per capture session.
     */
    class FloatScratch(initialCapacity: Int = 4096) {
        private var buffer: FloatArray = FloatArray(initialCapacity.coerceAtLeast(1))

        fun ensure(capacity: Int): FloatArray {
            if (buffer.size < capacity) {
                buffer = FloatArray(capacity)
            }
            return buffer
        }

        fun convert(src: ShortArray, count: Int): Pair<FloatArray, Int> {
            val dst = ensure(count)
            int16ToFloat(src, 0, dst, 0, count)
            return dst to count
        }
    }
}
