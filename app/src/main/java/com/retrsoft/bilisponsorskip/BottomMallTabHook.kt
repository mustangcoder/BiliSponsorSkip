package com.retrsoft.bilisponsorskip

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

internal fun readBottomTabIdentity(
    tab: Any,
    reader: ReflectiveValueReader,
): BottomTabIdentity {
    fun field(name: String): Any? = runCatching { reader.field(tab, name) }.getOrNull()

    return BottomTabIdentity(
        tabId = field("a") as? String,
        uri = field("d") as? String,
        publishType = field("k") == true,
    )
}

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
        val methods = RESOLVED_TAB_PROVIDERS.flatMap { className ->
            val providerClass = XposedHelpers.findClassIfExists(className, classLoader) ?: return@flatMap emptyList()
            providerClass.declaredMethods.filter { method ->
                method.name == "a" && method.parameterTypes.isEmpty() &&
                    List::class.java.isAssignableFrom(method.returnType)
            }
        }
        methods.forEach { method ->
            XposedBridge.hookMethod(
                method,
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
            )
        }
        return methods.size
    }

    private fun Any.identity() = readBottomTabIdentity(this, XposedBottomTabReader)

    private fun Any.objectFieldOrNull(name: String): Any? =
        runCatching { XposedHelpers.getObjectField(this, name) }.getOrNull()

    private object XposedBottomTabReader : ReflectiveValueReader {
        override fun call(target: Any, name: String): Any? = XposedHelpers.callMethod(target, name)

        override fun field(target: Any, name: String): Any? = when (name) {
            "k" -> XposedHelpers.getBooleanField(target, name)
            else -> XposedHelpers.getObjectField(target, name)
        }
    }

    private companion object {
        val RESOURCE_PROVIDERS = listOf(
            "tv.danmaku.bili.ui.main2.resource.a",
            "tv.danmaku.bili.ui.main2.resource.C",
            "tv.danmaku.bili.ui.main2.resource.k",
        )
        val RESOLVED_TAB_PROVIDERS = listOf("tv.danmaku.bili.ui.main2.Z", "tv.danmaku.bili.ui.main2.e0")
    }
}
