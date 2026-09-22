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
