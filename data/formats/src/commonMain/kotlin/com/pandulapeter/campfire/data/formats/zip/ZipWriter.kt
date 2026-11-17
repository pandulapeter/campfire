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

import kotlinx.coroutines.yield

/**
 * Writes zip archives with one entry per input, each DEFLATEd where that makes it smaller and STORED where it does not
 * (a tiny file, or one already compressed, such as an image): the writer decides by trying, not by the name. Every
 * Campfire version reads both, and the same entries always make the same bytes, which shared copies rely on. An
 * archive is smaller than what an import unpacks it to, so whether an import takes it is a question for the entries'
 * own sizes, not the archive's.
 *
 * ZIP64 is written only where an archive passes what the 32-bit format holds (an entry starting at or past
 * `0xFFFFFFFF`, a central directory reaching that far, 65,535 entries or more), so every archive within those limits
 * comes out byte for byte as one written before ZIP64 was.
 */
object ZipWriter {

    /** The archive of [entries] in memory, for the tests and the small cases. Yields as the streaming [write] does. */
    suspend fun write(entries: List<ZipEntry>, modifiedAt: DosTimestamp = DosTimestamp.EARLIEST): ByteArray {
        val sink = BufferZipSink(entries.sumOf { it.bytes.size + it.name.length * 2 + 128 })
        write(entries = entries.map { entry -> { entry } }, sink = sink, modifiedAt = modifiedAt)
        return sink.build()
    }

    /**
     * Writes the archive of [entries] to [sink] as it goes: each entry is read (called once, when it is its turn),
     * deflated, and written whole before the next is read, its CRC and sizes known before its header, so no data
     * descriptor is needed and what stays in memory is one entry and, for the central directory, each entry's name,
     * offset, CRC, and sizes; the directory itself is written as it is built, 64 KiB at a time. One past 4 GiB or of 65,535 entries or more is written as ZIP64. Yields (and so checks for
     * cancellation) between entries and every 256 KB of deflating.
     *
     * An entry read as null is left out, nothing of it written: a file that turned out to be missing or unreadable. Its
     * name comes with its bytes, since a file read by one name may be stored under another spelling of it.
     */
    suspend fun write(entries: List<suspend () -> ZipEntry?>, sink: ZipSink, modifiedAt: DosTimestamp = DosTimestamp.EARLIEST): ZipSummary =
        write(entries = entries, sink = sink, modifiedAt = modifiedAt, offsetLimit = OFFSET_MARKER, entryLimit = ENTRY_COUNT_MARKER)

