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

import com.pandulapeter.campfire.data.model.domain.ImportLimits

/**
 * Reads zip archives with STORED and DEFLATE entries, following the PKWARE APPNOTE. A ZIP64 archive is rejected with a
 * [ZipException]; a ZIP64, encrypted or otherwise compressed entry is left out and reported; the app only ever deals
 * with small archives of text files.
 */
internal object ZipReader {

    /**
     * The file entries of [archive], directories skipped. Names are returned as stored (forward slashes, possibly with
     * sub-directories), decoded as UTF-8.
     *
     * Only an archive that cannot be walked at all throws. An entry that cannot be read - or that the caller does not
     * want, or that is too large - costs nothing but its name: the songs next to a recording, a PDF or a damaged
     * `.DS_Store` are still perfectly good.
     *
     * @param maxTotalSize how many bytes the entries read may add up to once inflated. Sizes are checked as the
     * central directory declares them, before an entry is read; the inflater holds an entry to what it declared.
     * @param limitOf how large the entry of that name may be, null for one that is not to be read at all.
     */
    fun read(
        archive: ByteArray,
        maxTotalSize: Long = ImportLimits.MAX_IMPORT_SIZE,
        limitOf: (name: String) -> Long? = { Long.MAX_VALUE },
    ): ZipContent {
        val endOfCentralDirectory = findEndOfCentralDirectory(archive)
        val totalEntries = archive.u16(endOfCentralDirectory + 10)
        val centralDirectorySize = archive.u32(endOfCentralDirectory + 12)
        val centralDirectoryOffset = archive.u32(endOfCentralDirectory + 16)
        if (totalEntries == 0xFFFF || centralDirectorySize == 0xFFFFFFFFL || centralDirectoryOffset == 0xFFFFFFFFL) {
            throw ZipException("ZIP64 archives are not supported.")
        }
        if (centralDirectoryOffset + centralDirectorySize > archive.size) {
            throw ZipException("The central directory reaches past the end of the ${archive.size} byte archive.")
        }
        val entries = mutableListOf<ZipEntry>()
        val unread = mutableListOf<UnreadZipEntry>()
        var totalSize = 0L
        var position = centralDirectoryOffset.toInt()
        repeat(totalEntries) {
            if (archive.u32(position) != CENTRAL_DIRECTORY_SIGNATURE) {
                throw ZipException("Missing central directory header at offset $position.")
            }
            val flags = archive.u16(position + 8)
            val method = archive.u16(position + 10)
            val crc = archive.u32(position + 16)
            val compressedSize = archive.u32(position + 20)
            val uncompressedSize = archive.u32(position + 24)
            val nameLength = archive.u16(position + 28)
            val extraLength = archive.u16(position + 30)
            val commentLength = archive.u16(position + 32)
            val localHeaderOffset = archive.u32(position + 42)
            val name = archive.utf8(position + 46, nameLength)
            if (!name.endsWith("/")) {
                val limit = limitOf(name)
                val isUnsupported = flags and 0x0001 != 0 ||
                    compressedSize == 0xFFFFFFFFL || uncompressedSize == 0xFFFFFFFFL || localHeaderOffset == 0xFFFFFFFFL
                when {
                    limit == null -> unread += UnreadZipEntry(name, UnreadZipEntry.Reason.NOT_WANTED)
                    isUnsupported -> unread += UnreadZipEntry(name, UnreadZipEntry.Reason.UNREADABLE)
                    uncompressedSize > limit || totalSize + uncompressedSize > maxTotalSize ->
                        unread += UnreadZipEntry(name, UnreadZipEntry.Reason.TOO_LARGE)

                    else -> try {
                        entries += ZipEntry(
                            name = name,
                            bytes = readData(
                                archive = archive,
                                name = name,
                                method = method,
                                crc = crc,
                                compressedSize = compressedSize.toInt(),
                                uncompressedSize = uncompressedSize.toInt(),
                                localHeaderOffset = localHeaderOffset.toInt(),
                            ),
                        )
                        totalSize += uncompressedSize
                    } catch (exception: ZipException) {
                        println("Could not read \"$name\": ${exception.message}")
                        unread += UnreadZipEntry(name, UnreadZipEntry.Reason.UNREADABLE)
                    }
                }
            }
            position += 46 + nameLength + extraLength + commentLength
        }
        return ZipContent(entries = entries, unread = unread)
    }

    private fun readData(
        archive: ByteArray,
        name: String,
        method: Int,
        crc: Long,
        compressedSize: Int,
        uncompressedSize: Int,
        localHeaderOffset: Int,
    ): ByteArray {
        if (archive.u32(localHeaderOffset) != LOCAL_HEADER_SIGNATURE) {
            throw ZipException("Missing local header for \"$name\" at offset $localHeaderOffset.")
        }
        // The local header's name and extra fields may differ in length from the central directory ones, but the sizes
        // there are zero whenever a data descriptor follows the data, so the central directory stays the source of truth.
        val dataOffset = localHeaderOffset + 30 + archive.u16(localHeaderOffset + 26) + archive.u16(localHeaderOffset + 28)
        if (dataOffset < 0 || compressedSize < 0 || uncompressedSize < 0 || dataOffset.toLong() + compressedSize > archive.size) {
            throw ZipException("The data of \"$name\" reaches past the end of the ${archive.size} byte archive.")
        }
        val bytes = when (method) {
            METHOD_STORED -> {
                if (compressedSize != uncompressedSize) {
                    throw ZipException("Stored entry \"$name\" declares different compressed and uncompressed sizes.")
                }
                archive.copyOfRange(dataOffset, dataOffset + compressedSize)
            }

            METHOD_DEFLATE -> Inflater.inflate(archive, dataOffset, compressedSize, uncompressedSize)

            else -> throw ZipException("Unsupported compression method $method for \"$name\".")
        }
        if (Crc32.of(bytes) != crc) {
            throw ZipException("Checksum mismatch for \"$name\".")
        }
        return bytes
    }

    private fun findEndOfCentralDirectory(archive: ByteArray): Int {
        if (archive.size < 22) {
            throw ZipException("An archive of ${archive.size} bytes is too short to be a zip file.")
        }
        val lowestPossible = maxOf(0, archive.size - 22 - MAX_COMMENT_LENGTH)
        for (offset in archive.size - 22 downTo lowestPossible) {
            if (archive.u32(offset) == END_OF_CENTRAL_DIRECTORY_SIGNATURE) {
                return offset
            }
        }
        throw ZipException("Not a zip file: no end of central directory record found.")
    }

    private fun ByteArray.utf8(offset: Int, length: Int): String {
        if (length < 0 || offset < 0 || offset + length > size) {
            throw ZipException("Truncated archive: a $length byte name at offset $offset is outside the $size byte input.")
        }
        return copyOfRange(offset, offset + length).decodeToString()
    }

    private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054B50L
    private const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014B50L
    private const val LOCAL_HEADER_SIGNATURE = 0x04034B50L
    private const val METHOD_STORED = 0
    private const val METHOD_DEFLATE = 8
    private const val MAX_COMMENT_LENGTH = 65535
}
