package com.retrsoft.bilisponsorskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailRelateHookSelectorTest {
    @Test
    fun selectsByExactParameterAndReturnTypesInsteadOfMethodName() {
        val selected = selectDetailComponentMethods(
            methods = SelectorFixture::class.java.declaredMethods.asList(),
            itemClassName = ItemFixture::class.java.name,
            resultClassName = ComponentFixture::class.java.name,
        )

        assertEquals(listOf("obfuscatedName"), selected.map { it.name })
    }

    @Test
    fun rejectsWrongParameterCountAndReturnType() {
        val selected = selectDetailComponentMethods(
            methods = InvalidSelectorFixture::class.java.declaredMethods.asList(),
            itemClassName = ItemFixture::class.java.name,
            resultClassName = ComponentFixture::class.java.name,
        )

        assertEquals(emptyList<java.lang.reflect.Method>(), selected)
    }

    @Test
    fun readsTheUniqueExpectedEnumField() {
        assertEquals(
            "CM",
            readDetailRelateTypeName(
                ItemFixture(CardTypeFixture.CM),
                expectedEnumClassName = CardTypeFixture::class.java.name,
            ),
        )
    }

    @Test
    fun ambiguousOrMissingEnumFieldsFailOpen() {
        assertNull(
            readDetailRelateTypeName(
                AmbiguousItemFixture(CardTypeFixture.CM, CardTypeFixture.AV),
                expectedEnumClassName = CardTypeFixture::class.java.name,
            ),
        )
        assertNull(
            readDetailRelateTypeName(
                MissingItemFixture("CM"),
                expectedEnumClassName = CardTypeFixture::class.java.name,
            ),
        )
    }

    private enum class CardTypeFixture { AV, CM }

    private class ItemFixture(@JvmField val type: CardTypeFixture)

    private class AmbiguousItemFixture(
        @JvmField val first: CardTypeFixture,
        @JvmField val second: CardTypeFixture,
    )

    private class MissingItemFixture(@JvmField val type: String)

    private class ComponentFixture

    @Suppress("unused")
    private class SelectorFixture {
        fun obfuscatedName(item: ItemFixture): ComponentFixture = ComponentFixture()

        fun similarlyNamed(item: String): ComponentFixture = ComponentFixture()
    }

    @Suppress("unused")
    private class InvalidSelectorFixture {
        fun wrongReturn(item: ItemFixture): String = item.toString()

        fun wrongCount(item: ItemFixture, extra: Int): ComponentFixture = ComponentFixture()
    }
}
