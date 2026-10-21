package com.retrsoft.bilisponsorskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryAdSignalReaderTest {
    @Test
    fun readsPublicStoryAdMethods() {
        val reader = RecordingReader(
            calls = mapOf(
                "isAd" to true,
                "isNaturalAd" to false,
                "getAdInfo" to Any(),
                "getGoto" to "vertical_ad_live",
            ),
        )

        val signals = readStoryAdSignals(Any(), reader)

        assertTrue(signals.isAd)
        assertFalse(signals.isNaturalAd)
        assertTrue(signals.hasAdInfo)
        assertEquals("vertical_ad_live", signals.goto)
        assertFalse(reader.requestedCalls.any { it.contains("title", ignoreCase = true) })
        assertFalse(reader.requestedCalls.any { it.contains("text", ignoreCase = true) })
    }

    @Test
    fun fallsBackToVerifiedAdInfoAndGotoFields() {
        val reader = RecordingReader(
            fields = mapOf(
                "adInfo" to Any(),
                "goto" to "vertical_ad_picture",
            ),
        )

        val signals = readStoryAdSignals(Any(), reader)

        assertTrue(signals.hasAdInfo)
        assertEquals("vertical_ad_picture", signals.goto)
    }

    @Test
    fun unreadableValuesFailOpenAsOrdinaryContent() {
        val signals = readStoryAdSignals(Any(), ThrowingReader)

        assertEquals(StoryAdSignals(), signals)
        assertFalse(isMarkedStoryAd(signals))
    }

    private class RecordingReader(
        private val calls: Map<String, Any?> = emptyMap(),
        private val fields: Map<String, Any?> = emptyMap(),
    ) : ReflectiveValueReader {
        val requestedCalls = mutableListOf<String>()

        override fun call(target: Any, name: String): Any? {
            requestedCalls += name
            return calls[name]
        }

        override fun field(target: Any, name: String): Any? = fields[name]
    }

    private object ThrowingReader : ReflectiveValueReader {
        override fun call(target: Any, name: String): Any? = error("unreadable call")

        override fun field(target: Any, name: String): Any? = error("unreadable field")
    }
}
