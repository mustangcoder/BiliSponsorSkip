package com.retrsoft.bilisponsorskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomMallTabFilterTest {
    @Test
    fun identifiesMallTabByStableDataFields() {
        assertTrue(isMallBottomTab(BottomTabIdentity(id = "203")))
        assertTrue(isMallBottomTab(BottomTabIdentity(uri = "bilibili://mall/home")))
        assertTrue(isMallBottomTab(BottomTabIdentity(tabId = "会员购Bottom")))
    }

    @Test
    fun ordinaryBottomTabsArePreserved() {
        assertFalse(
            isMallBottomTab(
                BottomTabIdentity(id = "200", uri = "bilibili://main/home", tabId = "home"),
            ),
        )
    }

    @Test
    fun removesMallTabAndKeepsOriginalOrder() {
        val tabs = listOf(
            BottomTabIdentity(id = "200"),
            BottomTabIdentity(id = "202"),
            BottomTabIdentity(id = "606"),
            BottomTabIdentity(id = "203"),
            BottomTabIdentity(id = "204"),
        )

        assertEquals(listOf("200", "202", "606", "204"), filterMallBottomTab(tabs).map { it.id })
    }

    @Test
    fun switchDefaultsToEnabled() {
        assertTrue(SettingsSnapshot().hideBottomMallTab)
    }

    @Test
    fun switchIsPersistedAsBooleanSetting() {
        assertEquals("hide_bottom_mall_tab", SettingsContract.KEY_HIDE_BOTTOM_MALL_TAB)
        assertTrue(SettingsContract.KEY_HIDE_BOTTOM_MALL_TAB in SettingsContract.BOOLEAN_SETTING_KEYS)
    }

    @Test
    fun identifiesPublishTabByStableDataFields() {
        assertTrue(isPublishBottomTab(BottomTabIdentity(id = "606")))
        assertTrue(
            isPublishBottomTab(
                BottomTabIdentity(uri = "bilibili://uper/center_plus?relation_from=center_plus&tab_index=2"),
            ),
        )
    }

    @Test
    fun ordinaryTabsAreNotIdentifiedAsPublishTab() {
        assertFalse(isPublishBottomTab(BottomTabIdentity(id = "204", uri = "bilibili://user_center/mine")))
    }

    @Test
    fun removesMallAndPublishTabsWhileKeepingRemainingOrder() {
        val tabs = listOf(
            BottomTabIdentity(id = "200"),
            BottomTabIdentity(id = "202"),
            BottomTabIdentity(id = "606"),
            BottomTabIdentity(id = "203"),
            BottomTabIdentity(id = "204"),
        )

        assertEquals(
            listOf("200", "202", "204"),
            filterBottomTabs(tabs, hideMall = true, hidePublish = true).map { it.id },
        )
    }

    @Test
    fun publishSwitchDefaultsToEnabled() {
        assertTrue(SettingsSnapshot().hideBottomPublishTab)
    }

    @Test
    fun publishSwitchIsPersistedAsBooleanSetting() {
        assertEquals("hide_bottom_publish_tab", SettingsContract.KEY_HIDE_BOTTOM_PUBLISH_TAB)
        assertTrue(SettingsContract.KEY_HIDE_BOTTOM_PUBLISH_TAB in SettingsContract.BOOLEAN_SETTING_KEYS)
    }
}
