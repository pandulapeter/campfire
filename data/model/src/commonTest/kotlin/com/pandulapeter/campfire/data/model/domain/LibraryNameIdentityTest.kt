/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryNameIdentityTest {

    @Test
    fun `composed and decomposed spellings are one name`() {
        assertTrue(LibraryFiles.isSameLibraryName("café.cho", "café.cho"))
        assertTrue(LibraryFiles.isSameLibraryName("й.cho", "й.cho"))
    }

    @Test
    fun `spellings that differ only by case are one name`() {
        assertTrue(LibraryFiles.isSameLibraryName("Song.cho", "song.cho"))
        assertTrue(LibraryFiles.isSameLibraryName("İ", "i"))
        assertTrue(LibraryFiles.isSameLibraryName("ΟΔΟΣ", "οδοσ"))
        assertTrue(LibraryFiles.isSameLibraryName("ς", "σ"))
        assertTrue(LibraryFiles.isSameLibraryName("ẞ", "ß"))
    }

    @Test
    fun `different names are not one name`() {
        assertFalse(LibraryFiles.isSameLibraryName("a.cho", "b.cho"))
        assertFalse(LibraryFiles.isSameLibraryName("song.cho", "song_2.cho"))
    }

    @Test
    fun `a capital above U+FFFF is folded as one letter`() {
        assertTrue(LibraryFiles.isSameLibraryName("𞤀", "𞤢"))
        assertTrue(LibraryFiles.isSameLibraryName("𐲀.cho", "𐳀.cho"))
        assertFalse(LibraryFiles.isSameLibraryName("𞤀", "𞤁"))
    }

    @Test
    fun `the key keeps the length of the name`() {
        assertEquals("İ".length, LibraryFiles.identityKey("İ").length)
    }

    @Test
    fun `the key agrees with an ignore-case comparison of the composed names`() {
        val names = SAMPLES.flatMap { listOf(it, it.uppercase(), it.lowercase(), it.replaceFirstChar(Char::titlecase)) }.distinct()
        names.forEach { first ->
            names.forEach { second ->
                assertEquals(
                    first.normalizedToNfc().equals(second.normalizedToNfc(), ignoreCase = true),
                    LibraryFiles.isSameLibraryName(first, second),
                    "\"$first\" and \"$second\"",
                )
            }
        }
    }

    private companion object {
        /** Latin, Greek, Cyrillic, Turkish, German, Hungarian, CJK and emoji, composed and decomposed. */
        val SAMPLES = listOf(
            "song.cho", "Song.cho", "green_day-good_riddance.cho", "tukorfurogep-arviz.cho", "tükörfúrógép",
            "tükörfúrógép", "őűŐŰ", "οδος", "ΟΔΟΣ",
            "σςΣ", "Αθήνα", "катюша", "КАТЮША",
            "й", "й", "İstanbul", "istanbul", "ıstanbul", "ISTANBUL", "straße", "STRASSE", "ẞ",
            "ß", "Ångström", "Ångström", "Ångström", "東京", "東京タワー",
            "🔥 campfire", "🎸", "naïve café", "NAÏVE CAFÉ", "Ǆ", "ǅ", "ǆ",
            "ŉ", "ﬁ", "fi", "ᾈ", "ᾀ", "և", "Եւ", "a.cho", "b.cho", "",
        )
    }
}
