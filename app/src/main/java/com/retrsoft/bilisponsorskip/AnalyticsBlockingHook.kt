package com.retrsoft.bilisponsorskip

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.atomic.AtomicBoolean

internal val NEURON_ANALYTICS_METHODS = setOf(
    "report",
    "reportClick",
    "reportExposure",
    "reportH5",
    "reportH5Click",
    "reportH5Exposure",
    "reportH5Other",
    "reportH5PageView",
    "reportH5Tracker",
    "reportIjk",
    "reportPageView",
    "reportPlayer",
    "reportTracker",
    "trackCustom",
    "trackT",
    "trackTInCurrentProcess",
)

internal val INFO_EYES_ANALYTICS_METHODS = setOf("feedEvent", "report", "report2")

internal val NEURON_BUILDER_ANALYTICS_METHODS = setOf("submit")

internal fun shouldBlockAnalytics(settings: SettingsSnapshot, eventId: String? = null): Boolean =
    settings.enabled && settings.disableAnalyticsReporting && eventId !in NEURON_DIAGNOSTIC_EVENTS

internal fun shouldBlockNeuronCall(
    settings: SettingsSnapshot,
    methodName: String,
    args: Array<out Any?>,
): Boolean {
    if (methodName !in NEURON_ANALYTICS_METHODS) return false
    val eventId = if (methodName == "trackT" || methodName == "trackTInCurrentProcess") {
        args.getOrNull(1) as? String ?: return false
    } else null
    return shouldBlockAnalytics(settings, eventId)
}

internal fun shouldBlockNeuronBuilder(settings: SettingsSnapshot, builder: Any): Boolean {
    if (!shouldBlockAnalytics(settings)) return false
    // 9.15.0 的 Builder.c 对应 eventId，读取失败交由 Hook 记录并放行。
    val field = builder.javaClass.getDeclaredField("c")
    field.isAccessible = true
    val eventId = field.get(builder) as? String
    if (eventId.isNullOrBlank()) return false
    return shouldBlockAnalytics(settings, eventId)
}

private val NEURON_DIAGNOSTIC_EVENTS = setOf(
    "infra.crash",
    "infra.xcrash",
    "infra.net",
    "infra.setup",
    "infra.webimage",
)

internal class AnalyticsBlockingHook(
    private val settings: SettingsRepository,
    private val classLoader: ClassLoader,
) {
    fun install() {
        var hookCount = 0
        mapOf(
            NEURONS_CLASS to NEURON_ANALYTICS_METHODS,
            INFO_EYES_CLASS to INFO_EYES_ANALYTICS_METHODS,
            NEURON_BUILDER_CLASS to NEURON_BUILDER_ANALYTICS_METHODS,
        ).forEach { (className, methods) ->
            runCatching { hookVoidMethods(className, methods) }
                .onSuccess { hookCount += it }
                .onFailure { Log.e("failed to install analytics blocking source=$className", it) }
        }
        if (hookCount == 0) error("analytics reporting methods not found")
        Log.d("analytics blocking hooks installed: count=$hookCount")
    }

    private fun hookVoidMethods(className: String, methodNames: Set<String>): Int {
        val targetClass = XposedHelpers.findClassIfExists(className, classLoader)
            ?: error("analytics reporting class not found: $className")
        val firstBlocked = AtomicBoolean(false)
        val firstReadFailure = AtomicBoolean(false)
        var count = 0
        methodNames.forEach { methodName ->
            val methods = targetClass.declaredMethods.filter { method ->
                method.name == methodName && method.returnType == Void.TYPE
            }
            methods.forEach { method ->
                runCatching {
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val current = settings.current
                                if (!shouldBlockAnalytics(current)) return
                                val blocked = runCatching {
                                    when (className) {
                                        NEURON_BUILDER_CLASS -> shouldBlockNeuronBuilder(current, param.thisObject)
                                        NEURONS_CLASS -> shouldBlockNeuronCall(current, method.name, param.args)
                                        else -> shouldBlockAnalytics(current)
                                    }
                                }.onFailure {
                                    if (firstReadFailure.compareAndSet(false, true)) {
                                        Log.e("failed to inspect analytics event: source=$className", it)
                                    }
                                }.getOrDefault(false)
                                if (!blocked) return
                                param.result = null
                                if (firstBlocked.compareAndSet(false, true)) {
                                    Log.d("analytics event blocked: source=$className; method=${method.name}")
                                }
                            }
                        },
                    )
                }.onSuccess { count += 1 }
                    .onFailure { Log.e("failed to hook analytics method: $className.${method.name}", it) }
            }
        }
        if (count == 0) error("analytics reporting methods not found: $className")
        Log.d("analytics blocking source installed: source=$className; count=$count")
        return count
    }

    private companion object {
        const val NEURONS_CLASS = "com.bilibili.lib.neuron.api.Neurons"
        const val INFO_EYES_CLASS = "com.bilibili.lib.infoeyes.InfoEyesManager"
        const val NEURON_BUILDER_CLASS = "com.bilibili.lib.neuron.core.b\$b"
    }
}
