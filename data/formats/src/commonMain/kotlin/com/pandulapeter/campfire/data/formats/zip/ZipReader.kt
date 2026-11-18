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
 * Reads zip archives with STORED and DEFLATE entries, ZIP64 included (archives past 4 GiB or 65,535 entries, and entries
 * past 4 GiB), following the PKWARE APPNOTE; an encrypted or otherwise compressed entry is left out and reported.
 *
 * [open] and [ZipDirectory.walk] of a [ZipSource] never hold an archive or an entry whole unless an [EntrySink] does;
 * [read] holds every entry it reads in memory.
 */
object ZipReader {

    /**
     * Reads the central directory of [source], ZIP64 included, refusing with a [ZipException] an archive that is no zip
     * or whose central directory is damaged, a damaged or split ZIP64 archive, a directory declared larger than
     * [maxCentralDirectorySize] or whose records add up to more, and more than [maxEntries] declared entries; the last
     * two are checked before anything is allocated for them, against the 64-bit values where the archive has them.
     *
     * @throws WrappedArchiveException (a [ZipException]) for an archive past 4 GiB written without ZIP64, which a caller
     * tells apart from a damaged one.
     */
    suspend fun open(
        source: ZipSource,
        maxCentralDirectorySize: Long = ImportLimits.MAX_CENTRAL_DIRECTORY_SIZE,
        maxEntries: Int = ImportLimits.MAX_ARCHIVE_ENTRIES,
    ): ZipDirectory = readZipDirectory(source, maxCentralDirectorySize, maxEntries)

    /**
     * The file entries of the archive [source] reads, directories skipped, each read into memory: [open], then
     * [ZipDirectory.walk] with every entry held whole, which [maxTotalSize] bounds, and none larger than
     * [Inflater.MAX_DECLARED_ENTRY_SIZE] ([ByteArrayEntrySink]). An entry of 2 GiB or more is left out however it is to be
     * read. Names are returned as stored, sub-directories included, decoded as UTF-8 or, where they are not UTF-8 and do
     * not claim to be, as code page 437 (see [entryName]).
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
    suspend fun read(
        source: ZipSource,
        maxTotalSize: Long = ImportLimits.MAX_IMPORT_SIZE,
        limitOf: (name: String, declaredSize: Long) -> EntryPolicy = { _, _ -> EntryPolicy.Read(Long.MAX_VALUE) },
        sniffedLimitOf: (name: String, prefix: ByteArray) -> Long? = { _, _ -> null },
    ): ZipContent {
        val sinks = mutableListOf<Pair<String, ByteArrayEntrySink>>()
        val walk = ZipWalk(
            directory = open(source),
            maxTotalSize = maxTotalSize,
            maxEntrySize = Int.MAX_VALUE.toLong(),
            limitOf = limitOf,
            sniffedLimitOf = sniffedLimitOf,
            sinkOf = { name, declaredSize -> ByteArrayEntrySink(declaredSize).also { sinks += name to it } },
        ).walk()
        val entries = sinks.filter { (_, sink) -> sink.isClosed }.map { (name, sink) -> ZipEntry(name, sink.bytes()) }
        return ZipContent(entries = entries, unread = walk.unread, chargedSize = walk.chargedSize)
    }

    /**
     * The name of an entry. UTF-8 where the entry says so (bit 11) and wherever the bytes are valid UTF-8 anyway, since
     * macOS writes UTF-8 names without setting the bit. Anything else is code page 437, which is what the format
     * specifies for a name without the bit and what the DOS-era Windows tools wrote: the accented letters of Western
     * and most Central European names come out right, instead of as a row of replacement characters.
     */
    internal fun ByteArray.entryName(offset: Int, length: Int, flags: Int): String {
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

    internal const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054B50L
    internal const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014B50L
    internal const val LOCAL_HEADER_SIGNATURE = 0x04034B50L
    internal const val METHOD_STORED = 0
    internal const val METHOD_DEFLATE = 8
    internal const val MAX_COMMENT_LENGTH = 65535
    private const val FLAG_UTF8_NAME = 0x0800

    /** 0x80-0xFF in IBM code page 437, the character set a zip entry name is in when it does not say otherwise. */
    private const val CP437_CHARACTERS =
        "ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒ" +
            "áíóúñÑªº¿⌐¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐" +
            "└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀" +
            "αßΓπΣσµτΦΘΩδ∞φε∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u00A0"
}
