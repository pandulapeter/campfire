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

import com.pandulapeter.campfire.data.formats.zip.ZipReader.LOCAL_HEADER_SIGNATURE
import com.pandulapeter.campfire.data.formats.zip.ZipReader.METHOD_DEFLATE
import com.pandulapeter.campfire.data.formats.zip.ZipReader.METHOD_STORED
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import kotlinx.coroutines.yield

/**
 * One [ZipDirectory.walk]: [ZipReader.read]'s walk of an archive in memory, decision for decision, over a [ZipSource]
 * and into [EntrySink]s. Every sum of declared numbers goes through [checkedSum].
 */
internal class ZipWalk(
    private val directory: ZipDirectory,
    private val maxTotalSize: Long,
    private val limitOf: (name: String, declaredSize: Long) -> EntryPolicy,
    private val sniffedLimitOf: (name: String, prefix: ByteArray) -> Long?,
    private val sinkOf: suspend (name: String, declaredSize: Long) -> EntrySink,
) {
    private val source = directory.source
    private val read = mutableListOf<String>()
    private val unread = mutableListOf<UnreadZipEntry>()
    private var chargedSize = 0L
    private var compressedBytesRead = 0L

    // The local header is read at most once per entry, by its peek or by its read, whichever comes first.
    private var dataOffsetIndex = -1
    private var dataOffset = 0L

    suspend fun walk(): ZipWalkResult {
        for (index in 0 until directory.entryCount) {
            val name = directory.nameOf(index)
            val reason = take(index, name, limitOf(name, directory.uncompressedSizes[index]))
            if (reason != null) {
                unread += UnreadZipEntry(name, reason)
            }
            // Entries that are skipped cost no bytes, so the per-slice yields of reading never come for a directory of them.
            if (index % ENTRIES_PER_YIELD == ENTRIES_PER_YIELD - 1) yield()
        }
        return ZipWalkResult(read = read, unread = unread, chargedSize = chargedSize)
    }

    private suspend fun take(index: Int, name: String, policy: EntryPolicy): UnreadZipEntry.Reason? {
        val compressedSize = directory.compressedSizes[index]
        return when (policy) {
            EntryPolicy.Skip -> UnreadZipEntry.Reason.NOT_WANTED
            is EntryPolicy.Read -> when {
                isUnsupported(index) -> UnreadZipEntry.Reason.UNREADABLE
                isTooLarge(index, policy.limit) -> UnreadZipEntry.Reason.TOO_LARGE
                isPastArchive(compressedSize) -> UnreadZipEntry.Reason.UNREADABLE
                else -> {
                    compressedBytesRead = checkedSum(compressedBytesRead, compressedSize)
                    readWhole(index, name)
                }
            }

            EntryPolicy.Sniff -> when {
                isUnsupported(index) -> UnreadZipEntry.Reason.NOT_WANTED
                isPastArchive(compressedSize) -> UnreadZipEntry.Reason.UNREADABLE
                else -> {
                    // Charged once, for the peek and for a read after it alike, as the in-memory reader charges it.
                    compressedBytesRead = checkedSum(compressedBytesRead, compressedSize)
                    sniff(index, name)
                }
            }
        }
    }

    private fun isUnsupported(index: Int) = directory.flags[index] and 0x0001 != 0 ||
        directory.compressedSizes[index] == MARKER || directory.uncompressedSizes[index] == MARKER ||
        directory.localHeaderOffsets[index] == MARKER

    private fun isTooLarge(index: Int, limit: Long): Boolean {
        val declaredSize = directory.uncompressedSizes[index]
        return declaredSize > limit || checkedSum(chargedSize, declaredSize) > maxTotalSize
    }

    /** The entries of an archive never share their data, so together they cannot take up more of it than there is. */
    private fun isPastArchive(compressedSize: Long) = checkedSum(compressedBytesRead, compressedSize) > source.size

    private suspend fun sniff(index: Int, name: String): UnreadZipEntry.Reason? {
        val prefix = try {
            peek(index)
        } catch (_: ZipException) {
            return UnreadZipEntry.Reason.NOT_WANTED
        }
        val limit = sniffedLimitOf(name, prefix) ?: return UnreadZipEntry.Reason.NOT_WANTED
        return if (isTooLarge(index, limit)) UnreadZipEntry.Reason.TOO_LARGE else readWhole(index, name)
    }

    /** Null once the entry's sink was closed. */
    private suspend fun readWhole(index: Int, name: String): UnreadZipEntry.Reason? {
        val declaredSize = directory.uncompressedSizes[index]
        // Charged before it is read: a damaged entry has cost what it declared all the same.
        chargedSize = checkedSum(chargedSize, declaredSize)
        return try {
            sinkOf(name, declaredSize).receive(declaredSize, directory.crcs[index]) { writer -> readData(index, name, writer) }
            read += name
            null
        } catch (exception: ZipException) {
            println("Could not read \"$name\": ${exception.message}")
            UnreadZipEntry.Reason.UNREADABLE
        }
    }

    /** The first [ImportLimits.SNIFF_LENGTH] bytes of an entry, or all of it where it is shorter; nothing is checked. */
    private suspend fun peek(index: Int): ByteArray {
        val offset = dataOffsetOf(index)
        val compressedSize = directory.compressedSizes[index]
        return when (val method = directory.methods[index]) {
            METHOD_STORED -> ByteArray(minOf(ImportLimits.SNIFF_LENGTH.toLong(), compressedSize).toInt()).also {
                source.read(offset, it, 0, it.size)
            }

            METHOD_DEFLATE -> Inflater.inflatePrefix(source, offset, compressedSize, ImportLimits.SNIFF_LENGTH)
            else -> throw ZipException("Unsupported compression method $method.")
        }
    }

    private suspend fun readData(index: Int, name: String, writer: EntryWriter) {
        val offset = dataOffsetOf(index)
        val compressedSize = directory.compressedSizes[index]
        when (val method = directory.methods[index]) {
            METHOD_STORED -> {
                if (compressedSize != directory.uncompressedSizes[index]) {
                    throw ZipException("Stored entry \"$name\" declares different compressed and uncompressed sizes.")
                }
                copy(offset, compressedSize, writer)
            }

            METHOD_DEFLATE -> Inflater.inflate(source, offset, compressedSize, writer)
            else -> throw ZipException("Unsupported compression method $method for \"$name\".")
        }
    }

    private suspend fun copy(offset: Long, length: Long, writer: EntryWriter) {
        val buffer = ByteArray(minOf(COPY_BUFFER_SIZE.toLong(), length).toInt())
        var position = offset
        val end = offset + length
        while (position < end) {
            val count = minOf(buffer.size.toLong(), end - position).toInt()
            source.read(position, buffer, 0, count)
            writer.write(buffer, 0, count)
            position += count
        }
    }

    /**
     * Where the entry's data starts, after its local header. The local header's sizes are never read: they are zero
     * whenever a data descriptor follows the data, so the central directory stays the source of truth.
     */
    private suspend fun dataOffsetOf(index: Int): Long {
        if (dataOffsetIndex == index) return dataOffset
        val localHeaderOffset = directory.localHeaderOffsets[index]
        val compressedSize = directory.compressedSizes[index]
        // The in-memory reader cannot hold an entry of 2 GiB or more, and leaves it out however it is to be read; this
        // one does the same, so that both read every archive alike.
        if (compressedSize > Int.MAX_VALUE || directory.uncompressedSizes[index] > Int.MAX_VALUE) {
            throw ZipException("An entry of 2 GiB or more is not read.")
        }
        if (checkedSum(localHeaderOffset, LOCAL_HEADER_SIZE.toLong()) > source.size) {
            throw ZipException("Missing local header at offset $localHeaderOffset.")
        }
        val header = ByteArray(LOCAL_HEADER_SIZE)
        source.read(localHeaderOffset, header, 0, LOCAL_HEADER_SIZE)
        if (header.u32(0) != LOCAL_HEADER_SIGNATURE) {
            throw ZipException("Missing local header at offset $localHeaderOffset.")
        }
        val offset = localHeaderOffset + LOCAL_HEADER_SIZE + header.u16(26) + header.u16(28)
        if (checkedSum(offset, compressedSize) > source.size) {
            throw ZipException("The entry's data reaches past the end of the ${source.size} byte archive.")
        }
        dataOffsetIndex = index
        dataOffset = offset
        return offset
    }

    private companion object {
        const val MARKER = 0xFFFFFFFFL
        const val LOCAL_HEADER_SIZE = 30
        const val COPY_BUFFER_SIZE = 64 * 1024
        const val ENTRIES_PER_YIELD = 1024
    }
}
