package com.retrsoft.bilisponsorskip

internal data class BottomTabIdentity(
    val tabId: String? = null,
    val uri: String? = null,
    val publishType: Boolean = false,
)

internal fun isMallBottomTab(tab: BottomTabIdentity): Boolean =
    tab.tabId == MALL_TAB_NAME || tab.uri in MALL_TAB_URIS

internal fun isPublishBottomTab(tab: BottomTabIdentity): Boolean =
    tab.tabId == PUBLISH_TAB_NAME ||
        tab.publishType ||
        tab.uri?.startsWith(PUBLISH_TAB_URI_PREFIX) == true

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

internal const val MALL_TAB_NAME = "会员购Bottom"
internal val MALL_TAB_URIS = setOf(
    "bilibili://mall/home",
    "bilibili://mall/homepage",
)
internal const val PUBLISH_TAB_NAME = "publish"
internal const val PUBLISH_TAB_URI_PREFIX = "bilibili://uper/center_plus"
