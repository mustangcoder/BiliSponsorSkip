package com.retrsoft.bilisponsorskip

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

internal class BottomMallTabHook(
    private val settings: SettingsRepository,
    private val classLoader: ClassLoader,
) {
    fun install() {
        var hookCount = 0
        hookCount += installResolvedTabHook()
        RESOURCE_PROVIDERS.forEach { className ->
            val providerClass = XposedHelpers.findClassIfExists(className, classLoader) ?: return@forEach
            hookCount += XposedBridge.hookAllMethods(
                providerClass,
                "c",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val current = settings.current
                        if (!current.enabled || (!current.hideBottomMallTab && !current.hideBottomPublishTab)) return
                        @Suppress("UNCHECKED_CAST")
                        val tabs = param.result as? MutableList<Any?> ?: return
                        val before = tabs.size
                        tabs.removeAll { tab ->
                            tab != null && shouldHideBottomTab(
                                tab = tab.identity(),
                                hideMall = current.hideBottomMallTab,
                                hidePublish = current.hideBottomPublishTab,
                            )
                        }
                        if (tabs.size != before) Log.d("bottom mall tab filtered: provider=$className")
                    }
                },
            ).size
        }
        if (hookCount == 0) error("bottom tab resource providers not found")
        Log.d("bottom mall tab data hooks installed: count=$hookCount")
    }

    private fun installResolvedTabHook(): Int {
        val providerClass = XposedHelpers.findClassIfExists(RESOLVED_TAB_PROVIDER, classLoader) ?: return 0
        return XposedBridge.hookAllMethods(
            providerClass,
            "a",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val current = settings.current
                    if (!current.enabled || (!current.hideBottomMallTab && !current.hideBottomPublishTab)) return
                    @Suppress("UNCHECKED_CAST")
                    val tabs = param.result as? MutableList<Any?> ?: return
                    val before = tabs.size
                    tabs.removeAll { wrapper ->
                        val tab = wrapper?.objectFieldOrNull("c") ?: return@removeAll false
                        shouldHideBottomTab(
                            tab = tab.identity(),
                            hideMall = current.hideBottomMallTab,
                            hidePublish = current.hideBottomPublishTab,
                        )
                    }
                    if (tabs.size != before) Log.d("bottom mall tab filtered: provider=resolved")
                }
            },
        ).size
    }

    private fun Any.identity() = BottomTabIdentity(
        id = objectFieldOrNull("a") as? String,
        uri = objectFieldOrNull("d") as? String,
        tabId = objectFieldOrNull("g") as? String,
    )

    private fun Any.objectFieldOrNull(name: String): Any? =
        runCatching { XposedHelpers.getObjectField(this, name) }.getOrNull()

    private companion object {
        val RESOURCE_PROVIDERS = listOf(
            "tv.danmaku.bili.ui.main2.resource.a",
            "tv.danmaku.bili.ui.main2.resource.C",
            "tv.danmaku.bili.ui.main2.resource.k",
        )
        const val RESOLVED_TAB_PROVIDER = "tv.danmaku.bili.ui.main2.Z"
    }
}
