package com.retrsoft.bilisponsorskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryAdFilterTest {
    @Test
    fun identifiesEveryExplicitStoryAdSignal() {
        assertTrue(isMarkedStoryAd(StoryAdSignals(isAd = true)))
        assertTrue(isMarkedStoryAd(StoryAdSignals(isNaturalAd = true)))
        assertTrue(isMarkedStoryAd(StoryAdSignals(hasAdInfo = true)))
        assertTrue(isMarkedStoryAd(StoryAdSignals(goto = "vertical_ad_av")))
        assertTrue(isMarkedStoryAd(StoryAdSignals(goto = "vertical_ad_live")))
        assertTrue(isMarkedStoryAd(StoryAdSignals(goto = "vertical_ad_picture")))
    }

    @Test
    fun ordinaryStoryItemIsPreserved() {
        assertFalse(isMarkedStoryAd(StoryAdSignals()))
        assertFalse(isMarkedStoryAd(StoryAdSignals(goto = "vertical_av")))
    }

    @Test
    fun visiblePromotionTextIsNotAnAdSignal() {
        val signals = StoryAdSignals()

        assertFalse(isMarkedStoryAd(signals))
    }

    @Test
    fun filtersMixedAndConsecutiveAdsWithoutMutatingInput() {
        val input = listOf(
            StoryEntry("video-1", StoryAdSignals()),
            StoryEntry("ad-1", StoryAdSignals(isAd = true)),
            StoryEntry("ad-2", StoryAdSignals(hasAdInfo = true)),
            StoryEntry("video-2", StoryAdSignals()),
        )

        val filtered = filterMarkedStoryAds(input) { it.signals }

        assertEquals(listOf("video-1", "video-2"), filtered.map { it.id })
        assertEquals(listOf("video-1", "ad-1", "ad-2", "video-2"), input.map { it.id })
        assertNotSame(input, filtered)
    }

    @Test
    fun allAdListBecomesEmpty() {
        val input = listOf(
            StoryEntry("ad-1", StoryAdSignals(goto = "vertical_ad_av")),
            StoryEntry("ad-2", StoryAdSignals(isNaturalAd = true)),
        )

        assertTrue(filterMarkedStoryAds(input) { it.signals }.isEmpty())
    }

    private data class StoryEntry(
        val id: String,
        val signals: StoryAdSignals,
    )
}
