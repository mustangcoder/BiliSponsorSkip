package com.retrsoft.bilisponsorskip

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.ParameterizedType

internal fun isComposeBottomStateClass(type: Class<*>): Boolean = runCatching {
    if (type.declaredConstructors.none { constructor ->
            constructor.parameterTypes.size >= 4 &&
                constructor.parameterTypes.take(4).all { List::class.java.isAssignableFrom(it) }
        }
    ) return false
    val field = type.getDeclaredField("a")
    if (!List::class.java.isAssignableFrom(field.type)) return false
    val tabState = (field.genericType as? ParameterizedType)?.actualTypeArguments?.singleOrNull() as? Class<*>
        ?: return false
    val tabData = tabState.getDeclaredField("a").type
    tabState.getDeclaredField("o").type == Boolean::class.javaPrimitiveType &&
        tabData.getDeclaredField("h").type == String::class.java &&
        tabData.getDeclaredField("c").type == String::class.java
}.getOrDefault(false)

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
        val stateClasses = COMPOSE_STATE_CLASSES.mapNotNull { XposedHelpers.findClassIfExists(it, classLoader) }
            .filter(::isComposeBottomStateClass)
        if (stateClasses.isEmpty()) error("compatible compose bottom tab state class not found")
        var hookCount = 0
        stateClasses.forEach { stateClass ->
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
            hookCount += hooks.size
        }
        if (hookCount == 0) error("compose bottom tab state constructors not found")
        Log.d("compose bottom tab data hooks installed: count=$hookCount; classes=${stateClasses.joinToString { it.name }}")
    }

    private object XposedComposeTabReader : ReflectiveValueReader {
        override fun call(target: Any, name: String): Any? = XposedHelpers.callMethod(target, name)

        override fun field(target: Any, name: String): Any? = when (name) {
            "o" -> XposedHelpers.getBooleanField(target, name)
            else -> XposedHelpers.getObjectField(target, name)
        }
    }

    private companion object {
        val COMPOSE_STATE_CLASSES = listOf("Qo1.e", "Yu1.e")
    }
}
