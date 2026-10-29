package com.retrsoft.bilisponsorskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test

class ComposeBottomTabHookPolicyTest {
    @Test
    fun disabledModuleLeavesComposeTabsUntouched() {
        val tabs = listOf("home", "mall")

        assertNull(
            filterComposeBottomTabs(
                tabs = tabs,
                settings = SettingsSnapshot(enabled = false),
            ) { item ->
                if (item == "mall") BottomTabIdentity(uri = "bilibili://mall/home") else BottomTabIdentity()
            },
        )
    }

    @Test
    fun disabledBottomSwitchesLeaveComposeTabsUntouched() {
        assertNull(
            filterComposeBottomTabs(
                tabs = listOf("home"),
                settings = SettingsSnapshot(
                    hideBottomMallTab = false,
                    hideBottomPublishTab = false,
                ),
            ) { BottomTabIdentity(tabId = it) },
        )
    }

    @Test
    fun enabledPolicyReturnsNewFilteredListAndPreservesUnknownItems() {
        val home = FakeItem("home")
        val unknown = FakeItem("unknown")
        val publish = FakeItem("publish")
        val mall = FakeItem("mall")
        val original = listOf(home, unknown, publish, mall)

        val filtered = filterComposeBottomTabs(
            tabs = original,
            settings = SettingsSnapshot(
                hideBottomMallTab = true,
                hideBottomPublishTab = true,
            ),
        ) { item ->
            when (item) {
                home -> BottomTabIdentity(tabId = "home")
                publish -> BottomTabIdentity(publishType = true)
                mall -> BottomTabIdentity(tabId = "会员购Bottom")
                else -> null
            }
        }

        assertEquals(listOf(home, unknown), filtered)
        assertEquals(listOf(home, unknown, publish, mall), original)
        assertNotSame(original, filtered)
    }

    @Test
    fun enabledPolicyHonorsIndependentBottomSwitches() {
        val publish = FakeItem("publish")
        val mall = FakeItem("mall")
        val tabs = listOf(publish, mall)
        val identity: (FakeItem) -> BottomTabIdentity = {
            if (it === publish) BottomTabIdentity(publishType = true)
            else BottomTabIdentity(uri = "bilibili://mall/home")
        }

        assertEquals(
            listOf(publish),
            filterComposeBottomTabs(
                tabs,
                SettingsSnapshot(hideBottomMallTab = true, hideBottomPublishTab = false),
                identity,
            ),
        )
        assertEquals(
            listOf(mall),
            filterComposeBottomTabs(
                tabs,
                SettingsSnapshot(hideBottomMallTab = false, hideBottomPublishTab = true),
                identity,
            ),
        )
    }

    private data class FakeItem(val name: String)
}
