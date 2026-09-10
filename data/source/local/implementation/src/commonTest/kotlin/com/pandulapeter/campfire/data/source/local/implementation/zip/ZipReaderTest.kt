/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.zip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class ZipReaderTest {

    @Test
    fun readsAStoredArchive() {
        val read = ZipReader.read(storedArchive())

        assertEquals(listOf("sub/nested.txt", "hello.txt"), read.map { it.name })
        assertEquals("nested", read[0].bytes.decodeToString())
        assertEquals("hello zip", read[1].bytes.decodeToString())
    }

    @Test
    fun skipsDirectoryEntries() {
        // The archive holds three central directory records; the "sub/" directory must not become an entry.
        assertEquals(3, storedArchive().u16(EOCD_OFFSET + 10))
        assertEquals(2, ZipReader.read(storedArchive()).size)
    }

    @Test
    fun rejectsATruncatedArchive() {
        val archive = storedArchive()

        // Without the end of central directory record the archive is not recognisable at all.
        assertFailsWith<ZipException> { ZipReader.read(archive.copyOfRange(0, archive.size - 10)) }
        // The central directory is intact but the entry data it points at is gone.
        assertFailsWith<ZipException> { ZipReader.read(archive.copyOfRange(EOCD_OFFSET, archive.size)) }
    }

    @Test
    fun rejectsAnEmptyInput() {
        assertFailsWith<ZipException> { ZipReader.read(ByteArray(0)) }
    }

    @Test
    fun rejectsAnUnsupportedCompressionMethod() {
        val archive = storedArchive()
        archive[HELLO_CENTRAL_DIRECTORY_OFFSET + 10] = 99
        archive[HELLO_CENTRAL_DIRECTORY_OFFSET + 11] = 0

        val exception = assertFailsWith<ZipException> { ZipReader.read(archive) }

        assertTrue(exception.message.orEmpty().contains("99"), "Unexpected message: ${exception.message}")
    }

    @Test
    fun rejectsAnEntryWithABadChecksum() {
        val archive = storedArchive()
        archive[HELLO_DATA_OFFSET] = 'H'.code.toByte()

        val exception = assertFailsWith<ZipException> { ZipReader.read(archive) }

        assertTrue(exception.message.orEmpty().contains("Checksum"), "Unexpected message: ${exception.message}")
    }

    @Test
    fun rejectsAnEncryptedEntry() {
        val archive = storedArchive()
        archive[HELLO_CENTRAL_DIRECTORY_OFFSET + 8] = 1

        assertFailsWith<ZipException> { ZipReader.read(archive) }
    }

    /**
     * A minimal archive produced by the system `zip -0` tool, holding the directory `sub/`, the stored file
     * `sub/nested.txt` ("nested") and the stored file `hello.txt` ("hello zip").
     */
    private fun storedArchive() = STORED_ARCHIVE_HEX.hexToByteArray()

    private fun String.hexToByteArray() = ByteArray(length / 2) {
        ((digit(this[it * 2]) shl 4) or digit(this[it * 2 + 1])).toByte()
    }

    private fun digit(character: Char) = when (character) {
        in '0'..'9' -> character - '0'
        in 'a'..'f' -> character - 'a' + 10
        else -> throw IllegalArgumentException("Not a hex digit: $character")
    }

    private companion object {
        // Offsets inside STORED_ARCHIVE_HEX, read off the archive once and asserted on by the tests above.
        const val EOCD_OFFSET = 297
        const val HELLO_CENTRAL_DIRECTORY_OFFSET = 242
        const val HELLO_DATA_OFFSET = 123

        const val STORED_ARCHIVE_HEX =
            "504b03040a00000000003b63295d0000000000000000000000000400000073756" +
                    "22f504b03040a00000000003b63295de9c2c9aa06000000060000000e0000007375622f6e" +
                    "65737465642e7478746e6573746564504b03040a00000000003b63295d8b7395ac0900000" +
                    "0090000000900000068656c6c6f2e74787468656c6c6f207a6970504b01021e030a000000" +
                    "00003b63295d000000000000000000000000040000000000000000001000ed41000000007" +
                    "375622f504b01021e030a00000000003b63295de9c2c9aa06000000060000000e00000000" +
                    "00000000000000a481220000007375622f6e65737465642e747874504b01021e030a00000" +
                    "000003b63295d8b7395ac0900000009000000090000000000000000000000a48154000000" +
                    "68656c6c6f2e747874504b05060000000003000300a5000000840000000000"
    }
}
