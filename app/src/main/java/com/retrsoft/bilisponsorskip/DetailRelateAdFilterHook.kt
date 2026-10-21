package com.retrsoft.bilisponsorskip

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicLong

internal fun effectiveHideDetailRelateAds(moduleEnabled: Boolean, switchEnabled: Boolean): Boolean =
    moduleEnabled && switchEnabled

internal fun shouldSuppressDetailRelate(cardTypeName: String?, enabled: Boolean): Boolean =
    enabled && isDetailRelateAd(cardTypeName)

internal fun selectDetailComponentMethods(
    methods: List<Method>,
    itemClassName: String,
    resultClassName: String,
): List<Method> = methods.filter { method ->
    method.parameterTypes.size == 1 &&
        method.parameterTypes[0].name == itemClassName &&
        method.returnType.name == resultClassName
}

internal fun requireSingleDetailComponentMethod(methods: List<Method>): Method =
    methods.singleOrNull() ?: error("Expected exactly one detail relate component method, found ${methods.size}")

internal fun readDetailRelateTypeName(
    item: Any,
    expectedEnumClassName: String = DETAIL_RELATE_TYPE_CLASS,
): String? = runCatching {
    val candidates = item.javaClass.declaredFields.filter { it.type.name == expectedEnumClassName }
    val field = candidates.singleOrNull() ?: return null
    field.isAccessible = true
    (field.get(item) as? Enum<*>)?.name
}.getOrNull()

internal class DetailRelateAdFilterHook(
    private val classLoader: ClassLoader,
    private val isEnabled: () -> Boolean,
) {
    private val suppressedCount = AtomicLong(0L)

    fun install() {
        val serviceClass = XposedHelpers.findClassIfExists(DETAIL_RELATE_SERVICE_CLASS, classLoader)
            ?: error("Detail relate service class not found")
        XposedHelpers.findClassIfExists(DETAIL_RELATE_ITEM_CLASS, classLoader)
            ?: error("Detail relate item class not found")
        XposedHelpers.findClassIfExists(DETAIL_RELATE_TYPE_CLASS, classLoader)
            ?: error("Detail relate type class not found")
        XposedHelpers.findClassIfExists(RUNNING_COMPONENT_CLASS, classLoader)
            ?: error("Detail relate component class not found")

        val method = requireSingleDetailComponentMethod(
            selectDetailComponentMethods(
                methods = serviceClass.declaredMethods.asList(),
                itemClassName = DETAIL_RELATE_ITEM_CLASS,
                resultClassName = RUNNING_COMPONENT_CLASS,
            ),
        )
        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val item = param.args.firstOrNull() ?: return
                val cardTypeName = readDetailRelateTypeName(item)
                if (!shouldSuppressDetailRelate(cardTypeName, isEnabled())) return
                param.result = null
                Log.d("detail relate ads suppressed: total=${suppressedCount.incrementAndGet()}")
            }
        })
        Log.d("detail relate ad filter hook installed: ${method.declaringClass.name}.${method.name}")
    }

    private companion object {
        const val DETAIL_RELATE_SERVICE_CLASS =
            "com.bilibili.ship.theseus.united.page.intro.module.relate.DetailRelateService"
        const val DETAIL_RELATE_ITEM_CLASS =
            "com.bilibili.ship.theseus.united.page.intro.module.relate.B0"
        const val RUNNING_COMPONENT_CLASS = "com.bilibili.app.gemini.ui.RunningUIComponent"
    }
}

internal const val DETAIL_RELATE_TYPE_CLASS =
    "com.bilibili.ship.theseus.united.page.intro.module.relate.RelateCardType"
