package com.retrsoft.bilisponsorskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeBottomTabFilterTest {
    @Test
    fun readsIdentityFromComposeStateAndTabData() {
        val tabData = FakeTabData()
        val state = FakeState()
        val reader = FakeReader(
            mapOf(
                state to mapOf("a" to tabData, "o" to true),
                tabData to mapOf(
                    "h" to "publish",
                    "c" to "bilibili://uper/center_plus?tab_index=2",
                ),
            ),
        )

        assertEquals(
            BottomTabIdentity(
                tabId = "publish",
                uri = "bilibili://uper/center_plus?tab_index=2",
                publishType = true,
            ),
            readComposeBottomTabIdentity(state, reader),
        )
    }

    @Test
    fun missingTabDataFailsOpen() {
        val state = FakeState()

        assertNull(
            readComposeBottomTabIdentity(
                state,
                FakeReader(mapOf(state to mapOf("a" to null, "o" to false))),
            ),
        )
        assertNull(readComposeBottomTabIdentity(state, ThrowingReader))
    }

    @Test
    fun filtersComposeTabsWithoutMutatingOriginalList() {
        val home = FakeState()
        val publish = FakeState()
        val mall = FakeState()
        val original = listOf(home, publish, mall)
        val reader = composeReader(
            home to BottomTabIdentity(tabId = "home", uri = "bilibili://main/home"),
            publish to BottomTabIdentity(tabId = "publish", publishType = true),
            mall to BottomTabIdentity(tabId = "会员购Bottom", uri = "bilibili://mall/home"),
        )

        val filtered = filterBottomTabs(
            tabs = original,
            hideMall = true,
            hidePublish = true,
        ) { readComposeBottomTabIdentity(it, reader) ?: BottomTabIdentity() }

        assertEquals(listOf(home), filtered)
        assertEquals(listOf(home, publish, mall), original)
        assertFalse(filtered === original)
        assertSame(home, filtered.single())
    }

    @Test
    fun composeMallAndPublishSwitchesRemainIndependent() {
        val home = FakeState()
        val publish = FakeState()
        val mall = FakeState()
        val tabs = listOf(home, publish, mall)
        val reader = composeReader(
            home to BottomTabIdentity(tabId = "home"),
            publish to BottomTabIdentity(publishType = true),
            mall to BottomTabIdentity(uri = "bilibili://mall/home"),
        )
        val identity: (FakeState) -> BottomTabIdentity = {
            readComposeBottomTabIdentity(it, reader) ?: BottomTabIdentity()
        }

        assertEquals(
            listOf(home, publish),
            filterBottomTabs(tabs, hideMall = true, hidePublish = false, identity = identity),
        )
        assertEquals(
            listOf(home, mall),
            filterBottomTabs(tabs, hideMall = false, hidePublish = true, identity = identity),
        )
        assertTrue(
            filterBottomTabs(tabs, hideMall = false, hidePublish = false, identity = identity)
                .containsAll(tabs),
        )
    }

    private fun composeReader(vararg identities: Pair<FakeState, BottomTabIdentity>): FakeReader {
        val values = linkedMapOf<Any, Map<String, Any?>>()
        identities.forEach { (state, identity) ->
            val tabData = FakeTabData()
            values[state] = mapOf("a" to tabData, "o" to identity.publishType)
            values[tabData] = mapOf("h" to identity.tabId, "c" to identity.uri)
        }
        return FakeReader(values)
    }

    private class FakeState

    private class FakeTabData

    private class FakeReader(
        private val values: Map<Any, Map<String, Any?>>,
    ) : ReflectiveValueReader {
        override fun call(target: Any, name: String): Any? = null

        override fun field(target: Any, name: String): Any? = values[target]?.get(name)
    }

    private object ThrowingReader : ReflectiveValueReader {
        override fun call(target: Any, name: String): Any? = error("unexpected call")

        override fun field(target: Any, name: String): Any? = error("missing field")
    }
}
