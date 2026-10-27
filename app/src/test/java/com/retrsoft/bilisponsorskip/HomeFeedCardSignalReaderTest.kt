package com.retrsoft.bilisponsorskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeFeedCardSignalReaderTest {
    @Test
    fun readsPublicLiveRoutingMethods() {
        val signals = readHomeFeedCardSignals(
            Any(),
            FakeReader(
                calls = mapOf(
                    "getGoto" to "live_rcmd",
                    "getCardGoto" to "live",
                    "getUri" to "bilibili://live/12345",
                ),
            ),
        )

        assertEquals("live_rcmd", signals.goTo)
        assertEquals("live", signals.cardGoto)
        assertEquals("bilibili://live/12345", signals.uri)
    }

    @Test
    fun fallsBackToVerifiedFields() {
        val signals = readHomeFeedCardSignals(
            Any(),
            FakeReader(
                fields = mapOf(
                    "goTo" to "live",
                    "cardGoto" to "live_rcmd",
                    "uri" to "https://live.bilibili.com/12345",
                ),
            ),
        )

        assertEquals("live", signals.goTo)
        assertEquals("live_rcmd", signals.cardGoto)
        assertEquals("https://live.bilibili.com/12345", signals.uri)
    }

    @Test
    fun reflectionFailuresFailOpen() {
        val signals = readHomeFeedCardSignals(Any(), ThrowingReader)

        assertEquals(HomeFeedCardSignals(), signals)
        assertFalse(isHomeFeedLiveCard(signals))
    }

    private class FakeReader(
        private val calls: Map<String, Any?> = emptyMap(),
        private val fields: Map<String, Any?> = emptyMap(),
    ) : ReflectiveValueReader {
        override fun call(target: Any, name: String): Any? = calls[name]

        override fun field(target: Any, name: String): Any? = fields[name]
    }

    private object ThrowingReader : ReflectiveValueReader {
        override fun call(target: Any, name: String): Any? = error("missing method")

        override fun field(target: Any, name: String): Any? = error("missing field")
    }
}
