package com.retrsoft.bilisponsorskip

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

internal fun <T> filterComposeBottomTabs(
    tabs: List<T>,
    settings: SettingsSnapshot,
    identity: (T) -> BottomTabIdentity?,
): List<T>? {
    if (!settings.enabled || (!settings.hideBottomMallTab && !settings.hideBottomPublishTab)) return null
    return tabs.filterNot { tab ->
        val tabIdentity = identity(tab) ?: return@filterNot false
        shouldHideBottomTab(
            tab = tabIdentity,
            hideMall = settings.hideBottomMallTab,
            hidePublish = settings.hideBottomPublishTab,
        )
    }
}

internal class ComposeBottomTabHook(
    private val settings: SettingsRepository,
    private val classLoader: ClassLoader,
) {
    fun install() {
        val stateClass = XposedHelpers.findClassIfExists(COMPOSE_STATE_CLASS, classLoader)
            ?: error("compose bottom tab state class not found")
        val hooks = XposedBridge.hookAllConstructors(
            stateClass,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val tabs = param.args.firstOrNull() as? List<*> ?: return
                    val filtered = runCatching {
                        filterComposeBottomTabs(tabs, settings.current) { state ->
                            state?.let { readComposeBottomTabIdentity(it, XposedComposeTabReader) }
                        }
                    }.onFailure {
                        Log.e("failed to filter compose bottom tabs", it)
                    }.getOrNull() ?: return

                    param.args[0] = ArrayList(filtered)
                    val removed = tabs.size - filtered.size
                    if (removed > 0) {
                        Log.d("compose bottom tabs filtered: removed=$removed; remaining=${filtered.size}")
                    }
                }
            },
        )
        if (hooks.isEmpty()) error("compose bottom tab state constructors not found")
        Log.d("compose bottom tab data hooks installed: count=${hooks.size}")
    }

    private object XposedComposeTabReader : ReflectiveValueReader {
        override fun call(target: Any, name: String): Any? = XposedHelpers.callMethod(target, name)

        override fun field(target: Any, name: String): Any? = when (name) {
            "o" -> XposedHelpers.getBooleanField(target, name)
            else -> XposedHelpers.getObjectField(target, name)
        }
    }

    private companion object {
        const val COMPOSE_STATE_CLASS = "Qo1.e"
    }
}
