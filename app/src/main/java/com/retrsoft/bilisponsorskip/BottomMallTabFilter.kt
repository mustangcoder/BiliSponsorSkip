package com.retrsoft.bilisponsorskip

internal data class BottomTabIdentity(
    val id: String? = null,
    val uri: String? = null,
    val tabId: String? = null,
)

internal fun isMallBottomTab(tab: BottomTabIdentity): Boolean =
    tab.id == MALL_TAB_ID || tab.uri == MALL_TAB_URI || tab.tabId == MALL_TAB_NAME

internal fun isPublishBottomTab(tab: BottomTabIdentity): Boolean =
    tab.id == PUBLISH_TAB_ID || tab.uri?.startsWith(PUBLISH_TAB_URI_PREFIX) == true

internal fun shouldHideBottomTab(
    tab: BottomTabIdentity,
    hideMall: Boolean,
    hidePublish: Boolean,
): Boolean = (hideMall && isMallBottomTab(tab)) || (hidePublish && isPublishBottomTab(tab))

internal fun <T> filterMallBottomTab(
    tabs: List<T>,
    identity: (T) -> BottomTabIdentity = { it as BottomTabIdentity },
): List<T> = tabs.filterNot { isMallBottomTab(identity(it)) }

internal fun <T> filterBottomTabs(
    tabs: List<T>,
    hideMall: Boolean,
    hidePublish: Boolean,
    identity: (T) -> BottomTabIdentity = { it as BottomTabIdentity },
): List<T> = tabs.filterNot { tab ->
    shouldHideBottomTab(identity(tab), hideMall, hidePublish)
}

internal const val MALL_TAB_ID = "203"
internal const val MALL_TAB_URI = "bilibili://mall/home"
internal const val MALL_TAB_NAME = "会员购Bottom"
internal const val PUBLISH_TAB_ID = "606"
internal const val PUBLISH_TAB_URI_PREFIX = "bilibili://uper/center_plus"
