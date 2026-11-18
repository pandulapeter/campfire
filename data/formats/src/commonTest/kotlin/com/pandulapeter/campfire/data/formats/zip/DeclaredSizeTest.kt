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

import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * An entry whose data cannot be the size its central directory declares: left out as unreadable by [ZipReader.read] and
 * by a walk alike, charged as declared, and the walk asks no sink for it, so nothing is set aside on the strength of the lie.
 */
internal class DeclaredSizeTest {

    @Test
    fun `entries declaring more than their data can hold are unreadable and get no sink`() = runTest {
        val text = "{title: A}\n".repeat(200).encodeToByteArray()
        // Stored, since random bytes do not deflate smaller.
        val noise = Random(1).nextBytes(300)
        val archive = ZipWriter.write(listOf(ZipEntry("deflated.cho", text), ZipEntry("stored.bin", noise), ZipEntry("honest.cho", text)))
        val deflatedSize = archive.compressedSizeOf("deflated.cho")
        archive.declareUncompressedSize("deflated.cho", deflatedSize * ZipDirectory.MAX_DEFLATE_RATIO + 1)
        archive.declareUncompressedSize("stored.bin", noise.size + 1L)

        val inMemory = readArchive(archive)
        val charged = mutableListOf<String>()
        val walk = ZipReader.open(ByteArrayZipSource(archive)).walk(onCharged = { name, _ -> charged += name }) { name, declaredSize ->
            if (name != "honest.cho") fail("A sink was asked for $name, which cannot be its $declaredSize bytes")
            RecordingEntrySink()
        }

        val unreadable = listOf(UnreadZipEntry("deflated.cho", UnreadZipEntry.Reason.UNREADABLE), UnreadZipEntry("stored.bin", UnreadZipEntry.Reason.UNREADABLE))
        assertEquals(unreadable, inMemory.unread)
        assertEquals(unreadable, walk.unread)
        assertEquals(listOf("deflated.cho", "stored.bin", "honest.cho"), charged)
        assertEquals(inMemory.chargedSize, walk.chargedSize)
    }

    @Test
    fun `deflated data may declare up to the most deflate can inflate it to and no more`() = runTest {
        val archive = ZipWriter.write(listOf(ZipEntry("a.cho", "{title: A}\n".repeat(200).encodeToByteArray())))
        val most = archive.compressedSizeOf("a.cho") * ZipDirectory.MAX_DEFLATE_RATIO

        val answers = listOf(most, most + 1).map { declared ->
            archive.declareUncompressedSize("a.cho", declared)
            ZipReader.open(ByteArrayZipSource(archive)).canProduceDeclaredSize(0)
        }

        assertEquals(listOf(true, false), answers)
    }
}

/** The compressed size the central directory gives the entry [name]. */
internal fun ByteArray.compressedSizeOf(name: String): Long = centralRecordOf(name).let { u32(it + 20) }

/** Sets the uncompressed size the central directory declares for the entry [name] to [declared]. */
internal fun ByteArray.declareUncompressedSize(name: String, declared: Long) {
    val record = centralRecordOf(name)
    for (byte in 0 until 4) this[record + 24 + byte] = (declared ushr (8 * byte)).toByte()
}

private fun ByteArray.centralRecordOf(name: String): Int {
    val end = size - 22
    var position = u32(end + 16).toInt()
    repeat(u16(end + 10)) {
        val nameLength = u16(position + 28)
        if (decodeToString(position + 46, position + 46 + nameLength) == name) return position
        position += 46 + nameLength + u16(position + 30) + u16(position + 32)
    }
    error("No entry $name")
}
