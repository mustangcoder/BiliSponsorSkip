package com.retrsoft.bilisponsorskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedLiveSettingsTest {
    @Test
    fun liveCardFilterDefaultsToEnabled() {
        assertTrue(SettingsSnapshot().hideHomeFeedLiveCards)
    }

    @Test
    fun liveCardFilterParticipatesInBooleanSettingsTransport() {
        assertEquals(
            "hide_home_feed_live_cards",
            SettingsContract.KEY_HIDE_HOME_FEED_LIVE_CARDS,
        )
        assertTrue(
            SettingsContract.KEY_HIDE_HOME_FEED_LIVE_CARDS in SettingsContract.BOOLEAN_SETTING_KEYS,
        )
        assertEquals(
            false,
            SettingsSnapshot(hideHomeFeedLiveCards = false)
                .booleanSettings()[SettingsContract.KEY_HIDE_HOME_FEED_LIVE_CARDS],
        )
    }

    @Test
    fun advertisingAndLiveCardSettingsRemainIndependent() {
        val combinations = listOf(
            SettingsSnapshot(hideHomeFeedAds = false, hideHomeFeedLiveCards = false),
            SettingsSnapshot(hideHomeFeedAds = true, hideHomeFeedLiveCards = false),
            SettingsSnapshot(hideHomeFeedAds = false, hideHomeFeedLiveCards = true),
            SettingsSnapshot(hideHomeFeedAds = true, hideHomeFeedLiveCards = true),
        )

        assertFalse(combinations[0].hideHomeFeedAds)
        assertFalse(combinations[0].hideHomeFeedLiveCards)
        assertTrue(combinations[1].hideHomeFeedAds)
        assertFalse(combinations[1].hideHomeFeedLiveCards)
        assertFalse(combinations[2].hideHomeFeedAds)
        assertTrue(combinations[2].hideHomeFeedLiveCards)
        assertTrue(combinations[3].hideHomeFeedAds)
        assertTrue(combinations[3].hideHomeFeedLiveCards)
    }
}
