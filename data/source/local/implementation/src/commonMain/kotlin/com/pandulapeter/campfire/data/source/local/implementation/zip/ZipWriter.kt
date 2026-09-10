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

/**
 * Writes zip archives with one STORED (uncompressed) entry per input. The files the app exports are tiny ChordPro and
 * JSON documents, so leaving out DEFLATE compression costs nothing and keeps the writer trivial.
 */
internal object ZipWriter {

    fun write(entries: List<ZipEntry>): ByteArray {
        val builder = ByteArrayBuilder(entries.sumOf { it.bytes.size + it.name.length * 2 + 128 })
        val offsets = IntArray(entries.size)
        val names = entries.map { it.name.encodeToByteArray() }
        val checksums = LongArray(entries.size) { Crc32.of(entries[it].bytes) }
        entries.forEachIndexed { index, entry ->
            offsets[index] = builder.size
            builder.u32(LOCAL_HEADER_SIGNATURE)
            builder.u16(VERSION)
            builder.u16(FLAG_UTF8_NAMES)
            builder.u16(METHOD_STORED)
            builder.u16(0) // Modification time.
            builder.u16(0) // Modification date.
            builder.u32(checksums[index])
            builder.u32(entry.bytes.size.toLong())
            builder.u32(entry.bytes.size.toLong())
            builder.u16(names[index].size)
            builder.u16(0) // Extra field length.
            builder.bytes(names[index])
            builder.bytes(entry.bytes)
        }
        val centralDirectoryOffset = builder.size
        entries.forEachIndexed { index, entry ->
            builder.u32(CENTRAL_DIRECTORY_SIGNATURE)
            builder.u16(VERSION) // Version made by.
            builder.u16(VERSION) // Version needed to extract.
            builder.u16(FLAG_UTF8_NAMES)
            builder.u16(METHOD_STORED)
            builder.u16(0) // Modification time.
            builder.u16(0) // Modification date.
            builder.u32(checksums[index])
            builder.u32(entry.bytes.size.toLong())
            builder.u32(entry.bytes.size.toLong())
            builder.u16(names[index].size)
            builder.u16(0) // Extra field length.
            builder.u16(0) // Comment length.
            builder.u16(0) // Disk number.
            builder.u16(0) // Internal attributes.
            builder.u32(0) // External attributes.
            builder.u32(offsets[index].toLong())
            builder.bytes(names[index])
        }
        val centralDirectorySize = builder.size - centralDirectoryOffset
        builder.u32(END_OF_CENTRAL_DIRECTORY_SIGNATURE)
        builder.u16(0) // Disk number.
        builder.u16(0) // Disk where the central directory starts.
        builder.u16(entries.size)
        builder.u16(entries.size)
        builder.u32(centralDirectorySize.toLong())
        builder.u32(centralDirectoryOffset.toLong())
        builder.u16(0) // Comment length.
        return builder.build()
    }

    private const val LOCAL_HEADER_SIGNATURE = 0x04034B50L
    private const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014B50L
    private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054B50L
    private const val VERSION = 20
    private const val FLAG_UTF8_NAMES = 0x0800
    private const val METHOD_STORED = 0
}
