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
     * descriptor is needed and what stays in memory is one entry and, for the central directory, each entry's offset,
     * CRC, and sizes. An archive past 4 GiB is refused rather than written as ZIP64, part of it already in [sink]. Yields
     * (and so checks for cancellation) between entries and every 256 KB of deflating.
     *
     * An entry read as null is left out, nothing of it written: a file that turned out to be missing or unreadable. Its
     * name comes with its bytes, since a file read by one name may be stored under another spelling of it.
     */
    suspend fun write(entries: List<suspend () -> ZipEntry?>, sink: ZipSink, modifiedAt: DosTimestamp = DosTimestamp.EARLIEST): ZipSummary {
        if (entries.size >= ZIP64_MARKER) throw ZipException("An archive cannot hold ${entries.size} entries, the most is ${ZIP64_MARKER - 1}.")
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
            offsets[index] = sink.position.checkedOffset()
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
        val centralDirectoryOffset = sink.position.checkedOffset()
        header.clear()
        repeat(count) { index ->
            header.u32(CENTRAL_DIRECTORY_SIGNATURE)
            header.u16(VERSION) // Version made by.
            header.u16(VERSION) // Version needed to extract.
            header.u16(FLAG_UTF8_NAMES)
            header.u16(methods[index])
            header.u16(modifiedAt.time)
            header.u16(modifiedAt.date)
            header.u32(checksums[index])
            header.u32(compressedSizes[index])
            header.u32(sizes[index])
            header.u16(names[index].size)
            header.u16(0) // Extra field length.
            header.u16(0) // Comment length.
            header.u16(0) // Disk number.
            header.u16(0) // Internal attributes.
            header.u32(0) // External attributes.
            header.u32(offsets[index])
            header.bytes(names[index])
        }
        val centralDirectorySize = header.size.toLong()
        (centralDirectoryOffset + centralDirectorySize).checkedOffset()
        header.u32(END_OF_CENTRAL_DIRECTORY_SIGNATURE)
        header.u16(0) // Disk number.
        header.u16(0) // Disk where the central directory starts.
        header.u16(count)
        header.u16(count)
        header.u32(centralDirectorySize)
        header.u32(centralDirectoryOffset)
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

    /** 0xFFFFFFFF itself is ZIP64's marker in a 32-bit field, so the largest offset a plain archive can hold is one less. */
    private fun Long.checkedOffset() = also { if (it >= MAX_OFFSET) throw ZipException("An archive cannot be larger than 4 GiB.") }

    private const val LOCAL_HEADER_SIGNATURE = 0x04034B50L
    private const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014B50L
    private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054B50L
    private const val VERSION = 20
    private const val FLAG_UTF8_NAMES = 0x0800
    private const val METHOD_STORED = 0
    private const val METHOD_DEFLATED = 8
    private const val ZIP64_MARKER = 0xFFFF
    private const val MAX_NAME_SIZE = 0xFFFF
    private const val MAX_OFFSET = 0xFFFFFFFFL
    private const val LARGE_ENTRY_SIZE = 1024 * 1024
    private const val TRIAL_SIZE = 64 * 1024
    private const val MIN_TRIAL_SAVING = 3
}
