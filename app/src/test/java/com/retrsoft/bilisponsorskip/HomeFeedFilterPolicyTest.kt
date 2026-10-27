package com.retrsoft.bilisponsorskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class HomeFeedFilterPolicyTest {
    @Test
    fun advertisingAndLiveSwitchesAreIndependent() {
        val feed = mixedFeed()

        assertEquals(
            listOf("video", "ad", "live", "ad-live"),
            filter(feed, hideAds = false, hideLiveCards = false).items.map { it.id },
        )
        assertEquals(
            listOf("video", "live"),
            filter(feed, hideAds = true, hideLiveCards = false).items.map { it.id },
        )
        assertEquals(
            listOf("video", "ad"),
            filter(feed, hideAds = false, hideLiveCards = true).items.map { it.id },
        )
        assertEquals(
            listOf("video"),
            filter(feed, hideAds = true, hideLiveCards = true).items.map { it.id },
        )
    }

    @Test
    fun reportsRemovalCountsWithoutMutatingInput() {
        val feed = mixedFeed()

        val outcome = filter(feed, hideAds = true, hideLiveCards = true)

        assertEquals(2, outcome.removedAds)
        assertEquals(1, outcome.removedLiveCards)
        assertEquals(listOf("video"), outcome.items.map { it.id })
        assertEquals(listOf("video", "ad", "live", "ad-live"), feed.map { it.id })
        assertNotSame(feed, outcome.items)
    }

    @Test
    fun cardMatchingBothCategoriesIsCountedAsAdvertisingFirst() {
        val outcome = filter(
            listOf(FeedEntry("ad-live", ad = true, goTo = "live")),
            hideAds = true,
            hideLiveCards = true,
        )

        assertEquals(1, outcome.removedAds)
        assertEquals(0, outcome.removedLiveCards)
        assertEquals(emptyList<FeedEntry>(), outcome.items)
    }

    private fun filter(
        entries: List<FeedEntry>,
        hideAds: Boolean,
        hideLiveCards: Boolean,
    ): HomeFeedFilterOutcome<FeedEntry> = filterHomeFeedResult(
        items = entries,
        hideAds = hideAds,
        hideLiveCards = hideLiveCards,
    ) { entry ->
        HomeFeedCardSignals(
            ad = HomeFeedAdSignals(isAd = entry.ad),
            goTo = entry.goTo,
        )
    }

    private fun mixedFeed() = listOf(
        FeedEntry("video"),
        FeedEntry("ad", ad = true),
        FeedEntry("live", goTo = "live"),
        FeedEntry("ad-live", ad = true, goTo = "live"),
    )

    private data class FeedEntry(
        val id: String,
        val ad: Boolean = false,
        val goTo: String? = null,
    )
}
