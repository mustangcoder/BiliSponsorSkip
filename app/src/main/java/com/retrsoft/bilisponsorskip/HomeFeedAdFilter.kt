package com.retrsoft.bilisponsorskip

internal data class HomeFeedAdSignals(
    val isAdCard: Boolean = false,
    val isAd: Boolean = false,
    val isAdLoc: Boolean = false,
    val cmMark: Int = 0,
    val hasAdInfo: Boolean = false,
    val adCallback: String? = null,
)

internal fun isMarkedHomeFeedAd(signals: HomeFeedAdSignals): Boolean =
    signals.isAdCard ||
        signals.isAd ||
        signals.isAdLoc ||
        signals.cmMark != 0 ||
        signals.hasAdInfo ||
        !signals.adCallback.isNullOrBlank()

internal fun <T> filterMarkedHomeFeedAds(
    entries: List<T>,
    signals: (T) -> HomeFeedAdSignals,
): List<T> = entries.filterNot { isMarkedHomeFeedAd(signals(it)) }
