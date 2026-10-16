package com.retrsoft.bilisponsorskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedAdFilterTest {
    @Test
    fun identifiesEveryExplicitAdMarker() {
        assertTrue(isMarkedHomeFeedAd(HomeFeedAdSignals(isAdCard = true)))
        assertTrue(isMarkedHomeFeedAd(HomeFeedAdSignals(isAd = true)))
        assertTrue(isMarkedHomeFeedAd(HomeFeedAdSignals(isAdLoc = true)))
        assertTrue(isMarkedHomeFeedAd(HomeFeedAdSignals(cmMark = 1)))
        assertTrue(isMarkedHomeFeedAd(HomeFeedAdSignals(hasAdInfo = true)))
        assertTrue(isMarkedHomeFeedAd(HomeFeedAdSignals(adCallback = "opaque-ad-callback")))
    }

    @Test
    fun ordinaryCardWithoutAdMarkersIsPreserved() {
        assertFalse(isMarkedHomeFeedAd(HomeFeedAdSignals()))
    }

    @Test
    fun removesOnlyMarkedAdsFromMixedFeed() {
        val feed = listOf(
            HomeFeedEntry("video-1", HomeFeedAdSignals()),
            HomeFeedEntry("ad", HomeFeedAdSignals(isAd = true)),
            HomeFeedEntry("live", HomeFeedAdSignals()),
        )

        assertEquals(
            listOf("video-1", "live"),
            filterMarkedHomeFeedAds(feed) { it.signals }.map { it.id },
        )
    }

    @Test
    fun filterDefaultsToEnabled() {
        assertTrue(SettingsSnapshot().hideHomeFeedAds)
    }

    @Test
    fun filterSwitchIsPersistedAsBooleanSetting() {
        assertEquals("hide_home_feed_ads", SettingsContract.KEY_HIDE_HOME_FEED_ADS)
        assertTrue(SettingsContract.KEY_HIDE_HOME_FEED_ADS in SettingsContract.BOOLEAN_SETTING_KEYS)
    }

    private data class HomeFeedEntry(
        val id: String,
        val signals: HomeFeedAdSignals,
    )
}
