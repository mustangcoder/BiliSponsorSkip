package com.retrsoft.bilisponsorskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryAdHookPolicyTest {
    @Test
    fun moduleAndSwitchMustBothBeEnabled() {
        assertTrue(effectiveSkipStoryFeedAds(moduleEnabled = true, switchEnabled = true))
        assertEquals(false, effectiveSkipStoryFeedAds(moduleEnabled = false, switchEnabled = true))
        assertEquals(false, effectiveSkipStoryFeedAds(moduleEnabled = true, switchEnabled = false))
    }

    @Test
    fun filtersListIntoANewIdempotentResult() {
        val ordinary = FakeStoryItem(false)
        val ad = FakeStoryItem(true)
        val original = listOf(ordinary, ad)

        val first = filterStoryAdResult(original, enabled = true, reader = FakeStoryReader)
        val second = filterStoryAdResult(first.value, enabled = true, reader = FakeStoryReader)

        assertEquals(listOf(ordinary), first.value)
        assertEquals(1, first.removed)
        assertNotSame(original, first.value)
        assertEquals(listOf(ordinary), second.value)
        assertEquals(0, second.removed)
        assertEquals(listOf(ordinary, ad), original)
    }

    @Test
    fun disabledOrNonListResultsAreUntouched() {
        val list = listOf(FakeStoryItem(true))
        assertSame(list, filterStoryAdResult(list, enabled = false, reader = FakeStoryReader).value)

        val marker = Any()
        assertSame(marker, filterStoryAdResult(marker, enabled = true, reader = FakeStoryReader).value)
        assertNull(filterStoryAdResult(null, enabled = true, reader = FakeStoryReader).value)
    }

    @Test
    fun allAdResultCanBecomeAnEmptyList() {
        val outcome = filterStoryAdResult(
            listOf(FakeStoryItem(true), FakeStoryItem(true)),
            enabled = true,
            reader = FakeStoryReader,
        )

        assertEquals(emptyList<Any>(), outcome.value)
        assertEquals(2, outcome.removed)
    }

    private data class FakeStoryItem(val ad: Boolean)

    private object FakeStoryReader : ReflectiveValueReader {
        override fun call(target: Any, name: String): Any? = when (name) {
            "isAd" -> (target as FakeStoryItem).ad
            else -> null
        }

        override fun field(target: Any, name: String): Any? = null
    }
}
