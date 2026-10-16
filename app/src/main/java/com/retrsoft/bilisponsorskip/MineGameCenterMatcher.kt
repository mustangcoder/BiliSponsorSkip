package com.retrsoft.bilisponsorskip

internal data class MineCardSignals(
    val title: String,
    val headerResource: String,
    val itemContainerResource: String,
    val itemLabels: Set<String>,
)

internal fun isMineGameCenterCard(signals: MineCardSignals): Boolean =
    signals.title.trim() == "游戏中心" &&
        signals.headerResource == "head_layout" &&
        signals.itemContainerResource == "item_recycle" &&
        "我的游戏" in signals.itemLabels

internal enum class MineGameCenterCardAction {
    NONE,
    HIDE,
    RESTORE,
}

internal fun mineGameCenterCardAction(
    enabled: Boolean,
    matches: Boolean,
    currentlyHidden: Boolean,
): MineGameCenterCardAction = when {
    !enabled && currentlyHidden -> MineGameCenterCardAction.RESTORE
    enabled && matches && !currentlyHidden -> MineGameCenterCardAction.HIDE
    else -> MineGameCenterCardAction.NONE
}

internal fun mineGameCenterCardTargetHeight(
    enabled: Boolean,
    matches: Boolean,
    currentlyCollapsed: Boolean,
    originalHeight: Int,
): Int? = when {
    enabled && matches && !currentlyCollapsed -> 0
    !enabled && currentlyCollapsed -> originalHeight
    else -> null
}
