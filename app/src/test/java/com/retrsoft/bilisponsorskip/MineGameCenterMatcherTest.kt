package com.retrsoft.bilisponsorskip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MineGameCenterMatcherTest {
    @Test
    fun matchesCompleteGameCenterCard() {
        val signals = MineCardSignals(
            title = "游戏中心",
            headerResource = "head_layout",
            itemContainerResource = "item_recycle",
            itemLabels = setOf("我的游戏", "找游戏", "游戏排行榜", "游戏礼包"),
        )

        assertTrue(isMineGameCenterCard(signals))
    }

    @Test
    fun doesNotMatchTopNavigationGameCenterButton() {
        val signals = MineCardSignals(
            title = "游戏中心",
            headerResource = "",
            itemContainerResource = "",
            itemLabels = emptySet(),
        )

        assertFalse(isMineGameCenterCard(signals))
    }

    @Test
    fun doesNotMatchOtherMineCardUsingSameLayoutResources() {
        val signals = MineCardSignals(
            title = "我的服务",
            headerResource = "head_layout",
            itemContainerResource = "item_recycle",
            itemLabels = setOf("小程序", "我的课程", "免流量服务", "个性装扮"),
        )

        assertFalse(isMineGameCenterCard(signals))
    }

    @Test
    fun acceptsGameCenterCardWhenPromotedItemChanges() {
        val signals = MineCardSignals(
            title = "游戏中心",
            headerResource = "head_layout",
            itemContainerResource = "item_recycle",
            itemLabels = setOf("我的游戏", "新游戏入口"),
        )

        assertTrue(isMineGameCenterCard(signals))
    }

    @Test
    fun hideGameCenterCardDefaultsToEnabled() {
        assertTrue(SettingsSnapshot().hideMineGameCenterCard)
    }

    @Test
    fun hideGameCenterCardIsPersistedAsBooleanSetting() {
        assertTrue(SettingsContract.KEY_HIDE_MINE_GAME_CENTER_CARD in SettingsContract.BOOLEAN_SETTING_KEYS)
    }

    @Test
    fun matchingVisibleCardIsHiddenWhenEnabled() {
        assertEquals(
            MineGameCenterCardAction.HIDE,
            mineGameCenterCardAction(enabled = true, matches = true, currentlyHidden = false),
        )
    }

    @Test
    fun hiddenCardIsRestoredWhenSettingIsDisabled() {
        assertEquals(
            MineGameCenterCardAction.RESTORE,
            mineGameCenterCardAction(enabled = false, matches = true, currentlyHidden = true),
        )
    }

    @Test
    fun hiddenRecyclerItemCollapsesItsLayoutHeight() {
        assertEquals(
            0,
            mineGameCenterCardTargetHeight(
                enabled = true,
                matches = true,
                currentlyCollapsed = false,
                originalHeight = -2,
            ),
        )
    }

    @Test
    fun disabledSettingRestoresOriginalLayoutHeight() {
        assertEquals(
            -2,
            mineGameCenterCardTargetHeight(
                enabled = false,
                matches = true,
                currentlyCollapsed = true,
                originalHeight = -2,
            ),
        )
    }
}
