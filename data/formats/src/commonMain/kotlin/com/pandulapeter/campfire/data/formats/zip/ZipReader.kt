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
 * Reads zip archives with STORED and DEFLATE entries, following the PKWARE APPNOTE. A ZIP64 archive is rejected with a
 * [ZipException]; a ZIP64, encrypted or otherwise compressed entry is left out and reported; the app only ever deals
 * with small archives of text files.
 */
object ZipReader {

    /**
     * The file entries of [archive], directories skipped. Names are returned as stored, sub-directories included,
     * decoded as UTF-8 or, where they are not UTF-8 and do not claim to be, as code page 437 (see [entryName]).
     *
     * Only an archive that cannot be walked at all throws. An entry that cannot be read - or that the caller does not
     * want, or that is too large - costs nothing but its name: the songs next to a recording, a PDF or a damaged
     * `.DS_Store` are still perfectly good.
     *
     * @param maxTotalSize how many bytes the entries read may add up to once inflated. Sizes are checked as the
     * central directory declares them and charged before an entry is read, so a damaged entry costs what it declared;
     * the inflater holds an entry to what it declared.
     * @param limitOf what to do with the entry of that name and declared size: read it within a limit, skip it, or
     * [EntryPolicy.Sniff] it, which hands its first [ImportLimits.SNIFF_LENGTH] bytes to [sniffedLimitOf].
     * @param sniffedLimitOf how large a sniffed entry starting with that prefix may be, null for one that is not to be
     * read after all. A sniffed entry that is encrypted, ZIP64 or cannot be peeked at is not wanted rather than
     * damaged, since nobody asked for it, and one that is not wanted costs nothing of [maxTotalSize].
     */
    fun read(
        archive: ByteArray,
        maxTotalSize: Long = ImportLimits.MAX_IMPORT_SIZE,
        limitOf: (name: String, declaredSize: Long) -> EntryPolicy = { _, _ -> EntryPolicy.Read(Long.MAX_VALUE) },
        sniffedLimitOf: (name: String, prefix: ByteArray) -> Long? = { _, _ -> null },
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
        val walk = Walk(archive = archive, maxTotalSize = maxTotalSize, sniffedLimitOf = sniffedLimitOf)
        var position = centralDirectoryOffset.toInt()
        repeat(totalEntries) {
            if (archive.u32(position) != CENTRAL_DIRECTORY_SIGNATURE) {
                throw ZipException("Missing central directory header at offset $position.")
            }
            val flags = archive.u16(position + 8)
            val nameLength = archive.u16(position + 28)
            val extraLength = archive.u16(position + 30)
            val commentLength = archive.u16(position + 32)
            val name = archive.entryName(position + 46, nameLength, flags)
            // A backslash is a separator too: Windows PowerShell 5.1 writes the platform's own, against the format.
            if (!name.endsWith("/") && !name.endsWith("\\")) {
                val record = Record(
                    name = name,
                    flags = flags,
                    method = archive.u16(position + 10),
                    crc = archive.u32(position + 16),
                    compressedSize = archive.u32(position + 20),
                    uncompressedSize = archive.u32(position + 24),
                    localHeaderOffset = archive.u32(position + 42),
                )
                walk.take(record, limitOf(name, record.uncompressedSize))
            }
            position += 46 + nameLength + extraLength + commentLength
        }
        return ZipContent(entries = walk.entries, unread = walk.unread, chargedSize = walk.chargedSize)
    }

    /** One file entry as the central directory describes it. */
    private class Record(
        val name: String,
        val flags: Int,
        val method: Int,
        val crc: Long,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val localHeaderOffset: Long,
    ) {
        val isUnsupported
            get() = flags and 0x0001 != 0 ||
                compressedSize == 0xFFFFFFFFL || uncompressedSize == 0xFFFFFFFFL || localHeaderOffset == 0xFFFFFFFFL
    }

