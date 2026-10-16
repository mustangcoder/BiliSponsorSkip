package com.retrsoft.bilisponsorskip

import android.app.Activity
import android.os.Bundle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

internal class SplashAdHook(
    private val settings: SettingsRepository,
    private val classLoader: ClassLoader,
) {
    fun install() {
        installOrderSelectionHook()
        installHotSplashFallback()
    }

    private fun installOrderSelectionHook() {
        val selectorClass = XposedHelpers.findClassIfExists(SELECTOR_CLASS, classLoader)
            ?: error("splash selector class not found")
        val hooked = XposedBridge.hookAllMethods(
            selectorClass,
            SELECTOR_METHOD,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val sourceName = (param.args.firstOrNull { it is Enum<*> } as? Enum<*>)?.name ?: return
                    val source = splashAdSourceOf(sourceName)
                    val current = settings.current
                    val skip = effectiveSkipSplashAd(
                        moduleEnabled = current.enabled,
                        source = source,
                        skipColdStart = current.skipColdStartSplashAd,
                        skipHotStart = current.skipHotStartSplashAd,
                    )
                    if (!skip) return
                    param.result = null
                    Log.d("splash ad selection skipped: source=$sourceName")
                }
            },
        )
        if (hooked.isEmpty()) error("splash selector method not found")
        Log.d("splash ad selection hook installed: $SELECTOR_CLASS.$SELECTOR_METHOD")
    }

    private fun installHotSplashFallback() {
        val activityClass = XposedHelpers.findClassIfExists(HOT_SPLASH_ACTIVITY, classLoader)
            ?: error("hot splash activity not found")
        XposedHelpers.findAndHookMethod(
            activityClass,
            "onCreate",
            Bundle::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val current = settings.current
                    if (!current.enabled || !current.skipHotStartSplashAd) return
                    (param.thisObject as? Activity)?.finish()
                    Log.d("hot splash activity finished")
                }
            },
        )
        Log.d("hot splash fallback hook installed: $HOT_SPLASH_ACTIVITY.onCreate")
    }

    private companion object {
        const val SELECTOR_CLASS = "tv.danmaku.bili.splash.ad.core.SplashOrderSelectComponentKt"
        const val SELECTOR_METHOD = "loadSplashForShowInner"
        const val HOT_SPLASH_ACTIVITY = "tv.danmaku.bili.splash.ad.page.HotSplashActivity"
    }
}
