package com.retrsoft.bilisponsorskip

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

internal fun readHomeFeedCardSignals(
    item: Any,
    reader: ReflectiveValueReader,
): HomeFeedCardSignals {
    fun call(target: Any, name: String): Any? = runCatching { reader.call(target, name) }.getOrNull()
    fun field(target: Any, name: String): Any? = runCatching { reader.field(target, name) }.getOrNull()

    val adInfo = call(item, "getAdInfo") ?: field(item, "adInfo")
    return HomeFeedCardSignals(
        ad = HomeFeedAdSignals(
            isAdCard = call(item, "isAdCard") == true,
            isAd = field(item, "isAd") == true || adInfo?.let { call(it, "isAd") } == true,
            isAdLoc = field(item, "isAdLoc") == true || adInfo?.let { call(it, "isAdLoc") } == true,
            cmMark = (field(item, "cmMark") as? Number)?.toInt()?.takeIf { it != 0 }
                ?: (adInfo?.let { call(it, "getCmMark") } as? Number)?.toInt()
                ?: 0,
            hasAdInfo = adInfo != null,
            adCallback = (field(item, "ad_cb") as? String)
                ?: (adInfo?.let { call(it, "getAdCb") } as? String),
        ),
        goTo = (call(item, "getGoto") as? String) ?: (field(item, "goTo") as? String),
        cardGoto = (call(item, "getCardGoto") as? String) ?: (field(item, "cardGoto") as? String),
        uri = (call(item, "getUri") as? String) ?: (field(item, "uri") as? String),
    )
}

internal class HomeFeedAdHook(
    private val settings: SettingsRepository,
    private val classLoader: ClassLoader,
) {
    fun install() {
        installLegacyParserHook()
        installAdapterSubmissionHook()
    }

    private fun installLegacyParserHook() {
        val parserClass = XposedHelpers.findClassIfExists(PARSER_CLASS, classLoader)
            ?: error("Pegasus feed parser class not found")
        val hooked = XposedBridge.hookAllMethods(
            parserClass,
            PARSER_METHOD,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val current = settings.current
                    if (!current.enabled || (!current.hideHomeFeedAds && !current.hideHomeFeedLiveCards)) return
                    @Suppress("UNCHECKED_CAST")
                    val items = param.result as? MutableList<Any?> ?: return
                    filterInPlace(
                        items,
                        "parser",
                        hideAds = current.hideHomeFeedAds,
                        hideLiveCards = current.hideHomeFeedLiveCards,
                    )
                }
            },
        )
        if (hooked.isEmpty()) error("Pegasus feed parser method not found")
        Log.d("home feed ad filter hook installed: $PARSER_CLASS.$PARSER_METHOD")
    }

    private fun installAdapterSubmissionHook() {
        val collectorClass = XposedHelpers.findClassIfExists(FEED_COLLECTOR_CLASS, classLoader)
            ?: error("Pegasus feed collector class not found")
        val hooked = XposedBridge.hookAllMethods(
            collectorClass,
            "invokeSuspend",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val current = settings.current
                    if (!current.enabled || (!current.hideHomeFeedAds && !current.hideHomeFeedLiveCards)) return
                    val state = runCatching {
                        XposedHelpers.getObjectField(param.thisObject, "L\$0")
                    }.getOrNull() ?: return
                    val items = runCatching {
                        @Suppress("UNCHECKED_CAST")
                        XposedHelpers.getObjectField(state, "a") as? MutableList<Any?>
                    }.getOrNull() ?: return
                    filterInPlace(
                        items,
                        "adapter",
                        hideAds = current.hideHomeFeedAds,
                        hideLiveCards = current.hideHomeFeedLiveCards,
                    )
                }
            },
        )
        if (hooked.isEmpty()) error("Pegasus feed collector method not found")
        Log.d("home feed adapter filter hook installed: $FEED_COLLECTOR_CLASS.invokeSuspend")
    }

    private fun filterInPlace(
        items: MutableList<Any?>,
        source: String,
        hideAds: Boolean,
        hideLiveCards: Boolean,
    ) {
        val outcome = filterHomeFeedResult(items, hideAds, hideLiveCards) { item ->
            if (item == null) HomeFeedCardSignals() else readHomeFeedCardSignals(item, XposedHomeFeedReader)
        }
        if (outcome.removedAds == 0 && outcome.removedLiveCards == 0) return
        items.clear()
        items.addAll(outcome.items)
        Log.d(
            "home feed cards filtered: source=$source; ads=${outcome.removedAds}; " +
                "live=${outcome.removedLiveCards}; remaining=${items.size}",
        )
    }

    private object XposedHomeFeedReader : ReflectiveValueReader {
        override fun call(target: Any, name: String): Any? = XposedHelpers.callMethod(target, name)

        override fun field(target: Any, name: String): Any? = when (name) {
            "isAd", "isAdLoc" -> XposedHelpers.getBooleanField(target, name)
            "cmMark" -> XposedHelpers.getIntField(target, name)
            else -> XposedHelpers.getObjectField(target, name)
        }
    }

    private companion object {
        const val PARSER_CLASS = "com.bilibili.pegasus.api.i"
        const val PARSER_METHOD = "c"
        const val FEED_COLLECTOR_CLASS = "com.bilibili.pegasus.PegasusFragment\$initFeeds\$3\$1"
    }
}
