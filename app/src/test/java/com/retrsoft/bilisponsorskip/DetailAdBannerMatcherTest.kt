package com.retrsoft.bilisponsorskip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailAdBannerMatcherTest {
    @Test
    fun matchesCommercialBannerWithAdLabelAndDetailAction() {
        val signals = DetailAdBannerSignals(
            texts = setOf("广告", "上京东 点外卖享超级补贴", "查看详情"),
            contentDescriptions = emptySet(),
        )

        assertTrue(isDetailAdBanner(signals))
    }

    @Test
    fun doesNotMatchOrdinaryVideoContentWithOnlyDetailText() {
        val signals = DetailAdBannerSignals(
            texts = setOf("查看详情", "视频简介"),
            contentDescriptions = emptySet(),
        )

        assertFalse(isDetailAdBanner(signals))
    }

    @Test
    fun hideDetailAdBannerDefaultsToEnabled() {
        assertTrue(SettingsSnapshot().hideDetailAdBanner)
    }

    @Test
    fun hideDetailAdBannerIsPersistedAsBooleanSetting() {
        assertEquals("hide_detail_ad_banner", SettingsContract.KEY_HIDE_DETAIL_AD_BANNER)
        assertTrue(SettingsContract.KEY_HIDE_DETAIL_AD_BANNER in SettingsContract.BOOLEAN_SETTING_KEYS)
    }

    @Test
    fun matchingVisibleBannerIsHiddenWhenEnabled() {
        assertEquals(
            DetailAdBannerAction.HIDE,
            detailAdBannerAction(enabled = true, matches = true, currentlyHidden = false),
        )
    }

    @Test
    fun hiddenBannerIsRestoredWhenSettingIsDisabled() {
        assertEquals(
            DetailAdBannerAction.RESTORE,
            detailAdBannerAction(enabled = false, matches = true, currentlyHidden = true),
        )
    }

    @Test
    fun ordinaryContentIsNotChanged() {
        assertEquals(
            DetailAdBannerAction.NONE,
            detailAdBannerAction(enabled = true, matches = false, currentlyHidden = false),
        )
    }

    @Test
    fun collapsesUnderPlayerContainerForKnownAdFrame() {
        assertEquals(
            "underplayer_container",
            detailAdBannerContainerResource(
                listOf("content_container", "underplayer_container", "", "ad_tint_frame"),
            ),
        )
    }

    @Test
    fun doesNotCollapseGenericUnderPlayerContentWithoutAdFrame() {
        assertEquals(
            null,
            detailAdBannerContainerResource(
                listOf("content_container", "underplayer_container", "activity_card"),
            ),
        )
    }

    @Test
    fun knownAdFrameWithDetailActionDoesNotRequireVisibleAdLabel() {
        assertTrue(
            isKnownDetailAdFrame(
                resourceName = "ad_tint_frame",
                hasDetailAction = true,
            ),
        )
    }

    @Test
    fun moduleMasterSwitchDisablesBannerHiding() {
        assertFalse(effectiveHideDetailAdBanner(moduleEnabled = false, hideEnabled = true))
    }
}
