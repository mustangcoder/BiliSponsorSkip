package com.retrsoft.bilisponsorskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpstreamMergeCompatibilityTest {
    @Test
    fun customColorsCoexistWithIndependentFeedBottomAndAnalyticsFilters() {
        val settings = SettingsSnapshot(
            hideHomeFeedAds = false,
            hideHomeFeedLiveCards = true,
            disableAnalyticsReporting = true,
        ).copy(categoryColors = mapOf("sponsor" to "#448AFF"))
        val ad = HomeFeedCardSignals(ad = HomeFeedAdSignals(isAd = true))
        val live = HomeFeedCardSignals(goTo = "live_rcmd")
        val video = HomeFeedCardSignals(goTo = "av")

        assertEquals(
            listOf(ad, video),
            filterHomeFeedResult(listOf(ad, live, video), settings.hideHomeFeedAds, settings.hideHomeFeedLiveCards) { it }.items,
        )
        val tabs = listOf(
            BottomTabIdentity(tabId = "home"),
            BottomTabIdentity(publishType = true),
            BottomTabIdentity(uri = "bilibili://mall/home"),
            BottomTabIdentity(tabId = "我的Bottom"),
        )
        assertEquals(
            listOf(tabs[0], tabs[3]),
            filterComposeBottomTabs(tabs, settings) { it },
        )
        assertTrue(shouldBlockAnalytics(settings, "main.homepage.bottombar.0.click"))
        assertFalse(shouldBlockAnalytics(settings, "infra.net"))
        assertEquals(0xFF448AFF.toInt(), settings.categoryColor("sponsor"))
    }

    @Test
    fun settingsTransportKeepsNewColorKeysAndEveryCustomBooleanValue() {
        val stored = SettingsSnapshot(
            hideBottomMallTab = false,
            hideBottomPublishTab = true,
            disableAnalyticsReporting = true,
            categoryColors = mapOf("sponsor" to "#448AFF"),
        ).booleanSettings()
        val transported = SettingsContract.readBooleanSettingsForSync { key, default -> stored[key] ?: default }

        assertEquals(false, transported[SettingsContract.KEY_HIDE_BOTTOM_MALL_TAB])
        assertEquals(true, transported[SettingsContract.KEY_HIDE_BOTTOM_PUBLISH_TAB])
        assertEquals(true, transported[SettingsContract.KEY_DISABLE_ANALYTICS_REPORTING])
        assertEquals(stored, transported)
        SettingsContract.CATEGORIES.forEach { category ->
            assertTrue(SettingsContract.categoryColorKey(category) in SettingsContract.STRING_SETTING_KEYS)
            assertTrue(SettingsContract.categoryModeKey(category) in SettingsContract.STRING_SETTING_KEYS)
        }
    }
}
