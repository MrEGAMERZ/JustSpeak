package com.justspeak.keyboard.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmConvertersTest {

    @Test
    fun int16ToFloat_scalesFullScaleSamples() {
        val src = shortArrayOf(0, 32767, -32768, 16384)
        val out = PcmConverters.int16ToFloat(src)
        assertEquals(4, out.size)
        assertEquals(0.0f, out[0], 1e-6f)
        assertTrue(out[1] in 0.99f..1.0f)
        assertEquals(-1.0f, out[2], 1e-6f)
        assertEquals(0.5f, out[3], 1e-4f)
    }

    @Test
    fun floatScratch_reusesBufferAcrossCalls() {
        val scratch = PcmConverters.FloatScratch(8)
        val first = scratch.convert(shortArrayOf(1, 2, 3, 4), 4)
        val second = scratch.convert(shortArrayOf(5, 6), 2)
        assertTrue(first.first === second.first)
        assertEquals(2, second.second)
    }
}
