package com.retrsoft.bilisponsorskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomMallTabFilterTest {
    @Test
    fun identifiesMallTabByStableDataFields() {
        assertTrue(isMallBottomTab(BottomTabIdentity(uri = "bilibili://mall/home")))
        assertTrue(isMallBottomTab(BottomTabIdentity(uri = "bilibili://mall/homepage")))
        assertTrue(isMallBottomTab(BottomTabIdentity(tabId = "会员购Bottom")))
    }

    @Test
    fun ordinaryBottomTabsArePreserved() {
        assertFalse(
            isMallBottomTab(
                BottomTabIdentity(uri = "bilibili://main/home", tabId = "home"),
            ),
        )
        assertFalse(isMallBottomTab(BottomTabIdentity(uri = "bilibili://mall/detail")))
        assertFalse(isMallBottomTab(BottomTabIdentity(tabId = "会员购")))
    }

    @Test
    fun removesMallTabAndKeepsOriginalOrder() {
        val tabs = listOf(
            BottomTabIdentity(tabId = "home"),
            BottomTabIdentity(tabId = "dynamic"),
            BottomTabIdentity(tabId = "publish", publishType = true),
            BottomTabIdentity(tabId = "会员购Bottom"),
            BottomTabIdentity(tabId = "我的Bottom"),
        )

        assertEquals(
            listOf("home", "dynamic", "publish", "我的Bottom"),
            filterMallBottomTab(tabs).map { it.tabId },
        )
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
        assertTrue(isPublishBottomTab(BottomTabIdentity(tabId = "publish")))
        assertTrue(isPublishBottomTab(BottomTabIdentity(publishType = true)))
        assertTrue(
            isPublishBottomTab(
                BottomTabIdentity(uri = "bilibili://uper/center_plus?relation_from=center_plus&tab_index=2"),
            ),
        )
    }

    @Test
    fun ordinaryTabsAreNotIdentifiedAsPublishTab() {
        assertFalse(
            isPublishBottomTab(
                BottomTabIdentity(tabId = "我的Bottom", uri = "bilibili://user_center/mine"),
            ),
        )
        assertFalse(isPublishBottomTab(BottomTabIdentity(tabId = "publisher")))
        assertFalse(isPublishBottomTab(BottomTabIdentity(uri = "bilibili://uper/add_archive")))
    }

    @Test
    fun removesMallAndPublishTabsWhileKeepingRemainingOrder() {
        val tabs = listOf(
            BottomTabIdentity(tabId = "home"),
            BottomTabIdentity(tabId = "dynamic"),
            BottomTabIdentity(tabId = "publish", publishType = true),
            BottomTabIdentity(tabId = "会员购Bottom"),
            BottomTabIdentity(tabId = "我的Bottom"),
        )

        assertEquals(
            listOf("home", "dynamic", "我的Bottom"),
            filterBottomTabs(tabs, hideMall = true, hidePublish = true).map { it.tabId },
        )
    }

    @Test
    fun mallAndPublishSwitchesRemainIndependent() {
        val tabs = listOf(
            BottomTabIdentity(tabId = "home"),
            BottomTabIdentity(tabId = "publish", publishType = true),
            BottomTabIdentity(tabId = "会员购Bottom"),
        )

        assertEquals(
            listOf("home", "publish", "会员购Bottom"),
            filterBottomTabs(tabs, hideMall = false, hidePublish = false).map { it.tabId },
        )
        assertEquals(
            listOf("home", "publish"),
            filterBottomTabs(tabs, hideMall = true, hidePublish = false).map { it.tabId },
        )
        assertEquals(
            listOf("home", "会员购Bottom"),
            filterBottomTabs(tabs, hideMall = false, hidePublish = true).map { it.tabId },
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
