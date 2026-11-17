/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.formats.zip

/**
 * One stored entry of a [Zip64TestArchive]: [data], or, where [gap] is set, that many bytes left out of the archive (a
 * [SparseZipSource] reads them as zeroes) and declared with [gapCrc]. [marked] names the central header's fields put at
 * their markers, with their values in a `0x0001` field ([centralExtra] replaces that field's bytes whole, for a broken
 * one); [localZip64] gives the local header one with both sizes, as Info-ZIP and Python do; [descriptor] follows the
 * data with a data descriptor of 8-byte sizes (bit 3 set), with or without its signature.
 */
internal class Zip64TestEntry(
    val name: String,
    val data: ByteArray = ByteArray(0),
    val gap: Long = 0,
    val gapCrc: Long = 0,
    val marked: Set<Zip64Field> = emptySet(),
    val centralExtra: ByteArray? = null,
    val localZip64: Boolean = false,
    val descriptor: Descriptor? = null,
) {
    val size get() = if (gap > 0) gap else data.size.toLong()

    enum class Descriptor { SIGNED, UNSIGNED }
}

/** The central header fields that ZIP64 can move into a `0x0001` field, in the field's order. */
internal enum class Zip64Field { UNCOMPRESSED_SIZE, COMPRESSED_SIZE, LOCAL_HEADER_OFFSET }

/**
 * An archive laid out as the ZIP64 writers in [RealWriterArchives] lay theirs, starting at [base] (zeroes before it),
 * with every field the tests damage open to a change: the end record's 32-bit fields ([endEntries], [endSize],
 * [endOffset], null for the real value) and disk numbers, whether a ZIP64 record and locator are written at all, and
 * the record's and locator's fields, each changed by a function of its real value.
 */
internal class Zip64TestArchive(
    private val entries: List<Zip64TestEntry>,
    private val base: Long = 0,
    private val endEntries: Int? = null,
    private val endSize: Long? = null,
    private val endOffset: Long? = null,
    private val endDisks: Int = 0,
    private val writesZip64: Boolean = true,
    private val recordEntries: (Long) -> Long = { it },
    private val recordSize: (Long) -> Long = { it },
    private val recordDirectorySize: (Long) -> Long = { it },
    private val recordDirectoryOffset: (Long) -> Long = { it },
    private val recordDisks: Long = 0,
    private val locatorRecordOffset: (Long) -> Long = { it },
    private val locatorDisks: Long = 1,
) {

    private val pieces = mutableListOf<Pair<Long, ByteArray>>()
    private var pieceStart = base
    private var piece = ByteArrayBuilder()
    private val position get() = pieceStart + piece.size

    fun source(): SparseZipSource {
        val localHeaderOffsets = entries.map { writeLocal(it) }
        val directoryOffset = position
        entries.forEachIndexed { index, entry -> writeCentral(entry, localHeaderOffsets[index]) }
        val directorySize = position - directoryOffset
        if (writesZip64) {
            val recordOffset = position
            piece.u32(0x06064B50L)
            u64(recordSize(44))
            piece.u16(45)
            piece.u16(45)
            u64(recordDisks)
            u64(recordEntries(entries.size.toLong()))
            u64(recordEntries(entries.size.toLong()))
            u64(recordDirectorySize(directorySize))
            u64(recordDirectoryOffset(directoryOffset))
            piece.u32(0x07064B50L)
            piece.u32(0)
            u64(locatorRecordOffset(recordOffset))
            piece.u32(locatorDisks)
        }
        piece.u32(0x06054B50L)
        repeat(2) { piece.u16(endDisks) }
        repeat(2) { piece.u16(endEntries ?: minOf(entries.size, 0xFFFF)) }
        piece.u32(endSize ?: minOf(directorySize, MARKER))
        piece.u32(endOffset ?: minOf(directoryOffset, MARKER))
        piece.u16(0)
        pieces += pieceStart to piece.build()
        return SparseZipSource(position, pieces.toList())
    }

    private fun writeLocal(entry: Zip64TestEntry): Long {
        val offset = position
        piece.u32(0x04034B50L)
        piece.u16(45)
        piece.u16(if (entry.descriptor != null) 0x0008 else 0)
        piece.u16(0) // Stored.
        repeat(2) { piece.u16(0) } // Modification time and date.
        piece.u32(if (entry.descriptor != null) 0 else crcOf(entry))
        repeat(2) { piece.u32(if (entry.localZip64) MARKER else if (entry.descriptor != null) 0 else minOf(entry.size, MARKER)) }
        val name = entry.name.encodeToByteArray()
        piece.u16(name.size)
        piece.u16(if (entry.localZip64) 20 else 0)
        piece.bytes(name)
        if (entry.localZip64) {
            piece.u16(1)
            piece.u16(16)
            repeat(2) { u64(entry.size) }
        }
        if (entry.gap > 0) skip(entry.gap) else piece.bytes(entry.data)
        when (entry.descriptor) {
            null -> Unit
            Zip64TestEntry.Descriptor.SIGNED -> {
                piece.u32(0x08074B50L)
                descriptorFields(entry)
            }
            Zip64TestEntry.Descriptor.UNSIGNED -> descriptorFields(entry)
        }
        return offset
    }

    private fun descriptorFields(entry: Zip64TestEntry) {
        piece.u32(crcOf(entry))
        repeat(2) { u64(entry.size) }
    }

    private fun writeCentral(entry: Zip64TestEntry, localHeaderOffset: Long) {
        val values = mapOf(
            Zip64Field.UNCOMPRESSED_SIZE to entry.size,
            Zip64Field.COMPRESSED_SIZE to entry.size,
            Zip64Field.LOCAL_HEADER_OFFSET to localHeaderOffset,
        )
        val extra = entry.centralExtra ?: if (entry.marked.isEmpty()) {
            ByteArray(0)
        } else {
            ByteArrayBuilder().apply {
                u16(1)
                u16(8 * entry.marked.size)
                Zip64Field.entries.filter { it in entry.marked }.forEach { field -> u64(values.getValue(field)) }
            }.build()
        }
        fun field(field: Zip64Field) = if (field in entry.marked) MARKER else values.getValue(field)
        val name = entry.name.encodeToByteArray()
        piece.u32(0x02014B50L)
        piece.u16(45)
        piece.u16(45)
        piece.u16(if (entry.descriptor != null) 0x0008 else 0)
        piece.u16(0) // Stored.
        repeat(2) { piece.u16(0) } // Modification time and date.
        piece.u32(crcOf(entry))
        piece.u32(field(Zip64Field.COMPRESSED_SIZE))
        piece.u32(field(Zip64Field.UNCOMPRESSED_SIZE))
        piece.u16(name.size)
        piece.u16(extra.size)
        repeat(3) { piece.u16(0) } // Comment length, disk number, internal attributes.
        piece.u32(0) // External attributes.
        piece.u32(field(Zip64Field.LOCAL_HEADER_OFFSET))
        piece.bytes(name)
        piece.bytes(extra)
    }

    private fun crcOf(entry: Zip64TestEntry) = if (entry.gap > 0) entry.gapCrc else Crc32.of(entry.data)

    private fun skip(length: Long) {
        pieces += pieceStart to piece.build()
        pieceStart = position + length
        piece = ByteArrayBuilder()
    }

    private fun u64(value: Long) = piece.u64(value)

    private companion object {
        const val MARKER = 0xFFFFFFFFL
    }
}

/** Appends [value] as a little-endian 64-bit field. */
internal fun ByteArrayBuilder.u64(value: Long) {
    u32(value and 0xFFFFFFFFL)
    u32(value ushr 32)
}
