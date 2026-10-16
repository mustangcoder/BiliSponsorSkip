package com.retrsoft.bilisponsorskip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashAdSkipPolicyTest {
    @Test
    fun coldSplashUsesColdStartSwitch() {
        assertTrue(
            shouldSkipSplashAd(
                source = SplashAdSource.COLD,
                skipColdStart = true,
                skipHotStart = false,
            ),
        )
    }

    @Test
    fun hotSplashUsesReturnToAppSwitch() {
        assertTrue(
            shouldSkipSplashAd(
                source = SplashAdSource.HOT,
                skipColdStart = false,
                skipHotStart = true,
            ),
        )
    }

    @Test
    fun unrelatedSplashSourceIsNotSkipped() {
        assertFalse(
            shouldSkipSplashAd(
                source = SplashAdSource.OTHER,
                skipColdStart = true,
                skipHotStart = true,
            ),
        )
    }

    @Test
    fun bothSwitchesDefaultToEnabled() {
        val settings = SettingsSnapshot()

        assertTrue(settings.skipColdStartSplashAd)
        assertTrue(settings.skipHotStartSplashAd)
    }

    @Test
    fun bothSwitchesArePersistedBooleanSettings() {
        assertEquals("skip_cold_start_splash_ad", SettingsContract.KEY_SKIP_COLD_START_SPLASH_AD)
        assertEquals("skip_hot_start_splash_ad", SettingsContract.KEY_SKIP_HOT_START_SPLASH_AD)
        assertTrue(SettingsContract.KEY_SKIP_COLD_START_SPLASH_AD in SettingsContract.BOOLEAN_SETTING_KEYS)
        assertTrue(SettingsContract.KEY_SKIP_HOT_START_SPLASH_AD in SettingsContract.BOOLEAN_SETTING_KEYS)
    }

    @Test
    fun resolvesOnlyKnownNativeSplashSources() {
        assertTrue(splashAdSourceOf("COLD") == SplashAdSource.COLD)
        assertTrue(splashAdSourceOf("HOT") == SplashAdSource.HOT)
        assertTrue(splashAdSourceOf("CALL_UP") == SplashAdSource.OTHER)
    }

    @Test
    fun moduleMasterSwitchDisablesAllSplashSkipping() {
        assertFalse(
            effectiveSkipSplashAd(
                moduleEnabled = false,
                source = SplashAdSource.COLD,
                skipColdStart = true,
                skipHotStart = true,
            ),
        )
    }
}
