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
    }

    @Test
    fun `a streamed archive reads back`() = runTest {
        val sink = ChunkSink()

        ZipWriter.write(entries = entries.map { entry -> { entry } }, sink = sink)

        val read = ZipReader.read(sink.bytes()).entries
        assertEquals(entries.map { it.name }, read.map { it.name })
        entries.forEachIndexed { index, entry -> assertContentEquals(entry.bytes, read[index].bytes) }
    }

    /** Keeps each write as a chunk of its own, the way a file sink sees them, rather than one growing buffer. */
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