    /** The entries one [read] has taken so far, the ones it has not, and what they have cost. */
    private class Walk(
        private val archive: ByteArray,
        private val maxTotalSize: Long,
        private val sniffedLimitOf: (name: String, prefix: ByteArray) -> Long?,
    ) {
        val entries = mutableListOf<ZipEntry>()
        val unread = mutableListOf<UnreadZipEntry>()
        var chargedSize = 0L
            private set
        private var compressedBytesRead = 0L

        fun take(record: Record, policy: EntryPolicy) {
            val reason = when (policy) {
                EntryPolicy.Skip -> UnreadZipEntry.Reason.NOT_WANTED
                is EntryPolicy.Read -> when {
                    record.isUnsupported -> UnreadZipEntry.Reason.UNREADABLE
                    isTooLarge(record, policy.limit) -> UnreadZipEntry.Reason.TOO_LARGE
                    isPastArchive(record) -> UnreadZipEntry.Reason.UNREADABLE
                    else -> {
                        compressedBytesRead += record.compressedSize
                        readWhole(record)
                    }
                }

                EntryPolicy.Sniff -> when {
                    record.isUnsupported -> UnreadZipEntry.Reason.NOT_WANTED
                    isPastArchive(record) -> UnreadZipEntry.Reason.UNREADABLE
                    else -> {
                        // Charged once, for the peek and for a read after it alike: every peek takes at most its own
                        // compressed bytes of input and all of them together at most the archive's, whatever the central
                        // directory claims, while charging again for the read would refuse a nested backup that is more
                        // than half of its archive.
                        compressedBytesRead += record.compressedSize
                        sniff(record)
                    }
                }
            }
            if (reason != null) {
                unread += UnreadZipEntry(record.name, reason)
            }
        }

        private fun isTooLarge(record: Record, limit: Long) =
            record.uncompressedSize > limit || chargedSize + record.uncompressedSize > maxTotalSize

        /**
         * The entries of an archive never share their data, so together they cannot take up more of it than there is.
         * A central directory whose entries do is pointing several of them at the same bytes, which only a zip bomb does.
         */
        private fun isPastArchive(record: Record) = compressedBytesRead + record.compressedSize > archive.size

        private fun sniff(record: Record): UnreadZipEntry.Reason? {
            val prefix = try {
                peekData(archive, record)
            } catch (_: ZipException) {
                return UnreadZipEntry.Reason.NOT_WANTED
            }
            val limit = sniffedLimitOf(record.name, prefix) ?: return UnreadZipEntry.Reason.NOT_WANTED
            return if (isTooLarge(record, limit)) UnreadZipEntry.Reason.TOO_LARGE else readWhole(record)
        }

        /** Null once the entry is among [entries]. */
        private fun readWhole(record: Record): UnreadZipEntry.Reason? = try {
            // Charged before it is read: a damaged entry has cost what it declared all the same, and without this one
            // damaged entry could be read as many times as the central directory names it.
            chargedSize += record.uncompressedSize
            entries += ZipEntry(name = record.name, bytes = readData(archive, record))
            null
        } catch (exception: ZipException) {
            println("Could not read \"${record.name}\": ${exception.message}")
            UnreadZipEntry.Reason.UNREADABLE
        }
    }

    /** The first [ImportLimits.SNIFF_LENGTH] bytes of an entry, or all of it where it is shorter; nothing is checked. */
    private fun peekData(archive: ByteArray, record: Record): ByteArray {
        val dataOffset = dataOffsetOf(archive, record)
        val compressedSize = record.compressedSize.toInt()
        return when (record.method) {
            METHOD_STORED -> archive.copyOfRange(dataOffset, dataOffset + minOf(ImportLimits.SNIFF_LENGTH, compressedSize))
            METHOD_DEFLATE -> Inflater.inflate(archive, dataOffset, compressedSize, maxOutput = ImportLimits.SNIFF_LENGTH)
            else -> throw ZipException("Unsupported compression method ${record.method} for \"${record.name}\".")
        }
    }