    /**
     * [write] with the limits from which ZIP64 is written lowered by the tests, so that small archives can be read by
     * other tools as ZIP64: an offset or size of [offsetLimit] or more, and an entry count of [entryLimit] or more, is
     * put at its marker and given its ZIP64 value. Production passes the format's own limits.
     */
    internal suspend fun write(
        entries: List<suspend () -> ZipEntry?>,
        sink: ZipSink,
        modifiedAt: DosTimestamp,
        offsetLimit: Long,
        entryLimit: Int,
    ): ZipSummary {
        val names = ArrayList<ByteArray>(entries.size)
        val offsets = LongArray(entries.size)
        val checksums = LongArray(entries.size)
        val methods = IntArray(entries.size)
        val compressedSizes = LongArray(entries.size)
        val sizes = LongArray(entries.size)
        val deflater = Deflater()
        var deflated = ByteArrayBuilder()
        val header = ByteArrayBuilder()
        var count = 0
        entries.forEach { read ->
            yield()
            val entry = read() ?: return@forEach
            val bytes = entry.bytes
            val name = entry.name.encodeToByteArray()
            if (name.size > MAX_NAME_SIZE) throw ZipException("The name of entry \"${entry.name.take(64)}…\" is too long for an archive.")
            val index = count++
            names += name
            checksums[index] = Crc32.of(bytes)
            val isLarge = bytes.size > LARGE_ENTRY_SIZE
            deflated.clear()
            val isDeflated = if (isLarge) {
                deflateLarge(deflater, bytes)?.also { deflated = it } != null
            } else {
                deflateSmall(deflater, bytes, deflated)
            }
            methods[index] = if (isDeflated) METHOD_DEFLATED else METHOD_STORED
            compressedSizes[index] = (if (isDeflated) deflated.size else bytes.size).toLong()
            sizes[index] = bytes.size.toLong()
            offsets[index] = sink.position
            header.clear()
            header.u32(LOCAL_HEADER_SIGNATURE)
            header.u16(VERSION)
            header.u16(FLAG_UTF8_NAMES)
            header.u16(methods[index])
            header.u16(modifiedAt.time)
            header.u16(modifiedAt.date)
            header.u32(checksums[index])
            header.u32(compressedSizes[index])
            header.u32(sizes[index])
            header.u16(names[index].size)
            header.u16(0) // Extra field length.
            header.bytes(names[index])
            header.writeTo(sink)
            if (isDeflated) deflated.writeTo(sink) else sink.writeInSlices(bytes, bytes.size)
            // Let go of a large entry's buffer at once, rather than keep its peak until the archive ends.
            if (isLarge) deflated = ByteArrayBuilder()
        }
        val centralDirectoryOffset = sink.position
        header.clear()
        repeat(count) { index ->
            // Sizes never need ZIP64: an entry is one ByteArray, and its local header carries its sizes, so no data
            // descriptor or local extra field is needed either.
            val isOffsetZip64 = offsets[index] >= offsetLimit
            header.u32(CENTRAL_DIRECTORY_SIGNATURE)
            header.u16(VERSION) // Version made by.
            header.u16(if (isOffsetZip64) VERSION_ZIP64 else VERSION) // Version needed to extract.
            header.u16(FLAG_UTF8_NAMES)
            header.u16(methods[index])
            header.u16(modifiedAt.time)
            header.u16(modifiedAt.date)
            header.u32(checksums[index])
            header.u32(compressedSizes[index])
            header.u32(sizes[index])
            header.u16(names[index].size)
            header.u16(if (isOffsetZip64) ZIP64_EXTRA_SIZE else 0) // Extra field length.
            header.u16(0) // Comment length.
            header.u16(0) // Disk number.
            header.u16(0) // Internal attributes.
            header.u32(0) // External attributes.
            header.u32(if (isOffsetZip64) OFFSET_MARKER else offsets[index])
            header.bytes(names[index])
            if (isOffsetZip64) {
                header.u16(ZIP64_EXTRA_TAG)
                header.u16(ZIP64_EXTRA_SIZE - 4)
                header.u64(offsets[index])
            }
            // Written as it fills rather than held whole: the directory of as many entries as an import takes is tens of
            // megabytes, which a phone's heap would hold about twice over while the buffer doubles.
            if (header.size >= CENTRAL_DIRECTORY_SLICE_SIZE) {
                header.writeTo(sink)
                header.clear()
            }
        }
        val centralDirectorySize = sink.position - centralDirectoryOffset + header.size
        val centralDirectoryEnd = centralDirectoryOffset + centralDirectorySize
        // An archive longer than 0xFFFFFFFF bytes without a locator reads as one some tool wrapped past 4 GiB, so an end
        // record alone crossing the limit takes the ZIP64 records too.
        if (count >= entryLimit || centralDirectoryOffset >= offsetLimit || centralDirectoryEnd + END_OF_CENTRAL_DIRECTORY_SIZE > offsetLimit) {
            header.u32(ZIP64_END_OF_CENTRAL_DIRECTORY_SIGNATURE)
            header.u64(ZIP64_END_OF_CENTRAL_DIRECTORY_SIZE - 12L) // Size of the rest of the record.
            header.u16(VERSION_ZIP64) // Version made by.
            header.u16(VERSION_ZIP64) // Version needed to extract.
            header.u32(0) // Disk number.
            header.u32(0) // Disk where the central directory starts.
            header.u64(count.toLong())
            header.u64(count.toLong())
            header.u64(centralDirectorySize)
            header.u64(centralDirectoryOffset)
            header.u32(ZIP64_LOCATOR_SIGNATURE)
            header.u32(0) // Disk where the ZIP64 record is.
            header.u64(centralDirectoryEnd)
            header.u32(1) // Number of disks.
        }
        header.u32(END_OF_CENTRAL_DIRECTORY_SIGNATURE)
        header.u16(0) // Disk number.
        header.u16(0) // Disk where the central directory starts.
        repeat(2) { header.u16(if (count >= entryLimit) ENTRY_COUNT_MARKER else count) }
        header.u32(if (centralDirectorySize >= offsetLimit) OFFSET_MARKER else centralDirectorySize)
        header.u32(if (centralDirectoryOffset >= offsetLimit) OFFSET_MARKER else centralDirectoryOffset)
        header.u16(0) // Comment length.
        header.writeTo(sink)
        return ZipSummary(entryCount = count, unpackedSize = sizes.sum())
    }

