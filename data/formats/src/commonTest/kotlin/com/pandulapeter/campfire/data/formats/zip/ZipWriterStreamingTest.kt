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

import com.pandulapeter.campfire.data.model.hashing.Sha256
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

internal class ZipWriterStreamingTest {

    private val entries = listOf(
        ZipEntry("songs/riverlight.cho", "{title: Riverlight}\n[Am]Down by the [C]river\n".repeat(30).encodeToByteArray()),
        ZipEntry("covers/cover.jpg", Random(7).nextBytes(20_000)),
        ZipEntry("empty.cho", ByteArray(0)),
        ZipEntry("setlists/Árvíztűrő.setlist.json", "{\"title\": \"Árvíztűrő\"}".encodeToByteArray()),
    )

    @Test
    fun `an archive streamed into a sink is byte for byte the one written in memory`() = runTest {
        val sink = ChunkSink()

        ZipWriter.write(entries = entries.map { entry -> { entry } }, sink = sink)

        assertContentEquals(ZipWriter.write(entries), sink.bytes())
        assertEquals("3a54450db27d81ecb35d3fd86d0219f154a314cdab882fd634affbc197f279ef", Sha256.hashToHex(sink.bytes()))
    }

    @Test
    fun `each entry is read once and only after the one before it is written`() = runTest {
        val sink = ChunkSink()
        val reads = mutableListOf<String>()
        val positionsAtRead = mutableListOf<Long>()

        ZipWriter.write(
            entries = entries.map { entry ->
                {
                    if (entry.name in reads) fail("\"${entry.name}\" was read twice.")
                    reads += entry.name
                    positionsAtRead += sink.position
                    entry
                }
            },
            sink = sink,
        )

        assertEquals(entries.map { it.name }, reads)
        assertTrue(positionsAtRead.zipWithNext().all { (before, after) -> after > before }, "Read at $positionsAtRead.")
    }

    @Test
    fun `a central directory of many long names reaches the sink in slices rather than whole`() = runTest {
        val many = List(MANY_ENTRIES) { ZipEntry("Music/" + "Artist and album and a track title ".repeat(6) + "$it.mp3", byteArrayOf(it.toByte())) }
        val written = ByteArrayBuilder()
        var largestWrite = 0

        ZipWriter.write(
            entries = many.map { entry -> { entry } },
            sink = object : ZipSink {
                override val position get() = written.size.toLong()

                override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
                    largestWrite = maxOf(largestWrite, length)
                    written.bytes(bytes, offset, length)
                }
            },
        )

        val archive = written.build()
        assertContentEquals(ZipWriter.write(many), archive)
        assertEquals(many.map { it.name }, readArchive(archive).entries.map { it.name })
        assertTrue(largestWrite < MANY_ENTRIES * 100, "A write of $largestWrite bytes, of a directory of about ${MANY_ENTRIES * 250}")
    }

    @Test
    fun `the streaming writer tells how many entries it wrote and what they add up to unpacked`() = runTest {
        val summary = ZipWriter.write(entries = entries.map { entry -> { entry } }, sink = ChunkSink())

        assertEquals(ZipSummary(entryCount = entries.size, unpackedSize = entries.sumOf { it.bytes.size.toLong() }), summary)
    }

    @Test
    fun `an entry read as nothing is left out`() = runTest {
        val sink = ChunkSink()

        val summary = ZipWriter.write(
            entries = entries.map { entry -> { entry.takeUnless { it.name == "covers/cover.jpg" } } },
            sink = sink,
        )

        val kept = entries.filter { it.name != "covers/cover.jpg" }
        assertEquals(kept.size, summary.entryCount)
        assertContentEquals(ZipWriter.write(kept), sink.bytes())
        assertEquals("87dc918099d23a6b26caba56b73f739f827bf15a64c9a917cd66d6ed82f362b4", Sha256.hashToHex(sink.bytes()))
    }

    @Test
    fun `a streamed archive reads back`() = runTest {
        val sink = ChunkSink()

        ZipWriter.write(entries = entries.map { entry -> { entry } }, sink = sink)

        val read = readArchive(sink.bytes()).entries
        assertEquals(entries.map { it.name }, read.map { it.name })
        entries.forEachIndexed { index, entry -> assertContentEquals(entry.bytes, read[index].bytes) }
    }

    /** Keeps each write as a chunk of its own, the way a file sink sees them, rather than one growing buffer. */
    private companion object {
        const val MANY_ENTRIES = 4_000
    }

    private class ChunkSink : ZipSink {

        private val chunks = mutableListOf<ByteArray>()

        override var position = 0L
            private set

        override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
            chunks += bytes.copyOfRange(offset, offset + length)
            position += length
        }

        fun bytes() = chunks.fold(ByteArray(0)) { all, chunk -> all + chunk }
    }
}
