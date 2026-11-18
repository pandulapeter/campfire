/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

/**
 * The same archive without ZIP64, byte for byte but for its central directory and end records: every value a central
 * header's `0x0001` field holds moved into the 32-bit field at its marker, the field removed, and the ZIP64 end record
 * and locator dropped, their values (which win over the end record's, as the reader reads them) written into the end
 * record. Entry data, local headers, and any damage in them are left exactly as they are, so a damaged ZIP64 archive
 * has a twin damaged alike. A marker whose value is missing stays a marker, as the reader leaves such an entry out
 * either way. Null where the archive cannot be read that far, or a value does
 * not fit 32 bits.
 */
internal object Zip64Twin {

    fun of(archive: ByteArray): ByteArray? = try {
        convert(archive)
    } catch (_: IndexOutOfBoundsException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun convert(archive: ByteArray): ByteArray? {
        val end = (archive.size - END_SIZE downTo maxOf(0, archive.size - END_SIZE - 65_535)).firstOrNull { archive.u32(it) == END_SIGNATURE } ?: return null
        var entries = archive.u16(end + 10).toLong()
        var directorySize = archive.u32(end + 12)
        var directoryOffset = archive.u32(end + 16)
        val locator = end - LOCATOR_SIZE
        if (locator >= 0 && archive.u32(locator) == LOCATOR_SIGNATURE) {
            val record = archive.u64(locator + 8).takeIf { it in 0..(locator - RECORD_SIZE).toLong() }?.toInt() ?: return null
            if (archive.u32(record) != RECORD_SIGNATURE) return null
            entries = archive.u64(record + 32)
            directorySize = archive.u64(record + 40)
            directoryOffset = archive.u64(record + 48)
        }
        if (entries !in 0 until 0xFFFF || directoryOffset !in 0..archive.size.toLong() || directorySize !in 0..archive.size.toLong()) return null
        val directory = ByteArrayBuilder()
        var position = directoryOffset.toInt()
        repeat(entries.toInt()) {
            if (archive.u32(position) != CENTRAL_SIGNATURE) return null
            val nameLength = archive.u16(position + 28)
            val extraLength = archive.u16(position + 30)
            val commentLength = archive.u16(position + 32)
            val header = archive.copyOfRange(position, position + CENTRAL_SIZE)
            val name = archive.copyOfRange(position + CENTRAL_SIZE, position + CENTRAL_SIZE + nameLength)
            val extraStart = position + CENTRAL_SIZE + nameLength
            val extra = ByteArrayBuilder()
            var block = extraStart
            while (block + 4 <= extraStart + extraLength) {
                val id = archive.u16(block)
                val blockEnd = block + 4 + archive.u16(block + 2)
                // A block running past the header's extra fields is not read, by the streaming reader either.
                if (blockEnd > extraStart + extraLength) break
                if (id == ZIP64_EXTRA_ID) {
                    var value = block + 4
                    for (field in listOf(24, 20, 42)) {
                        if (header.u32(field) != MARKER) continue
                        if (value + 8 > blockEnd) break
                        // A value past 32 bits has no place without ZIP64; the largest one there is past every limit an
                        // entry is held to and every offset within the archive, which is all either reader makes of it.
                        header.put32(field, archive.u64(value).takeIf { it in 0 until MARKER } ?: (MARKER - 1))
                        value += 8
                    }
                } else {
                    extra.add(archive, block, blockEnd - block)
                }
                block = blockEnd
            }
            // What was not walked (bytes too few for a block's header, a block running past the end) is carried over.
            extra.add(archive, block, extraStart + extraLength - block)
            header.put16(30, extra.size)
            directory.add(header, 0, header.size)
            directory.add(name, 0, name.size)
            directory.add(extra.bytes(), 0, extra.size)
            directory.add(archive, extraStart + extraLength, commentLength)
            position = extraStart + extraLength + commentLength
        }
        val endRecord = archive.copyOfRange(end, archive.size)
        endRecord.put16(8, entries.toInt())
        endRecord.put16(10, entries.toInt())
        endRecord.put32(12, directory.size.toLong())
        endRecord.put32(16, directoryOffset)
        return archive.copyOfRange(0, directoryOffset.toInt()) + directory.bytes() + endRecord
    }

    private class ByteArrayBuilder {
        private val bytes = java.io.ByteArrayOutputStream()
        val size get() = bytes.size()
        fun add(source: ByteArray, offset: Int, length: Int) = bytes.write(source, offset, maxOf(0, length))
        fun bytes(): ByteArray = bytes.toByteArray()
    }

    private fun ByteArray.u16(at: Int) = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.u32(at: Int) = u16(at).toLong() or (u16(at + 2).toLong() shl 16)

    private fun ByteArray.u64(at: Int) = u32(at) or (u32(at + 4) shl 32)

    private fun ByteArray.put16(at: Int, value: Int) {
        this[at] = value.toByte()
        this[at + 1] = (value ushr 8).toByte()
    }

    private fun ByteArray.put32(at: Int, value: Long) {
        put16(at, (value and 0xFFFF).toInt())
        put16(at + 2, ((value ushr 16) and 0xFFFF).toInt())
    }

    private const val END_SIGNATURE = 0x06054b50L
    private const val LOCATOR_SIGNATURE = 0x07064b50L
    private const val RECORD_SIGNATURE = 0x06064b50L
    private const val CENTRAL_SIGNATURE = 0x02014b50L
    private const val END_SIZE = 22
    private const val LOCATOR_SIZE = 20
    private const val RECORD_SIZE = 56
    private const val CENTRAL_SIZE = 46
    private const val ZIP64_EXTRA_ID = 0x0001
    private const val MARKER = 0xFFFFFFFFL
}
