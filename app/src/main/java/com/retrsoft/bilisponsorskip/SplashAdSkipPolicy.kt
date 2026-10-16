package com.retrsoft.bilisponsorskip

internal enum class SplashAdSource {
    COLD,
    HOT,
    OTHER,
}

internal fun shouldSkipSplashAd(
    source: SplashAdSource,
    skipColdStart: Boolean,
    skipHotStart: Boolean,
): Boolean = when (source) {
    SplashAdSource.COLD -> skipColdStart
    SplashAdSource.HOT -> skipHotStart
    SplashAdSource.OTHER -> false
}

internal fun splashAdSourceOf(nativeName: String): SplashAdSource = when (nativeName) {
    "COLD" -> SplashAdSource.COLD
    "HOT" -> SplashAdSource.HOT
    else -> SplashAdSource.OTHER
}

internal fun effectiveSkipSplashAd(
    moduleEnabled: Boolean,
    source: SplashAdSource,
    skipColdStart: Boolean,
    skipHotStart: Boolean,
): Boolean = moduleEnabled && shouldSkipSplashAd(source, skipColdStart, skipHotStart)
