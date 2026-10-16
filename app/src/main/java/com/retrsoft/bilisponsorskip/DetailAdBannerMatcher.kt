package com.retrsoft.bilisponsorskip

internal data class DetailAdBannerSignals(
    val texts: Set<String>,
    val contentDescriptions: Set<String>,
)

internal fun isDetailAdBanner(signals: DetailAdBannerSignals): Boolean {
    val values = signals.texts + signals.contentDescriptions
    val hasAdLabel = values.any { it.trim() == "广告" }
    val hasDetailAction = values.any { it.contains("查看详情") }
    return isDetailAdBanner(hasAdLabel, hasDetailAction)
}

internal fun isDetailAdBanner(hasAdLabel: Boolean, hasDetailAction: Boolean): Boolean =
    hasAdLabel && hasDetailAction

internal enum class DetailAdBannerAction {
    NONE,
    HIDE,
    RESTORE,
}

internal fun detailAdBannerAction(
    enabled: Boolean,
    matches: Boolean,
    currentlyHidden: Boolean,
): DetailAdBannerAction = when {
    !enabled && currentlyHidden -> DetailAdBannerAction.RESTORE
    enabled && matches && !currentlyHidden -> DetailAdBannerAction.HIDE
    else -> DetailAdBannerAction.NONE
}

internal fun detailAdBannerContainerResource(ancestorResources: List<String>): String? {
    if (AD_FRAME_RESOURCE !in ancestorResources) return null
    val frameIndex = ancestorResources.indexOfLast { it == AD_FRAME_RESOURCE }
    return ancestorResources.subList(0, frameIndex)
        .lastOrNull { it == UNDER_PLAYER_CONTAINER_RESOURCE }
}

internal const val AD_FRAME_RESOURCE = "ad_tint_frame"
internal const val UNDER_PLAYER_CONTAINER_RESOURCE = "underplayer_container"

internal fun isKnownDetailAdFrame(resourceName: String, hasDetailAction: Boolean): Boolean =
    resourceName == AD_FRAME_RESOURCE && hasDetailAction

internal fun effectiveHideDetailAdBanner(moduleEnabled: Boolean, hideEnabled: Boolean): Boolean =
    moduleEnabled && hideEnabled
