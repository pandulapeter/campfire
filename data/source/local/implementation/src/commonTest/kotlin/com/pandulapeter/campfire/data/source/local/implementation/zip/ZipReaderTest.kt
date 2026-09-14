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

    @Test
    fun rejectsAnEntryDeclaringMoreThanItCouldEverInflateTo() {
        assertFailsWith<ZipException> { ZipReader.read(deflatedArchive(declaredSize = 2L shl 30)) }
        // Past the archive limit the entry limit still stands, before a buffer of the declared size is allocated.
        val exception = assertFailsWith<ZipException> {
            ZipReader.read(deflatedArchive(declaredSize = 1L shl 30), maxTotalSize = Long.MAX_VALUE)
        }
        assertTrue(exception.message.orEmpty().contains("${Inflater.MAX_ENTRY_SIZE}"), "Unexpected message: ${exception.message}")
    }

    @Test
    fun rejectsADeclaredSizeTheStreamDoesNotProduceWithoutAllocatingIt() {
        val exception = assertFailsWith<ZipException> { ZipReader.read(deflatedArchive(declaredSize = 48L shl 20)) }

        assertTrue(exception.message.orEmpty().contains("instead of"), "Unexpected message: ${exception.message}")
    }

    @Test
    fun rejectsAnArchiveOverTheTotalLimit() {
        // The two stored entries hold 6 and 9 bytes.
        assertEquals(2, ZipReader.read(storedArchive(), maxTotalSize = 15).size)

        val exception = assertFailsWith<ZipException> { ZipReader.read(storedArchive(), maxTotalSize = 14) }

        assertTrue(exception.message.orEmpty().contains("14"), "Unexpected message: ${exception.message}")
    }

    /**
     * An archive of one DEFLATE entry whose ten byte stream is a single stored block of "hello", and whose central
     * directory claims it inflates to [declaredSize] bytes.
     */
    private fun deflatedArchive(declaredSize: Long): ByteArray {
        val name = "bomb.cho".encodeToByteArray()
        val stream = byteArrayOf(0x01, 0x05, 0x00, 0xFA.toByte(), 0xFF.toByte()) + "hello".encodeToByteArray()
        val builder = ByteArrayBuilder()
        builder.u32(0x04034B50L)
        repeat(3) { builder.u16(0) } // Version, flags, method: the central directory is the one that is read.
        repeat(2) { builder.u16(0) } // Modification time and date.
        repeat(3) { builder.u32(0) } // Checksum and sizes.
        builder.u16(name.size)
        builder.u16(0) // Extra field length.
        builder.bytes(name)
        builder.bytes(stream)
        val centralDirectoryOffset = builder.size
        builder.u32(0x02014B50L)
        builder.u16(20) // Version made by.
        builder.u16(20) // Version needed to extract.
        builder.u16(0) // Flags.
        builder.u16(8) // DEFLATE.
        repeat(2) { builder.u16(0) } // Modification time and date.
        builder.u32(0) // Checksum, never reached.
        builder.u32(stream.size.toLong())
        builder.u32(declaredSize)
        builder.u16(name.size)
        repeat(4) { builder.u16(0) } // Extra field and comment lengths, disk number, internal attributes.
        builder.u32(0) // External attributes.
        builder.u32(0) // Local header offset.
        builder.bytes(name)
        val centralDirectorySize = builder.size - centralDirectoryOffset
        builder.u32(0x06054B50L)
        repeat(2) { builder.u16(0) } // Disk numbers.
        repeat(2) { builder.u16(1) } // Entries on this disk and in total.
        builder.u32(centralDirectorySize.toLong())
        builder.u32(centralDirectoryOffset.toLong())
        builder.u16(0) // Comment length.
        return builder.build()
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