    /** Whether [bytes] deflated into [output] is smaller than they are, the whole entry deflated as it always was. */
    private suspend fun deflateSmall(deflater: Deflater, bytes: ByteArray, output: ByteArrayBuilder): Boolean {
        deflater.deflate(bytes, output::u8)
        return output.size < bytes.size
    }

    /**
     * [deflateSmall] for an entry over [LARGE_ENTRY_SIZE], which never deflates into more memory than the entry: its
     * first [TRIAL_SIZE] bytes are deflated first, and an entry whose head does not shrink by [MIN_TRIAL_SAVING] percent
     * (a recording, a photo, a PDF of scans) is stored without deflating the rest. One that does is deflated into a buffer
     * sized to the entry, given up on as soon as it would be no smaller than stored. Null where the entry is stored.
     */
    private suspend fun deflateLarge(deflater: Deflater, bytes: ByteArray): ByteArrayBuilder? {
        var trialSize = 0
        deflater.deflate(bytes.copyOfRange(0, TRIAL_SIZE)) { trialSize++ }
        if (trialSize > TRIAL_SIZE - TRIAL_SIZE * MIN_TRIAL_SAVING / 100) return null
        val output = ByteArrayBuilder(bytes.size)
        return try {
            deflater.deflate(bytes) { value ->
                if (output.size + 1 >= bytes.size) throw NoSmallerThanStored
                output.u8(value)
            }
            output
        } catch (_: NoSmallerThanStored) {
            null
        }
    }

    /** Thrown out of a deflate that has reached its entry's own size, where storing it is no larger. */
    private object NoSmallerThanStored : RuntimeException()

    private const val LOCAL_HEADER_SIGNATURE = 0x04034B50L
    private const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014B50L
    private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054B50L
    private const val ZIP64_END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06064B50L
    private const val ZIP64_LOCATOR_SIGNATURE = 0x07064B50L
    private const val END_OF_CENTRAL_DIRECTORY_SIZE = 22
    private const val ZIP64_END_OF_CENTRAL_DIRECTORY_SIZE = 56
    private const val ZIP64_EXTRA_TAG = 0x0001
    private const val ZIP64_EXTRA_SIZE = 12
    private const val VERSION = 20
    private const val VERSION_ZIP64 = 45
    private const val FLAG_UTF8_NAMES = 0x0800
    private const val METHOD_STORED = 0
    private const val METHOD_DEFLATED = 8
    private const val ENTRY_COUNT_MARKER = 0xFFFF
    private const val OFFSET_MARKER = 0xFFFFFFFFL
    private const val MAX_NAME_SIZE = 0xFFFF
    private const val LARGE_ENTRY_SIZE = 1024 * 1024
    private const val CENTRAL_DIRECTORY_SLICE_SIZE = 64 * 1024
    private const val TRIAL_SIZE = 64 * 1024
    private const val MIN_TRIAL_SAVING = 3
}
