package com.retrsoft.bilisponsorskip

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

internal fun effectiveHidePauseAds(moduleEnabled: Boolean, switchEnabled: Boolean): Boolean =
    moduleEnabled && switchEnabled

internal fun pausePageEnabledResult(hostEnabled: Boolean, hidePauseAds: Boolean): Boolean =
    hostEnabled && !hidePauseAds

internal class PauseAdHook(
    private val classLoader: ClassLoader,
    private val isEnabled: () -> Boolean,
) {
    fun install() {
        XposedHelpers.findAndHookMethod(
            PAUSE_PAGE_CONFIG_CLASS,
            classLoader,
            "getPausePageEnable",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val hostEnabled = param.result as? Boolean ?: return
                    param.result = pausePageEnabledResult(hostEnabled, isEnabled())
                }
            },
        )
        Log.d("pause ad hook installed: $PAUSE_PAGE_CONFIG_CLASS.getPausePageEnable")
    }

    private companion object {
        const val PAUSE_PAGE_CONFIG_CLASS = "com.bilibili.adcommon.config.DDConfig"
    }
}
