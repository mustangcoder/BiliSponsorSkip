package com.retrsoft.bilisponsorskip

internal data class HomeFeedAdSignals(
    val isAdCard: Boolean = false,
    val isAd: Boolean = false,
    val isAdLoc: Boolean = false,
    val cmMark: Int = 0,
    val hasAdInfo: Boolean = false,
    val adCallback: String? = null,
)

internal data class HomeFeedCardSignals(
    val ad: HomeFeedAdSignals = HomeFeedAdSignals(),
    val goTo: String? = null,
    val cardGoto: String? = null,
    val uri: String? = null,
)

internal data class HomeFeedFilterOutcome<T>(
    val items: List<T>,
    val removedAds: Int,
    val removedLiveCards: Int,
)

internal fun isMarkedHomeFeedAd(signals: HomeFeedAdSignals): Boolean =
    signals.isAdCard ||
        signals.isAd ||
        signals.isAdLoc ||
        signals.cmMark != 0 ||
        signals.hasAdInfo ||
        !signals.adCallback.isNullOrBlank()

internal fun isHomeFeedLiveCard(signals: HomeFeedCardSignals): Boolean =
    signals.goTo in HOME_FEED_LIVE_TYPES ||
        signals.cardGoto in HOME_FEED_LIVE_TYPES ||
        HOME_FEED_LIVE_URI_PREFIXES.any { prefix -> signals.uri?.startsWith(prefix) == true }

internal fun <T> filterMarkedHomeFeedAds(
    entries: List<T>,
    signals: (T) -> HomeFeedAdSignals,
): List<T> = entries.filterNot { isMarkedHomeFeedAd(signals(it)) }

internal fun <T> filterHomeFeedResult(
    items: List<T>,
    hideAds: Boolean,
    hideLiveCards: Boolean,
    signals: (T) -> HomeFeedCardSignals,
): HomeFeedFilterOutcome<T> {
    var removedAds = 0
    var removedLiveCards = 0
    val filtered = items.filterNot { item ->
        val cardSignals = signals(item)
        when {
            hideAds && isMarkedHomeFeedAd(cardSignals.ad) -> {
                removedAds += 1
                true
            }
            hideLiveCards && isHomeFeedLiveCard(cardSignals) -> {
                removedLiveCards += 1
                true
            }
            else -> false
        }
    }
    return HomeFeedFilterOutcome(filtered, removedAds, removedLiveCards)
}

private val HOME_FEED_LIVE_TYPES = setOf("live", "live_rcmd")

private val HOME_FEED_LIVE_URI_PREFIXES = listOf(
    "bilibili://live/",
    "https://live.bilibili.com/",
)
