package com.retrsoft.bilisponsorskip

import com.alibaba.fastjson.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostHookCompatibilityTest {
    @Test
    fun parserSelectionRejectsAReusedSyntheticClassAndAcceptsTheRealListParser() {
        assertTrue(selectHomeFeedParserMethods(ReusedParser::class.java.declaredMethods.asList()).isEmpty())
        assertEquals(
            listOf("c"),
            selectHomeFeedParserMethods(FeedParser::class.java.declaredMethods.asList()).map { it.name },
        )
        assertTrue(selectHomeFeedParserMethods(WrongParser::class.java.declaredMethods.asList()).isEmpty())
        assertTrue(selectHomeFeedParserMethods(WrongNamespaceParser::class.java.declaredMethods.asList()).isEmpty())
    }

    @Test
    fun detailSelectionUsesThe918ItemEvenWhenTheOldItemStillExists() {
        val methods = selectDetailComponentMethods(
            methods = DetailFactory::class.java.declaredMethods.asList(),
            itemClassNames = setOf(LegacyItem::class.java.name, ModernItem::class.java.name),
            resultClassName = Component::class.java.name,
        )

        assertEquals("createModern", requireSingleDetailComponentMethod(methods).name)
    }

    @Test
    fun composeSelectionValidatesTheStateAndNestedTabIdentityShape() {
        assertTrue(isComposeBottomStateClass(ComposeState::class.java))
        assertFalse(isComposeBottomStateClass(ReusedState::class.java))
        assertFalse(isComposeBottomStateClass(Any::class.java))
    }

    class ReusedParser {
        fun invoke(value: Any): Any = value
    }

    class FeedParser {
        fun c(value: JSONArray): ArrayList<Any> = arrayListOf(value)
    }

    class WrongParser {
        fun c(value: JSONArray): String = value.toString()
    }

    class WrongNamespaceParser {
        fun c(value: org.json.JSONArray): ArrayList<Any> = arrayListOf(value)
    }

    class LegacyItem
    class ModernItem
    class Component

    class DetailFactory {
        fun obsolete(value: LegacyItem): String = value.toString()
        fun createModern(value: ModernItem): Component = Component()
    }

    class TabData(@JvmField val h: String, @JvmField val c: String)
    class TabState(@JvmField val a: TabData, @JvmField val o: Boolean)
    class ComposeState(
        @JvmField val a: List<TabState>,
        val b: List<Any>,
        val c: List<Any>,
        val d: List<Any>,
    )

    class ReusedState(
        @JvmField val a: List<Any>,
        val b: List<Any>,
        val c: List<Any>,
        val d: List<Any>,
    )
}