    private fun readData(archive: ByteArray, record: Record): ByteArray {
        val name = record.name
        val compressedSize = record.compressedSize.toInt()
        val uncompressedSize = record.uncompressedSize.toInt()
        val dataOffset = dataOffsetOf(archive, record)
        val bytes = when (record.method) {
            METHOD_STORED -> {
                if (compressedSize != uncompressedSize) {
                    throw ZipException("Stored entry \"$name\" declares different compressed and uncompressed sizes.")
                }
                archive.copyOfRange(dataOffset, dataOffset + compressedSize)
            }

            METHOD_DEFLATE -> Inflater.inflate(archive, dataOffset, compressedSize, uncompressedSize)

            else -> throw ZipException("Unsupported compression method ${record.method} for \"$name\".")
        }
        if (Crc32.of(bytes) != record.crc) {
            throw ZipException("Checksum mismatch for \"$name\".")
        }
        return bytes
    }

    private fun dataOffsetOf(archive: ByteArray, record: Record): Int {
        val name = record.name
        val localHeaderOffset = record.localHeaderOffset.toInt()
        val compressedSize = record.compressedSize.toInt()
        val uncompressedSize = record.uncompressedSize.toInt()
        if (archive.u32(localHeaderOffset) != LOCAL_HEADER_SIGNATURE) {
            throw ZipException("Missing local header for \"$name\" at offset $localHeaderOffset.")
        }
        // The local header's name and extra fields may differ in length from the central directory ones, but the sizes
        // there are zero whenever a data descriptor follows the data, so the central directory stays the source of truth.
        val dataOffset = localHeaderOffset + 30 + archive.u16(localHeaderOffset + 26) + archive.u16(localHeaderOffset + 28)
        if (dataOffset < 0 || compressedSize < 0 || uncompressedSize < 0 || dataOffset.toLong() + compressedSize > archive.size) {
            throw ZipException("The data of \"$name\" reaches past the end of the ${archive.size} byte archive.")
        }
        return dataOffset
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

    /**
     * The name of an entry. UTF-8 where the entry says so (bit 11) and wherever the bytes are valid UTF-8 anyway, since
     * macOS writes UTF-8 names without setting the bit. Anything else is code page 437, which is what the format
     * specifies for a name without the bit and what the DOS-era Windows tools wrote: the accented letters of Western
     * and most Central European names come out right, instead of as a row of replacement characters.
     */
    private fun ByteArray.entryName(offset: Int, length: Int, flags: Int): String {
        if (length < 0 || offset < 0 || offset + length > size) {
            throw ZipException("Truncated archive: a $length byte name at offset $offset is outside the $size byte input.")
        }
        val bytes = copyOfRange(offset, offset + length)
        if (flags and FLAG_UTF8_NAME != 0) return bytes.decodeToString()
        return try {
            bytes.decodeToString(throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            buildString(length) {
                for (byte in bytes) {
                    val value = byte.toInt() and 0xFF
                    append(if (value < 0x80) value.toChar() else CP437_CHARACTERS[value - 0x80])
                }
            }
        }
    }

    private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054B50L
    private const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014B50L
    private const val LOCAL_HEADER_SIGNATURE = 0x04034B50L
    private const val METHOD_STORED = 0
    private const val METHOD_DEFLATE = 8
    private const val MAX_COMMENT_LENGTH = 65535
    private const val FLAG_UTF8_NAME = 0x0800

    /** 0x80-0xFF in IBM code page 437, the character set a zip entry name is in when it does not say otherwise. */
    private const val CP437_CHARACTERS =
        "ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒ" +
            "áíóúñÑªº¿⌐¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐" +
            "└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀" +
            "αßΓπΣσµτΦΘΩδ∞φε∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u00A0"
}
