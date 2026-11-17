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

    /** The name of the entry at [index], decoded as [ZipReader.read] decodes it. */
    fun nameOf(index: Int): String = with(ZipReader) {
        names.entryName(nameStarts[index], nameStarts[index + 1] - nameStarts[index], flags[index])
    }

    /**
     * Reads the entries in the order the central directory lists them, as [ZipReader.read] reads an archive in memory:
     * the same [EntryPolicy] asked of the same entries, the same peeks, the same charges, the same entries left out for
     * the same reasons. Each entry to be read is written into the sink [sinkOf] gives for it once its size has been
     * charged, which ends closed (it was read whole and checked) or abandoned (it was not, and is among the unread ones
     * as [UnreadZipEntry.Reason.UNREADABLE]). A stored entry is copied and a deflated one inflated, through a buffer
     * of their own whatever their size.
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
        sinkOf: suspend (name: String, declaredSize: Long) -> EntrySink,
    ): ZipWalkResult = ZipWalk(this, maxTotalSize, limitOf, sniffedLimitOf, sinkOf).walk()
}
