package com.duitrip.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoriesTest {

    @Test
    fun defaults_containKnownCategories() {
        assertTrue("Flight" in Categories.DEFAULT_NAMES)
        assertTrue("Other" in Categories.DEFAULT_NAMES)
        assertEquals(11, Categories.DEFAULT_NAMES.size)
    }

    @Test
    fun emojiFor_returnsMappedOrFallback() {
        assertEquals("✈️", Categories.emojiFor("Flight"))
        assertEquals("🍽️", Categories.emojiFor("Food & Drink"))
        assertEquals("🏷️", Categories.emojiFor("Unknown Category"))
    }

    @Test
    fun emojiFor_prefersCustomMap() {
        assertEquals("🎯", Categories.emojiFor("Darts", mapOf("Darts" to "🎯")))
    }

    @Test
    fun isValid_acceptsDefaultAndCustom() {
        assertTrue(Categories.isValid("Flight", emptyList()))
        assertTrue(Categories.isValid("Diving", listOf("Diving")))
        assertFalse(Categories.isValid("Nonexistent", emptyList()))
    }

    @Test
    fun maxCustomCategories_is20() {
        assertEquals(20, Categories.MAX_CUSTOM_CATEGORIES)
    }
}
