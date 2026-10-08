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

/** What [LibraryFiles.normalizedName] makes of a title, and which names of a library folder are its files. */
internal class LibraryFilesTest {

    @Test
    fun `a normalized name survives being normalized again`() {
        // An exported name is normalized again on its way back in, so every rule here has to leave its own output
        // alone - the spelled out "and" and the abbreviated "ft" included.
        listOf("rock_and_roll", "jay_z_ft_alicia_keys", "dont_cry", "blink_182", "ac_dc", "y_m_c_a", "the_beatles").forEach { name ->
            assertEquals(name, LibraryFiles.normalizedName(name))
        }
    }

    @Test
    fun `a capped name survives being normalized again`() {
        // Cut by characters, the first pass would end on "feat", which the second pass files as "ft".
        val feather = "a".repeat(115) + " feather"
        val long = "Árvíztűrő tükörfúrógép és a hosszú cím ".repeat(8)
        val singleWord = "b".repeat(300)
        listOf(feather, long, singleWord, *DECOMPOSED_NAMES.map { it.first }.toTypedArray()).forEach { base ->
            val once = LibraryFiles.normalizedName(base)
            assertTrue(once.encodeToByteArray().size <= LibraryFiles.MAX_NAME_BYTES, once)
            assertEquals(once, LibraryFiles.normalizedName(once))
        }
        assertEquals("a".repeat(115), LibraryFiles.normalizedName(feather))
        assertEquals("b".repeat(LibraryFiles.MAX_NAME_BYTES), LibraryFiles.normalizedName(singleWord))
    }

    @Test
    fun `letters outside the basic multilingual plane are kept`() {
        // Adlam and Old Hungarian are cased, so their capitals arrive lowercased; the ideograph is CJK Extension B.
        assertEquals(codePoints(0x1E922, 0x1E922), LibraryFiles.normalizedName(codePoints(0x1E900, 0x1E922)))
        assertEquals(codePoints(0x10CC0, 0x10CC1), LibraryFiles.normalizedName(codePoints(0x10C80, 0x10CC1)))
        assertEquals("rock_${codePoints(0x20000)}_roll", LibraryFiles.normalizedName("Rock ${codePoints(0x20000)} Roll"))
        assertEquals("rock_roll", LibraryFiles.normalizedName("Rock ${codePoints(0x1F3B8)}roll"))
        listOf(codePoints(0x1E922, 0x1E922), "rock_${codePoints(0x20000)}_roll").forEach { name ->
            assertEquals(name, LibraryFiles.normalizedName(name))
        }
        val capped = LibraryFiles.normalizedName(codePoints(*IntArray(40) { 0x1E922 }))
        assertTrue(capped.encodeToByteArray().size <= LibraryFiles.MAX_NAME_BYTES, capped)
        assertFalse(capped.last().isHighSurrogate())
        assertEquals(codePoints(*IntArray(LibraryFiles.MAX_NAME_BYTES / 4) { 0x1E922 }), capped)
        assertEquals(capped, LibraryFiles.normalizedName(capped))
    }

    @Test
    fun `a decomposed name in any script folds like its composed twin`() {
        DECOMPOSED_NAMES.forEach { (decomposed, composed) ->
            assertEquals(LibraryFiles.normalizedName(composed), LibraryFiles.normalizedName(decomposed), decomposed)
        }
        assertEquals("\u043C\u0430\u0439", LibraryFiles.normalizedName("\u041C\u0430\u0438\u0306"))
        assertEquals("\u03B5\u03BB\u03BB\u03AC\u03B4\u03B1_\u03BC\u03BF\u03C5", LibraryFiles.normalizedName("\u0395\u03BB\u03BB\u03B1\u0301\u03B4\u03B1 \u03BC\u03BF\u03C5"))
        assertEquals("viet", LibraryFiles.normalizedName("Vie\u0323\u0302t"))
    }

    @Test
    fun `a name with no composed form is left alone`() {
        assertEquals("\u0939\u093F\u0928\u094D\u0926\u0940", LibraryFiles.normalizedName("\u0939\u093F\u0928\u094D\u0926\u0940"))
    }

    @Test
    fun `a decomposed name is capped where its composed twin is`() {
        assertEquals("\u0439".repeat(60), LibraryFiles.normalizedName("\u0438\u0306".repeat(100)))
        assertEquals(LibraryFiles.normalizedName("\u0439".repeat(100)), LibraryFiles.normalizedName("\u0438\u0306".repeat(100)))
    }

    @Test
    fun `letters with no decomposition are spelled out`() {
        mapOf("Gəl" to "gel", "Ɛdwoa" to "edwoa", "Ɔkɔm" to "okom", "Ŋgɔnɔ" to "ngono").forEach { (title, name) ->
            assertEquals(name, LibraryFiles.normalizedName(title))
            assertEquals(name, LibraryFiles.normalizedName(name))
        }
    }

