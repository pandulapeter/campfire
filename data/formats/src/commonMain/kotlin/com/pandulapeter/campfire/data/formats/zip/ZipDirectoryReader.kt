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

import com.pandulapeter.campfire.data.formats.zip.ZipReader.CENTRAL_DIRECTORY_SIGNATURE
import com.pandulapeter.campfire.data.formats.zip.ZipReader.END_OF_CENTRAL_DIRECTORY_SIGNATURE
import com.pandulapeter.campfire.data.formats.zip.ZipReader.MAX_COMMENT_LENGTH
import kotlinx.coroutines.yield

/**
 * Reads the central directory of [source] into a [ZipDirectory]: it walks as many records as the end records declare,
 * from where they say the directory starts, each one only having to lie within the file (not within the directory's
 * declared size, which archives that read everywhere else do not always keep to). The directory is refused past
 * [maxCentralDirectorySize] (as declared, and as its records are walked) and past [maxEntries] declared entries, both
 * before anything is allocated for them.
 *
 * ZIP64 is read as [centralDirectoryBounds] and [zip64Values] say. A record whose marked value cannot be had is kept
 * with [FLAG_UNRESOLVED_ZIP64], so the walk leaves it out as one it cannot read.
 */
internal suspend fun readZipDirectory(source: ZipSource, maxCentralDirectorySize: Long, maxEntries: Int): ZipDirectory {
    val size = source.size
    if (size < END_RECORD_SIZE) {
        throw ZipException("An archive of $size bytes is too short to be a zip file.")
    }
    val tailLength = minOf(size, (END_RECORD_SIZE + MAX_COMMENT_LENGTH).toLong()).toInt()
    val tail = ByteArray(tailLength)
    source.read(size - tailLength, tail, 0, tailLength)
    val endRecord = (tailLength - END_RECORD_SIZE downTo 0).firstOrNull { tail.u32(it) == END_OF_CENTRAL_DIRECTORY_SIGNATURE }
        ?: throw ZipException("Not a zip file: no end of central directory record found.")
    val bounds = centralDirectoryBounds(source, tail, size - tailLength, endRecord)
    val centralDirectorySize = bounds.size
    if (checkedSum(bounds.offset, centralDirectorySize) > size) {
        throw ZipException("The central directory reaches past the end of the $size byte archive.")
    }
    if (centralDirectorySize > maxCentralDirectorySize) {
        throw ZipException("The central directory of $centralDirectorySize bytes is larger than $maxCentralDirectorySize.")
    }
    if (bounds.entryCount > maxEntries) {
        throw ZipException("The archive declares ${bounds.entryCount} entries, more than $maxEntries.")
    }
    // No more records are walked than the archive declares, so the count just checked also bounds what is allocated.
    val totalEntries = bounds.entryCount.toInt()
    val records = SequentialReader(source)
    val builder = DirectoryBuilder(minOf(totalEntries, INITIAL_RECORD_CAPACITY))
    var position = bounds.offset
    var walked = 0L
    repeat(totalEntries) {
        val header = records.bytesAt(position, RECORD_HEADER_SIZE, "a central directory header")
        val at = records.offsetOf(position)
        if (header.u32(at) != CENTRAL_DIRECTORY_SIGNATURE) {
            throw ZipException("Missing central directory header at offset $position.")
        }
        val flags = header.u16(at + 8)
        val nameLength = header.u16(at + 28)
        val extraLength = header.u16(at + 30)
        val recordLength = RECORD_HEADER_SIZE.toLong() + nameLength + extraLength + header.u16(at + 32)
        walked = checkedSum(walked, recordLength)
        if (walked > maxCentralDirectorySize) {
            throw ZipException("The central directory's records add up to more than $maxCentralDirectorySize bytes.")
        }
        val method = header.u16(at + 10)
        val crc = header.u32(at + 16)
        var compressedSize = header.u32(at + 20)
        var uncompressedSize = header.u32(at + 24)
        var localHeaderOffset = header.u32(at + 42)
        val isZip64 = compressedSize == ZIP64_MARKER || uncompressedSize == ZIP64_MARKER || localHeaderOffset == ZIP64_MARKER
        val namePosition = position + RECORD_HEADER_SIZE
        // The extra field is read, with the name in the same slice, only where a value lives in it and it lies within the
        // file: nothing else in it is read, and an archive whose last extra field is cut short still reads.
        val readsExtra = isZip64 && checkedSum(namePosition, nameLength.toLong() + extraLength) <= size
        val name = records.bytesAt(namePosition, if (readsExtra) nameLength + extraLength else nameLength, "a $nameLength byte name")
        val nameAt = records.offsetOf(namePosition)
        var entryFlags = flags
        if (isZip64) {
            val values = if (readsExtra) {
                zip64Values(name, nameAt + nameLength, extraLength, uncompressedSize, compressedSize, localHeaderOffset)
            } else {
                null
            }
            if (values == null) {
                entryFlags = entryFlags or FLAG_UNRESOLVED_ZIP64
            } else {
                uncompressedSize = values[0]
                compressedSize = values[1]
                localHeaderOffset = values[2]
            }
        }
        // A backslash is a separator too: Windows PowerShell 5.1 writes the platform's own, against the format. Either
        // byte ends a name in every encoding a name is read in, so a directory is told apart before decoding.
        val lastByte = if (nameLength == 0) -1 else name[nameAt + nameLength - 1].toInt()
        if (lastByte != '/'.code && lastByte != '\\'.code) {
            builder.add(name, nameAt, nameLength, entryFlags, method, crc, compressedSize, uncompressedSize, localHeaderOffset)
        }
        position = checkedSum(position, recordLength)
    }
    return builder.build(source, bounds.entryDataEnd, centralDirectorySize = walked, recordCount = totalEntries)
}

