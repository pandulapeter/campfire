/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.localization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluralRulesTest {

    @Test
    fun `every string table has a plural rule of its own`() {
        AppLocale.entries.forEach { locale -> assertTrue(locale.code in pluralRuleLanguages, locale.code) }
    }

    @Test
    fun `english, german, and hungarian read 1 as one and every other count as other`() {
        listOf("en", "de", "hu").forEach { language ->
            assertEquals(PluralCategory.ONE, pluralCategoryOf(language, 1), language)
            listOf(0, 2, 5, 21, 101, 1_000_000).forEach { assertEquals(PluralCategory.OTHER, pluralCategoryOf(language, it), language) }
        }
    }

    @Test
    fun `french reads 0 and 1 as one`() {
        assertEquals(PluralCategory.ONE, pluralCategoryOf("fr", 0))
        assertEquals(PluralCategory.ONE, pluralCategoryOf("fr", 1))
        assertEquals(PluralCategory.OTHER, pluralCategoryOf("fr", 2))
    }

    @Test
    fun `spanish, italian, and french read a whole million as many`() {
        listOf("es", "it", "fr").forEach { language ->
            assertEquals(PluralCategory.MANY, pluralCategoryOf(language, 2_000_000), language)
            assertEquals(PluralCategory.OTHER, pluralCategoryOf(language, 1_000_001), language)
        }
        assertEquals(PluralCategory.ONE, pluralCategoryOf("es", 1))
        assertEquals(PluralCategory.ONE, pluralCategoryOf("it", 1))
    }

    @Test
    fun `romanian reads 0 and the counts ending in 1 to 19 as few`() {
        assertEquals(PluralCategory.ONE, pluralCategoryOf("ro", 1))
        listOf(0, 2, 19, 101, 119).forEach { assertEquals(PluralCategory.FEW, pluralCategoryOf("ro", it), it.toString()) }
        listOf(20, 21, 100, 120).forEach { assertEquals(PluralCategory.OTHER, pluralCategoryOf("ro", it), it.toString()) }
    }

    @Test
    fun `hebrew reads 1 as one, 2 as two, and every other count as other`() {
        assertEquals(PluralCategory.ONE, pluralCategoryOf("he", 1))
        assertEquals(PluralCategory.TWO, pluralCategoryOf("he", 2))
        listOf(0, 3, 10, 20, 100).forEach { assertEquals(PluralCategory.OTHER, pluralCategoryOf("he", it), it.toString()) }
    }

    @Test
    fun `a language without a rule reads like english`() {
        assertEquals(PluralCategory.ONE, pluralCategoryOf("xx", 1))
        assertEquals(PluralCategory.OTHER, pluralCategoryOf("xx", 2))
    }

    @Test
    fun `a zero item reads 0 before the language's own category`() {
        val items = mapOf("zero" to "none", "one" to "one song", "other" to "songs")
        assertEquals("none", pluralItemOf(items, "fr", 0))
        assertEquals("none", pluralItemOf(items, "en", 0))
        assertEquals("one song", pluralItemOf(items, "fr", 1))
    }

    @Test
    fun `a category the plural has no item for reads as other`() {
        val items = mapOf("one" to "one song", "other" to "songs")
        assertEquals("one song", pluralItemOf(items, "fr", 0))
        assertEquals("songs", pluralItemOf(items, "ro", 2))
        assertEquals("songs", pluralItemOf(items, "he", 2))
        assertEquals("songs", pluralItemOf(items, "es", 1_000_000))
    }

    @Test
    fun `hebrew reads 2 from its two item`() {
        assertEquals("two songs", pluralItemOf(mapOf("one" to "a song", "two" to "two songs", "other" to "songs"), "he", 2))
    }
}
