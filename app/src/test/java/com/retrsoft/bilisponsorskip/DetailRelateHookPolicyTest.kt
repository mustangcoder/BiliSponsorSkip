package com.retrsoft.bilisponsorskip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailRelateHookPolicyTest {
    @Test
    fun moduleAndDetailSwitchMustBothBeEnabled() {
        assertTrue(effectiveHideDetailRelateAds(moduleEnabled = true, switchEnabled = true))
        assertFalse(effectiveHideDetailRelateAds(moduleEnabled = false, switchEnabled = true))
        assertFalse(effectiveHideDetailRelateAds(moduleEnabled = true, switchEnabled = false))
    }

    @Test
    fun suppressesOnlyExactCommercialType() {
        assertTrue(shouldSuppressDetailRelate("CM", enabled = true))
        assertFalse(shouldSuppressDetailRelate("AV", enabled = true))
        assertFalse(shouldSuppressDetailRelate(null, enabled = true))
        assertFalse(shouldSuppressDetailRelate("CM", enabled = false))
    }

    @Test(expected = IllegalStateException::class)
    fun installationRejectsNoMatchingMethod() {
        requireSingleDetailComponentMethod(emptyList())
    }

    @Test(expected = IllegalStateException::class)
    fun installationRejectsAmbiguousMethods() {
        val methods = AmbiguousFixture::class.java.declaredMethods.filter { it.returnType == String::class.java }
        requireSingleDetailComponentMethod(methods)
    }

    @Suppress("unused")
    private class AmbiguousFixture {
        fun first(value: Int): String = value.toString()

        fun second(value: Long): String = value.toString()
    }
}