/**
 * Reads a file front to back in slices large enough for a record header and the longest name, so that walking a
 * directory costs one read per slice rather than two per record, and yields every [YIELD_INTERVAL] bytes read.
 */
private class SequentialReader(private val source: ZipSource) {

    private val buffer = ByteArray(BUFFER_SIZE)
    private var bufferStart = 0L
    private var bufferLength = 0
    private var readSinceYield = 0L

    /** Where [position] is in the array [bytesAt] last returned. */
    fun offsetOf(position: Long) = (position - bufferStart).toInt()

    /** An array holding the [length] bytes at [position], which must lie within the file. */
    suspend fun bytesAt(position: Long, length: Int, what: String): ByteArray {
        if (checkedSum(position, length.toLong()) > source.size) {
            throw ZipException("Truncated archive: $what at offset $position is outside the ${source.size} byte input.")
        }
        if (position < bufferStart || position + length > bufferStart + bufferLength) {
            bufferStart = position
            bufferLength = minOf(BUFFER_SIZE.toLong(), source.size - position).toInt()
            source.read(position, buffer, 0, bufferLength)
            readSinceYield += bufferLength
            if (readSinceYield >= YIELD_INTERVAL) {
                readSinceYield = 0
                yield()
            }
        }
        return buffer
    }
}

/** The arrays of a [ZipDirectory], grown as records arrive, since a declared count is only an upper bound. */
private class DirectoryBuilder(initialCapacity: Int) {

    private val names = ByteArrayBuilder()
    private var nameStarts = IntArray(initialCapacity + 1)
    private var flags = IntArray(initialCapacity)
    private var methods = IntArray(initialCapacity)
    private var crcs = LongArray(initialCapacity)
    private var compressedSizes = LongArray(initialCapacity)
    private var uncompressedSizes = LongArray(initialCapacity)
    private var localHeaderOffsets = LongArray(initialCapacity)
    private var count = 0

    fun add(
        name: ByteArray,
        nameAt: Int,
        nameLength: Int,
        flags: Int,
        method: Int,
        crc: Long,
        compressedSize: Long,
        uncompressedSize: Long,
        localHeaderOffset: Long,
    ) {
        if (count == this.flags.size) grow()
        names.bytes(name, nameAt, nameLength)
        nameStarts[count + 1] = names.size
        this.flags[count] = flags
        methods[count] = method
        crcs[count] = crc
        compressedSizes[count] = compressedSize
        uncompressedSizes[count] = uncompressedSize
        localHeaderOffsets[count] = localHeaderOffset
        count++
    }

    fun build(source: ZipSource, entryDataEnd: Long, centralDirectorySize: Long, recordCount: Int) = ZipDirectory(
        source = source,
        entryDataEnd = entryDataEnd,
        centralDirectorySize = centralDirectorySize,
        recordCount = recordCount,
        names = names.build(),
        nameStarts = nameStarts.copyOf(count + 1),
        flags = flags.copyOf(count),
        methods = methods.copyOf(count),
        crcs = crcs.copyOf(count),
        compressedSizes = compressedSizes.copyOf(count),
        uncompressedSizes = uncompressedSizes.copyOf(count),
        localHeaderOffsets = localHeaderOffsets.copyOf(count),
    )

    private fun grow() {
        val capacity = maxOf(INITIAL_RECORD_CAPACITY, flags.size * 2)
        nameStarts = nameStarts.copyOf(capacity + 1)
        flags = flags.copyOf(capacity)
        methods = methods.copyOf(capacity)
        crcs = crcs.copyOf(capacity)
        compressedSizes = compressedSizes.copyOf(capacity)
        uncompressedSizes = uncompressedSizes.copyOf(capacity)
        localHeaderOffsets = localHeaderOffsets.copyOf(capacity)
    }
}

private const val END_RECORD_SIZE = 22
private const val RECORD_HEADER_SIZE = 46

/** A record header with the longest name and extra field the format allows, with room to spare. */
private const val BUFFER_SIZE = 192 * 1024

/** Records are allocated for as they arrive past this many, whatever the end record declares. */
private const val INITIAL_RECORD_CAPACITY = 1024
