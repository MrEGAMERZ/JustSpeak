package com.justspeak.keyboard.asr

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StubAsrEngineTest {

    @Test
    fun emitsPartialThenFinalTranscript() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = StubAsrEngine(
            dispatcher = dispatcher,
            partialDelayMs = 10,
            finalDelayMs = 10,
        )
        val partials = mutableListOf<String>()
        val finals = mutableListOf<String>()
        val states = mutableListOf<AsrState>()

        engine.start(object : NoOpAsrListener() {
            override fun onPartialTranscript(text: String) {
                partials += text
            }

            override fun onFinalTranscript(text: String) {
                finals += text
            }

            override fun onState(state: AsrState) {
                states += state
            }
        })

        assertTrue(engine.isRunning)
        advanceUntilIdle()

        assertEquals(listOf(StubAsrEngine.PARTIAL_TRANSCRIPT), partials)
        assertEquals(listOf(StubAsrEngine.DEFAULT_TRANSCRIPT), finals)
        assertTrue(states.contains(AsrState.Listening))
        assertTrue(states.contains(AsrState.Transcribing))
        assertTrue(states.last() == AsrState.Idle)
        assertFalse(engine.isRunning)
        assertEquals("stub", engine.backendName)
    }

    @Test
    fun stopCancelsBeforeFinal() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = StubAsrEngine(
            dispatcher = dispatcher,
            partialDelayMs = 50,
            finalDelayMs = 50,
        )
        val finals = mutableListOf<String>()
        engine.start(object : NoOpAsrListener() {
            override fun onFinalTranscript(text: String) {
                finals += text
            }
        })
        engine.stop()
        advanceUntilIdle()
        assertTrue(finals.isEmpty())
        assertFalse(engine.isRunning)
    }
}
