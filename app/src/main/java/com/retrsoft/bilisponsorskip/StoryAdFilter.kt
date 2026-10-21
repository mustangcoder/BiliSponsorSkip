package com.retrsoft.bilisponsorskip

internal data class StoryAdSignals(
    val isAd: Boolean = false,
    val isNaturalAd: Boolean = false,
    val hasAdInfo: Boolean = false,
    val goto: String? = null,
)

internal fun isMarkedStoryAd(signals: StoryAdSignals): Boolean =
    signals.isAd ||
        signals.isNaturalAd ||
        signals.hasAdInfo ||
        signals.goto in STORY_AD_GOTO_VALUES

internal fun <T> filterMarkedStoryAds(
    items: List<T>,
    signals: (T) -> StoryAdSignals,
): List<T> = items.filterNot { isMarkedStoryAd(signals(it)) }

private val STORY_AD_GOTO_VALUES = setOf(
    "vertical_ad_av",
    "vertical_ad_live",
    "vertical_ad_picture",
)
