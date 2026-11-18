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
 * Where the central directory lies and how many records it declares, as the end records give them, and where every
 * entry's local header and data must end: the end of the file for an archive without ZIP64, and the start of the
 * central directory for one with.
 */
internal class CentralDirectoryBounds(val entryCount: Long, val size: Long, val offset: Long, val entryDataEnd: Long)

/**
 * The bounds the end of central directory record at [endRecord] in [tail] (the last bytes of [source], from [tailStart])
 * gives, through the ZIP64 end of central directory record where it has one.
 *
 * ZIP64 is read where the end record has a field at its marker or a ZIP64 locator sits in the 20 bytes before it. The
 * ZIP64 record's values win over the end record's, whether or not those are at their markers, since real writers leave
 * stale ones there (Info-ZIP and `ditto` set only the fields that overflowed). An end record that needs a ZIP64 record
 * and has none is damaged (Info-ZIP's `zip -fz` writing to a pipe makes one), as is a ZIP64 record that cannot be read.
 * One that needs none and has a locator whose record cannot be read is read without ZIP64, so no archive that reads
 * without it is refused for a broken ZIP64 record. The disk fields of the locator and the ZIP64 record are never
 * checked, since Windows Explorer and OneDrive write wrong ones in ordinary archives of one file; what refuses a split
 * archive is the end record's own disk numbers, checked for a ZIP64 archive only.
 *
 * @throws WrappedArchiveException for a file longer than `0xFFFFFFFF` bytes with no locator.
 */
internal suspend fun centralDirectoryBounds(source: ZipSource, tail: ByteArray, tailStart: Long, endRecord: Int): CentralDirectoryBounds {
    val entryCount = tail.u16(endRecord + 10)
    val size = tail.u32(endRecord + 12)
    val offset = tail.u32(endRecord + 16)
    val locatorPosition = tailStart + endRecord - LOCATOR_SIZE
    val locator = when {
        endRecord >= LOCATOR_SIZE -> tail.copyOfRange(endRecord - LOCATOR_SIZE, endRecord)
        locatorPosition >= 0 -> ByteArray(LOCATOR_SIZE).also { source.read(locatorPosition, it, 0, LOCATOR_SIZE) }
        else -> null
    }?.takeIf { it.u32(0) == LOCATOR_SIGNATURE }
    if (locator == null && source.size > ZIP64_MARKER) {
        throw WrappedArchiveException(source.size)
    }
    val zip64 = suspend {
        if (tail.u16(endRecord + 4) != 0 || tail.u16(endRecord + 6) != 0) {
            throw ZipException("Split archives are not supported.")
        }
        zip64Bounds(source, requireNotNull(locator), locatorPosition)
    }
    return when {
        entryCount == 0xFFFF || size == ZIP64_MARKER || offset == ZIP64_MARKER -> {
            if (locator == null) {
                throw ZipException("The end of central directory record needs a ZIP64 record, and the archive has none.")
            }
            zip64()
        }

        locator != null -> try {
            zip64()
        } catch (_: ZipException) {
            CentralDirectoryBounds(entryCount.toLong(), size, offset, entryDataEnd = source.size)
        }

        else -> CentralDirectoryBounds(entryCount.toLong(), size, offset, entryDataEnd = source.size)
    }
}

/**
 * The ZIP64 end of central directory record [locator] (at [locatorPosition]) points at: its fixed fields and its
 * declared size before the locator, the central directory before it, every 64-bit value a [Long] that is not negative.
 * The extensible data sector after the fixed fields is skipped, never read.
 */
private suspend fun zip64Bounds(source: ZipSource, locator: ByteArray, locatorPosition: Long): CentralDirectoryBounds {
    val recordOffset = locator.u64(8)
    if (checkedSum(recordOffset, ZIP64_RECORD_SIZE.toLong()) > locatorPosition) {
        throw ZipException("The ZIP64 end of central directory record at $recordOffset does not lie before its locator.")
    }
    val record = ByteArray(ZIP64_RECORD_SIZE)
    source.read(recordOffset, record, 0, ZIP64_RECORD_SIZE)
    if (record.u32(0) != ZIP64_RECORD_SIGNATURE) {
        throw ZipException("Missing ZIP64 end of central directory record at offset $recordOffset.")
    }
    // The declared size counts what follows its own field: the fixed fields and the extensible data sector.
    val declaredSize = record.u64(4)
    if (declaredSize < ZIP64_RECORD_SIZE - 12 || checkedSum(recordOffset + 12, declaredSize) > locatorPosition) {
        throw ZipException("The ZIP64 end of central directory record declares $declaredSize bytes, past its locator.")
    }
    val entryCount = record.u64(32)
    val size = record.u64(40)
    val offset = record.u64(48)
    if (entryCount < 0) {
        throw ZipException("The ZIP64 end of central directory record declares $entryCount entries.")
    }
    if (checkedSum(offset, size) > recordOffset) {
        throw ZipException("The central directory reaches past the ZIP64 end of central directory record at $recordOffset.")
    }
    return CentralDirectoryBounds(entryCount, size, offset, entryDataEnd = offset)
}

private const val LOCATOR_SIZE = 20
private const val LOCATOR_SIGNATURE = 0x07064B50L
private const val ZIP64_RECORD_SIGNATURE = 0x06064B50L

/** The signature, the size field, and the fixed fields after it. */
private const val ZIP64_RECORD_SIZE = 56
