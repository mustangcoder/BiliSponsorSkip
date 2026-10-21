package com.retrsoft.bilisponsorskip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailRelateAdFilterTest {
    @Test
    fun identifiesOnlyCommercialRelateType() {
        assertTrue(isDetailRelateAd("CM"))
        assertFalse(isDetailRelateAd("AV"))
        assertFalse(isDetailRelateAd("BANGUMI"))
        assertFalse(isDetailRelateAd("LIVE"))
        assertFalse(isDetailRelateAd("COURSE"))
        assertFalse(isDetailRelateAd("UNKNOWN"))
        assertFalse(isDetailRelateAd(""))
        assertFalse(isDetailRelateAd(null))
    }
}
