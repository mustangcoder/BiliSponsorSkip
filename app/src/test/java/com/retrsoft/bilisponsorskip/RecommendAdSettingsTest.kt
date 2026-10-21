package com.retrsoft.bilisponsorskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendAdSettingsTest {
    @Test
    fun bothRecommendationFiltersDefaultToEnabled() {
        val defaults = SettingsSnapshot()

        assertTrue(defaults.skipStoryFeedAds)
        assertTrue(defaults.hideDetailRelateAds)
    }

    @Test
    fun bothKeysParticipateInBooleanSettingsTransport() {
        assertEquals("skip_story_feed_ads", SettingsContract.KEY_SKIP_STORY_FEED_ADS)
        assertEquals("hide_detail_relate_ads", SettingsContract.KEY_HIDE_DETAIL_RELATE_ADS)
        assertTrue(SettingsContract.KEY_SKIP_STORY_FEED_ADS in SettingsContract.BOOLEAN_SETTING_KEYS)
        assertTrue(SettingsContract.KEY_HIDE_DETAIL_RELATE_ADS in SettingsContract.BOOLEAN_SETTING_KEYS)

        val values = SettingsSnapshot(
            skipStoryFeedAds = false,
            hideDetailRelateAds = true,
        ).booleanSettings()
        assertEquals(false, values[SettingsContract.KEY_SKIP_STORY_FEED_ADS])
        assertEquals(true, values[SettingsContract.KEY_HIDE_DETAIL_RELATE_ADS])
    }

    @Test
    fun recommendationSwitchesRemainIndependentFromHomeFeedFilter() {
        val combinations = listOf(
            SettingsSnapshot(hideHomeFeedAds = false, skipStoryFeedAds = false, hideDetailRelateAds = false),
            SettingsSnapshot(hideHomeFeedAds = false, skipStoryFeedAds = true, hideDetailRelateAds = false),
            SettingsSnapshot(hideHomeFeedAds = true, skipStoryFeedAds = false, hideDetailRelateAds = true),
            SettingsSnapshot(hideHomeFeedAds = true, skipStoryFeedAds = true, hideDetailRelateAds = true),
        )

        assertFalse(combinations[0].skipStoryFeedAds)
        assertTrue(combinations[1].skipStoryFeedAds)
        assertTrue(combinations[2].hideDetailRelateAds)
        assertTrue(combinations[3].hideHomeFeedAds)
    }

    @Test
    fun moduleMasterSwitchControlsEachRuntimeFilter() {
        assertFalse(effectiveSkipStoryFeedAds(moduleEnabled = false, switchEnabled = true))
        assertFalse(effectiveHideDetailRelateAds(moduleEnabled = false, switchEnabled = true))
    }
}
