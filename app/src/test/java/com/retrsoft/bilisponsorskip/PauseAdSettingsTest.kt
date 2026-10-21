package com.retrsoft.bilisponsorskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseAdSettingsTest {
    @Test
    fun pauseAdFilterDefaultsToEnabled() {
        assertTrue(SettingsSnapshot().hidePauseAds)
    }

    @Test
    fun pauseAdKeyParticipatesInBooleanSettingsTransport() {
        assertEquals("hide_pause_ads", SettingsContract.KEY_HIDE_PAUSE_ADS)
        assertTrue(SettingsContract.KEY_HIDE_PAUSE_ADS in SettingsContract.BOOLEAN_SETTING_KEYS)
        assertEquals(
            false,
            SettingsSnapshot(hidePauseAds = false)
                .booleanSettings()[SettingsContract.KEY_HIDE_PAUSE_ADS],
        )
    }
}