    @Test
    fun `letters of other scripts are kept`() {
        assertEquals("катюша", LibraryFiles.normalizedName("Катюша"))
        assertEquals("ελλάδα_μου", LibraryFiles.normalizedName("Ελλάδα μου"))
        assertEquals("שלום_עולם", LibraryFiles.normalizedName("שלום עולם"))
        assertEquals("مرحبا_بالعالم", LibraryFiles.normalizedName("مرحبا بالعالم"))
        assertEquals("千と千尋の神隠し", LibraryFiles.normalizedName("千と千尋の神隠し"))
        assertEquals("हिन्दी_गीत", LibraryFiles.normalizedName("हिन्दी गीत"))
        assertEquals("кино_ft_цой", LibraryFiles.normalizedName("Кино feat. Цой"))
    }

    @Test
    fun `the cap counts UTF-8 bytes`() {
        assertEquals("я".repeat(60), LibraryFiles.normalizedName("я".repeat(300)))
        assertEquals("千".repeat(40), LibraryFiles.normalizedName("千".repeat(100)))
    }

    @Test
    fun `hidden files are not library files`() {
        assertTrue(LibraryFiles.isSongFileName("a.cho"))
        assertTrue(LibraryFiles.isSongFileName("A.CHO"))
        assertTrue(LibraryFiles.isSongFileName("a.crd"))
        assertFalse(LibraryFiles.isSongFileName("._a.cho"))
        assertFalse(LibraryFiles.isSongFileName(".cho"))
        assertFalse(LibraryFiles.isSongFileName(".DS_Store"))
        assertFalse(LibraryFiles.isSongFileName("a.txt"))
        assertTrue(LibraryFiles.isSetlistFileName("s.setlist.json"))
        assertFalse(LibraryFiles.isSetlistFileName("._s.setlist.json"))
        assertFalse(LibraryFiles.isSetlistFileName("s.json"))
        assertFalse(LibraryFileKind.SONG.matches("._a.cho"))
        assertTrue(LibraryFileKind.SETLIST.matches("s.setlist.json"))
    }

    @Test
    fun `every Latin letter folds to its base letter`() {
        assertEquals("gesi_za_woda", LibraryFiles.normalizedName("Gęsi za wodą"))
        // The same title decomposed, which is how macOS and some tools store it, arrives at the same name.
        assertEquals("gesi_za_woda", LibraryFiles.normalizedName("Ge\u0328si za woda\u0328"))
        assertEquals("isik", LibraryFiles.normalizedName("Işık"))
        assertEquals("istanbul", LibraryFiles.normalizedName("İstanbul"))
        // The cedilla spelling of the Romanian letters is at least as common as the comma below.
        assertEquals("sarki", LibraryFiles.normalizedName("Şarkı"))
        assertEquals("tara", LibraryFiles.normalizedName("Ţara"))
        assertEquals("viet_nam", LibraryFiles.normalizedName("Việt Nam"))
        assertEquals("dorde", LibraryFiles.normalizedName("Đorđe"))
        assertEquals("thu", LibraryFiles.normalizedName("Þú"))
        assertEquals("ijsselmeer", LibraryFiles.normalizedName("Ĳsselmeer"))
        listOf("gesi_za_woda", "isik", "viet_nam", "thu").forEach { assertEquals(it, LibraryFiles.normalizedName(it)) }
    }

    private companion object {

        /** One name per script in both of its forms, written as escapes so that the source file's own encoding decides nothing. */
        val DECOMPOSED_NAMES = listOf(
            "\u041C\u0430\u0438\u0306" to "\u041C\u0430\u0439",
            "\u0395\u03BB\u03BB\u03B1\u0301\u03B4\u03B1" to "\u0395\u03BB\u03BB\u03AC\u03B4\u03B1",
            "\u05E9\u05C1\u05B8\u05DC\u05D5\u05B9\u05DD" to "\u05E9\u05B8\u05C1\u05DC\u05D5\u05B9\u05DD",
            "Vie\u0323\u0302t Nam" to "Vi\u1EC7t Nam",
        )

        /** The text of [values], written as code points so that the source file's own encoding decides nothing. */
        fun codePoints(vararg values: Int) = buildString {
            values.forEach { value ->
                if (value < 0x10000) {
                    append(value.toChar())
                } else {
                    append((0xD800 + ((value - 0x10000) shr 10)).toChar())
                    append((0xDC00 + ((value - 0x10000) and 0x3FF)).toChar())
                }
            }
        }
    }
}
