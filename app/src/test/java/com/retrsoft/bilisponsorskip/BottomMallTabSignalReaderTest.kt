package com.retrsoft.bilisponsorskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BottomMallTabSignalReaderTest {
    @Test
    fun readsTabIdUriAndPublishTypeFromVerifiedFields() {
        val reader = FakeReader(
            mapOf(
                "a" to "publish",
                "d" to "bilibili://uper/center_plus?tab_index=2",
                "g" to "unrelated-report-id",
                "k" to true,
            ),
        )

        assertEquals(
            BottomTabIdentity(
                tabId = "publish",
                uri = "bilibili://uper/center_plus?tab_index=2",
                publishType = true,
            ),
            readBottomTabIdentity(Any(), reader),
        )
    }

    @Test
    fun reportIdDoesNotParticipateInIdentity() {
        val identity = readBottomTabIdentity(
            Any(),
            FakeReader(
                mapOf(
                    "a" to "home",
                    "d" to "bilibili://main/home",
                    "g" to "会员购Bottom",
                    "k" to false,
                ),
            ),
        )

        assertFalse(isMallBottomTab(identity))
        assertFalse(isPublishBottomTab(identity))
    }

    @Test
    fun reflectionFailuresFailOpen() {
        assertEquals(BottomTabIdentity(), readBottomTabIdentity(Any(), ThrowingReader))
    }

    private class FakeReader(private val values: Map<String, Any?>) : ReflectiveValueReader {
        override fun call(target: Any, name: String): Any? = null

        override fun field(target: Any, name: String): Any? = values[name]
    }

    private object ThrowingReader : ReflectiveValueReader {
        override fun call(target: Any, name: String): Any? = error("unexpected call")

        override fun field(target: Any, name: String): Any? = error("missing field")
    }
}
