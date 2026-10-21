package com.retrsoft.bilisponsorskip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseAdHookPolicyTest {
    @Test
    fun moduleAndPauseAdSwitchMustBothBeEnabled() {
        assertTrue(effectiveHidePauseAds(moduleEnabled = true, switchEnabled = true))
        assertFalse(effectiveHidePauseAds(moduleEnabled = false, switchEnabled = true))
        assertFalse(effectiveHidePauseAds(moduleEnabled = true, switchEnabled = false))
    }

    @Test
    fun enabledFilterForcesHostPausePageOff() {
        assertFalse(pausePageEnabledResult(hostEnabled = true, hidePauseAds = true))
        assertFalse(pausePageEnabledResult(hostEnabled = false, hidePauseAds = true))
    }

    @Test
    fun disabledFilterPreservesHostDecision() {
        assertTrue(pausePageEnabledResult(hostEnabled = true, hidePauseAds = false))
        assertFalse(pausePageEnabledResult(hostEnabled = false, hidePauseAds = false))
    }
}
