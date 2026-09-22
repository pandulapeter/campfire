/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.storage.file

import com.pandulapeter.campfire.data.model.domain.decodeLibraryText
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The decoder lives in `:data:model`, which has no tests of its own; it is exercised here, next to the storages that
 * read every library file through it.
 */
internal class LibraryTextDecodingTest {

    @Test
    fun readsUtf8AsIs() {
        assertEquals("{title: Tükörfúrógép}", "{title: Tükörfúrógép}".encodeToByteArray().decodeLibraryText())
    }

    @Test
    fun stripsTheByteOrderMark() {
        assertEquals("{title: É}", "\uFEFF{title: É}".encodeToByteArray().decodeLibraryText())
    }

    @Test
    fun stripsEveryByteOrderMarkAtTheStart() {
        assertEquals("{title: É}", "\uFEFF\uFEFF{title: É}".encodeToByteArray().decodeLibraryText())
    }

    @Test
    fun keepsAByteOrderMarkInsideTheText() {
        assertEquals("{title: A}\n\uFEFFx", "{title: A}\n\uFEFFx".encodeToByteArray().decodeLibraryText())
    }

    @Test
    fun readsLatin1LettersOfAFileThatIsNotUtf8() {
        // "Café à la crème" in Windows-1252: every accented letter is a single byte that starts no valid UTF-8 sequence.
        val bytes = bytes(0x43, 0x61, 0x66, 0xE9, 0x20, 0xE0, 0x20, 0x6C, 0x61, 0x20, 0x63, 0x72, 0xE8, 0x6D, 0x65)

        assertEquals("Café à la crème", bytes.decodeLibraryText())
    }

    @Test
    fun readsTheTypographicRangeOfWindows1252() {
        val bytes = bytes(0x93, 0x80, 0x35, 0x96, 0x8A, 0x9E, 0x9F, 0x85, 0x94, 0x99)

        assertEquals("“€5–ŠžŸ…”™", bytes.decodeLibraryText())
    }

    @Test
    fun replacesTheBytesWindows1252LeavesUndefined() {
        assertEquals("a�����é", bytes(0x61, 0x81, 0x8D, 0x8F, 0x90, 0x9D, 0xE9).decodeLibraryText())
    }

    @Test
    fun readsAHungarianFileInWindows1250() {
        // "Árvíztűrő tükörfúrógép": the ű and the ő are on the bytes where Windows-1252 has û and õ.
        val bytes = bytes(0xC1, 0x72, 0x76, 0xED, 0x7A, 0x74, 0xFB, 0x72, 0xF5, 0x20, 0x74, 0xFC, 0x6B, 0xF6, 0x72, 0x66, 0xFA, 0x72, 0xF3, 0x67, 0xE9, 0x70)

        assertEquals("Árvíztűrő tükörfúrógép", bytes.decodeLibraryText())
    }

    @Test
    fun readsAPolishFileInWindows1250() {
        // "Gęsi za wodą, żółw"
        val bytes = bytes(0x47, 0xEA, 0x73, 0x69, 0x20, 0x7A, 0x61, 0x20, 0x77, 0x6F, 0x64, 0xB9, 0x2C, 0x20, 0xBF, 0xF3, 0xB3, 0x77)

        assertEquals("Gęsi za wodą, żółw", bytes.decodeLibraryText())
    }

    @Test
    fun keepsWesternFilesThatShareBytesWithCentralEuropeanLettersOnWindows1252() {
        // Portuguese õ next to ç and ã, Estonian Õ next to ä, a French û with no á or í, and a Spanish ¿ opening a sentence.
        assertEquals("Corações não", bytes(0x43, 0x6F, 0x72, 0x61, 0xE7, 0xF5, 0x65, 0x73, 0x20, 0x6E, 0xE3, 0x6F).decodeLibraryText())
        assertEquals("Õhtu äär", bytes(0xD5, 0x68, 0x74, 0x75, 0x20, 0xE4, 0xE4, 0x72).decodeLibraryText())
        assertEquals("Sûr été", bytes(0x53, 0xFB, 0x72, 0x20, 0xE9, 0x74, 0xE9).decodeLibraryText())
        assertEquals("¿Qué año?", bytes(0xBF, 0x51, 0x75, 0xE9, 0x20, 0x61, 0xF1, 0x6F, 0x3F).decodeLibraryText())
    }

    @Test
    fun readsUtf16WithAndWithoutByteOrderMarks() {
        val text = "{title: Tükörfúrógép}\r\n[Am]Őszi szél"
        listOf(true, false).forEach { bigEndian ->
            assertEquals(text, utf16(text, bigEndian, hasMark = true).decodeLibraryText())
            assertEquals(text, utf16(text, bigEndian, hasMark = false).decodeLibraryText())
        }
    }

    @Test
    fun preservesSurrogatePairsAndReplacesMalformedUtf16() {
        assertEquals("{c: 𝄞 segno}", utf16("{c: 𝄞 segno}", false, true).decodeLibraryText())
        assertEquals("�A", bytes(0xFF, 0xFE, 0x34, 0xD8, 0x41, 0x00).decodeLibraryText())
        assertEquals("A�", bytes(0xFF, 0xFE, 0x41, 0x00, 0x00, 0xDC).decodeLibraryText())
        assertEquals("A�", bytes(0xFF, 0xFE, 0x41, 0x00, 0x42).decodeLibraryText())
    }

    private fun utf16(text: String, isBigEndian: Boolean, hasMark: Boolean): ByteArray {
        val units = (if (hasMark) "\uFEFF" else "") + text
        return ByteArray(units.length * 2) { index ->
            val unit = units[index / 2].code
            val isHighByte = (index % 2 == 0) == isBigEndian
            (if (isHighByte) unit shr 8 else unit and 0xFF).toByte()
        }
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
