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

import com.pandulapeter.campfire.data.model.importing.ImportLimits

/**
 * The file entries of an archive as [ZipReader.open] read its central directory, directories left out, kept compactly
 * (sizes, offsets, and checksums in primitive arrays, names as the directory's bytes, decoded only when walked) so
 * that a directory of [ImportLimits.MAX_ARCHIVE_ENTRIES] records stays a small part of a phone's heap. Nothing of an
 * entry's data has been read yet: [walk] does that.
 */
class ZipDirectory internal constructor(
    internal val source: ZipSource,
    /** How many bytes of central directory records were read, which is what holding the directory is charged as. */
    val centralDirectorySize: Long,
    /** How many records the central directory holds, directories included. */
    val recordCount: Int,
    /** Where every entry's local header and data must end: the file's end, or the central directory's start for ZIP64. */
    internal val entryDataEnd: Long,
    private val names: ByteArray,
    private val nameStarts: IntArray,
    internal val flags: IntArray,
    internal val methods: IntArray,
    internal val crcs: LongArray,
    internal val compressedSizes: LongArray,
    internal val uncompressedSizes: LongArray,
    internal val localHeaderOffsets: LongArray,
) {

    /** How many file entries the archive holds. */
    val entryCount get() = flags.size

    /** The name of the entry at [index], decoded as `ZipReader.entryName` says. */
    fun nameOf(index: Int): String = with(ZipReader) {
        names.entryName(nameStarts[index], nameStarts[index + 1] - nameStarts[index], flags[index])
    }

    /**
     * Whether the data of the entry at [index] could be the size it declares: a stored entry is its compressed bytes, and
     * no deflate stream inflates to more than [MAX_DEFLATE_RATIO] times its own length; an entry of another method is
     * never read. One that could not is damaged whatever else it holds, so nothing is set aside for it on the strength of
     * its declaration (a disk reservation, a staged copy): the walk leaves it out as unreadable before asking for its
     * sink, as reading it would find it short.
     */
    fun canProduceDeclaredSize(index: Int): Boolean {
        val compressedSize = compressedSizes[index]
        return when (methods[index]) {
            ZipReader.METHOD_STORED -> uncompressedSizes[index] == compressedSize
            ZipReader.METHOD_DEFLATE -> compressedSize <= Long.MAX_VALUE / MAX_DEFLATE_RATIO && uncompressedSizes[index] <= compressedSize * MAX_DEFLATE_RATIO
            else -> false
        }
    }

    /**
     * Reads the entries in the order the central directory lists them, asking [limitOf] about each, peeking at one it
     * answers [EntryPolicy.Sniff] for, and charging each one read before reading it. Each entry to be read is written into the sink [sinkOf] gives for it once its size has been
     * charged, which ends closed (it was read whole and checked) or abandoned (it was not, and is among the unread ones
     * as [UnreadZipEntry.Reason.UNREADABLE]). A stored entry is copied and a deflated one inflated, through a buffer
     * of their own whatever their size: an entry of any 64-bit size is read, as long as [limitOf] allows it. An entry
     * charged whose data cannot be its declared size ([canProduceDeclaredSize]) is left out as unreadable without a
     * sink, which is why every charge is told to [onCharged], before its sink is asked for where there is one.
     *
     * Only an archive that cannot be walked at all throws a [ZipException]; whatever [sinkOf], a sink, or the source
     * throws other than that is not a damaged entry, and ends the walk.
     *
     * @param maxTotalSize how many bytes the entries read may add up to, charged as declared before each is read.
     * @param limitOf what to do with the entry of that name and declared size.
     * @param sniffedLimitOf how large a sniffed entry starting with that prefix may be, null for one not to be read.
     * @param sinkOf where the entry of that name and declared size goes; a [ZipException] it throws leaves the entry out
     * as unreadable.
     */
    suspend fun walk(
        maxTotalSize: Long = ImportLimits.MAX_IMPORT_SIZE,
        limitOf: (name: String, declaredSize: Long) -> EntryPolicy = { _, _ -> EntryPolicy.Read(Long.MAX_VALUE) },
        sniffedLimitOf: (name: String, prefix: ByteArray) -> Long? = { _, _ -> null },
        onCharged: (name: String, declaredSize: Long) -> Unit = { _, _ -> },
        sinkOf: suspend (name: String, declaredSize: Long) -> EntrySink,
    ): ZipWalkResult = ZipWalk(
        directory = this,
        maxTotalSize = maxTotalSize,
        maxEntrySize = Long.MAX_VALUE,
        limitOf = limitOf,
        sniffedLimitOf = sniffedLimitOf,
        onCharged = onCharged,
        sinkOf = sinkOf,
    ).walk()

    internal companion object {

        /**
         * The most bytes one byte of a deflate stream can inflate to. A literal costs at least one bit for one byte; a
         * match costs at least a length code and a distance code of one bit each (a distance code has at least one
         * symbol, since a match without one cannot be written), and copies at most 258 bytes. So every two bits of the
         * stream give at most 258 bytes, and its 8 × n bits at most 258 × 4 × n; block headers, the end of block, and
         * the first literal only lower that. zlib's best over zeros comes close (about 1030), which a test checks.
         */
        const val MAX_DEFLATE_RATIO = 1032L
    }
}

/**
 * Set in [ZipDirectory.flags] for an entry with a size or offset at its ZIP64 marker whose value could not be had, which
 * is left out as unreadable. Above the 16 bits of the format's own flags.
 */
internal const val FLAG_UNRESOLVED_ZIP64 = 1 shl 16
