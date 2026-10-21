package com.retrsoft.bilisponsorskip

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

internal interface ReflectiveValueReader {
    fun call(target: Any, name: String): Any?

    fun field(target: Any, name: String): Any?
}

internal data class StoryAdFilterOutcome(
    val value: Any?,
    val removed: Int,
)

internal fun effectiveSkipStoryFeedAds(moduleEnabled: Boolean, switchEnabled: Boolean): Boolean =
    moduleEnabled && switchEnabled

internal fun readStoryAdSignals(
    item: Any,
    reader: ReflectiveValueReader,
): StoryAdSignals {
    fun call(name: String): Any? = runCatching { reader.call(item, name) }.getOrNull()
    fun field(name: String): Any? = runCatching { reader.field(item, name) }.getOrNull()

    return StoryAdSignals(
        isAd = call("isAd") == true,
        isNaturalAd = call("isNaturalAd") == true,
        hasAdInfo = (call("getAdInfo") ?: field("adInfo")) != null,
        goto = (call("getGoto") as? String) ?: (field("goto") as? String),
    )
}

internal fun filterStoryAdResult(
    value: Any?,
    enabled: Boolean,
    reader: ReflectiveValueReader,
): StoryAdFilterOutcome {
    if (!enabled || value !is List<*>) return StoryAdFilterOutcome(value, 0)
    val filtered = value.filterNot { item ->
        item != null && isMarkedStoryAd(readStoryAdSignals(item, reader))
    }
    return StoryAdFilterOutcome(filtered, value.size - filtered.size)
}

internal class StoryAdFilterHook(
    private val classLoader: ClassLoader,
    private val isEnabled: () -> Boolean,
) {
    fun install() {
        val responseClass = XposedHelpers.findClassIfExists(STORY_RESPONSE_CLASS, classLoader)
            ?: error("Story feed response class not found")
        val hooked = XposedBridge.hookAllMethods(
            responseClass,
            "getItems",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val outcome = filterStoryAdResult(
                        value = param.result,
                        enabled = isEnabled(),
                        reader = XposedStoryValueReader,
                    )
                    if (outcome.removed == 0) return
                    param.result = outcome.value
                    val remaining = (outcome.value as? List<*>)?.size ?: 0
                    Log.d("story ads filtered: removed=${outcome.removed}; remaining=$remaining")
                }
            },
        )
        if (hooked.isEmpty()) error("Story feed items getter not found")
        Log.d("story ad filter hook installed: $STORY_RESPONSE_CLASS.getItems")
    }

    private object XposedStoryValueReader : ReflectiveValueReader {
        override fun call(target: Any, name: String): Any? = XposedHelpers.callMethod(target, name)

        override fun field(target: Any, name: String): Any? = XposedHelpers.getObjectField(target, name)
    }

    private companion object {
        const val STORY_RESPONSE_CLASS = "com.bilibili.video.story.api.StoryFeedResponse"
    }
}
