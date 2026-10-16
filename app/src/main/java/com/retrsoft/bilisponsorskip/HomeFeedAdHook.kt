package com.retrsoft.bilisponsorskip

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

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
                    if (!current.enabled || !current.hideHomeFeedAds) return
                    @Suppress("UNCHECKED_CAST")
                    val items = param.result as? MutableList<Any?> ?: return
                    filterInPlace(items, "parser")
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
                    if (!current.enabled || !current.hideHomeFeedAds) return
                    val state = runCatching {
                        XposedHelpers.getObjectField(param.thisObject, "L\$0")
                    }.getOrNull() ?: return
                    val items = runCatching {
                        @Suppress("UNCHECKED_CAST")
                        XposedHelpers.getObjectField(state, "a") as? MutableList<Any?>
                    }.getOrNull() ?: return
                    filterInPlace(items, "adapter")
                }
            },
        )
        if (hooked.isEmpty()) error("Pegasus feed collector method not found")
        Log.d("home feed adapter filter hook installed: $FEED_COLLECTOR_CLASS.invokeSuspend")
    }

    private fun filterInPlace(items: MutableList<Any?>, source: String) {
        val before = items.size
        items.removeAll { item -> item != null && isMarkedHomeFeedAd(item.adSignals()) }
        val removed = before - items.size
        if (removed > 0) Log.d("home feed ads filtered: source=$source; removed=$removed; remaining=${items.size}")
    }

    private fun Any.adSignals(): HomeFeedAdSignals {
        val adInfo = callOrNull("getAdInfo") ?: objectFieldOrNull("adInfo")
        return HomeFeedAdSignals(
            isAdCard = callOrNull("isAdCard") == true,
            isAd = booleanFieldOrFalse("isAd") || adInfo?.callOrNull("isAd") == true,
            isAdLoc = booleanFieldOrFalse("isAdLoc") || adInfo?.callOrNull("isAdLoc") == true,
            cmMark = intFieldOrZero("cmMark").takeIf { it != 0 }
                ?: (adInfo?.callOrNull("getCmMark") as? Number)?.toInt()
                ?: 0,
            hasAdInfo = adInfo != null,
            adCallback = (objectFieldOrNull("ad_cb") as? String)
                ?: adInfo?.callOrNull("getAdCb") as? String,
        )
    }

    private fun Any.booleanFieldOrFalse(name: String): Boolean =
        runCatching { XposedHelpers.getBooleanField(this, name) }.getOrDefault(false)

    private fun Any.intFieldOrZero(name: String): Int =
        runCatching { XposedHelpers.getIntField(this, name) }.getOrDefault(0)

    private fun Any.objectFieldOrNull(name: String): Any? =
        runCatching { XposedHelpers.getObjectField(this, name) }.getOrNull()

    private companion object {
        const val PARSER_CLASS = "com.bilibili.pegasus.api.i"
        const val PARSER_METHOD = "c"
        const val FEED_COLLECTOR_CLASS = "com.bilibili.pegasus.PegasusFragment\$initFeeds\$3\$1"
    }
}
