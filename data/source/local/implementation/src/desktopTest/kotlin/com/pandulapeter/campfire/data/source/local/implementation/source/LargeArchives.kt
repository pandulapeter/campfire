/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.formats.zip.ZipEntry
import com.pandulapeter.campfire.data.formats.zip.ZipSink
import com.pandulapeter.campfire.data.formats.zip.ZipWriter
import com.pandulapeter.campfire.data.source.local.implementation.source.LargeImportApp.Companion.toHex
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipOutputStream
import kotlin.random.Random

/**
 * An archive [LargeArchives] wrote into [file], and what importing it must bring: [songCount] songs, [setlistCount]
 * setlists, and the library files of [fileHashes] (the SHA-256 of each picture and attachment its songs name).
 * [unpackedSize] is what its entries and those of its nested archives hold together: what the staging may hold beside the
 * archive itself while it unpacks.
 */
internal class LargeArchive(
    val file: File,
    val songCount: Int,
    val setlistCount: Int,
    val fileHashes: Set<String>,
    val unpackedSize: Long,
)

/**
 * The archives the large-archive test imports, written on the fly with `ZipWriter`'s streaming write a slice at a time
 * (never held whole, since the test runs in the heap it proves the import within), of pseudo-random bytes from fixed
 * seeds, so that every run imports the same files.
 */
internal object LargeArchives {

    /**
     * A library of about [size] bytes (less where its pictures deflate), as people bring them: [songCount] songs, each
     * showing a picture (a few shared by two songs) and every few naming an attachment, half the pictures and a few
     * attachments deflated and the rest stored, as `ZipWriter` decides by trying; with [hasLimitSizedFiles], two pictures
     * near the 8 MB limit and an attachment near the 50 MB one. Then a setlist, and two nested archives: one ZIP64 by its
     * count of entries, and one with data descriptors, as Java's own writer streams it. Past 4 GiB it is ZIP64 by size.
     */
    suspend fun library(file: File, size: Long, songCount: Int = 3_000, seed: Int = 1, hasLimitSizedFiles: Boolean = true): LargeArchive {
        val random = Random(seed)
        val hashes = mutableMapOf<String, String>()
        val pictureSizes = IntArray(songCount) { index ->
            when (index) {
                0, 1 -> if (hasLimitSizedFiles) LARGE_PICTURE_SIZE else 50_000
                else -> 2_000 + random.nextInt(60_000)
            }
        }
        val counted = zip64ByCount(seed)
        val streamed = withDataDescriptors(seed)
        val nestedSize = (counted.bytes.size + streamed.bytes.size).toLong()
        var attachmentBudget = size - pictureSizes.sumOf { it.toLong() } - nestedSize - songCount * SONG_SIZE
        val attachments = mutableListOf<Attachment>()
        while (attachmentBudget > 0) {
            val index = attachments.size
            val attachment = when {
                index == 0 && hasLimitSizedFiles -> Attachment(index, LARGE_ATTACHMENT_SIZE, ".mp3", isCompressible = false)
                index % 4 == 3 -> Attachment(index, 1_000_000 + random.nextInt(3_000_000), ".pdf", isCompressible = true)
                else -> Attachment(index, 1_000_000 + random.nextInt(29_000_000), ".mp3", isCompressible = false)
            }
            attachments += attachment
            attachmentBudget -= attachment.size
        }
        val attachmentsBySong = attachments.groupBy { it.index * songCount / maxOf(attachments.size, 1) }
        val songs = (0 until songCount).map { index ->
            val named = attachmentsBySong[index].orEmpty() + listOfNotNull(attachments.firstOrNull()?.takeIf { index == 2 })
            ZipEntry(name = "songs/${songFileName(index)}", bytes = songText(index, pictures = listOfNotNull(index, (index - 1).takeIf { index % 50 == 1 }), attachments = named).encodeToByteArray())
        }
        val setlist = ZipEntry(
            name = "setlists/gig.setlist.json",
            bytes = """{"title": "Gig", "songs": [${(0 until 5).joinToString { """{"fileName": "${songFileName(it)}"}""" }}]}""".encodeToByteArray(),
        )
        val entries = songs.map { entry -> suspend { entry } } +
            listOf(suspend { setlist }, suspend { ZipEntry("nested/counted.zip", counted.bytes) }, suspend { ZipEntry("nested/streamed.zip", streamed.bytes) }) +
            (0 until songCount).map { index ->
                suspend {
                    val bytes = picture(seed = seed * 1_000_003 + index, size = pictureSizes[index], isCompressible = index % 2 == 0)
                    hashes["p$index"] = sha256(bytes)
                    ZipEntry("images/p$index.png", bytes)
                }
            } +
            attachments.map { attachment ->
                suspend {
                    val bytes = noise(seed = seed * 7_919 + attachment.index, size = attachment.size, isCompressible = attachment.isCompressible)
                    hashes[attachment.fileName] = sha256(bytes)
                    ZipEntry("images/${attachment.fileName}", bytes)
                }
            }
        val summary = write(file, entries)
        return LargeArchive(
            file = file,
            songCount = songCount + counted.songCount + streamed.songCount,
            setlistCount = 1,
            fileHashes = hashes.values.toSet() + counted.fileHashes + streamed.fileHashes,
            unpackedSize = summary.unpackedSize + counted.unpackedSize + streamed.unpackedSize,
        )
    }

    /** Writes [entries] into [file] through `ZipWriter`'s streaming write, as an export writes one. */
    suspend fun write(file: File, entries: List<suspend () -> ZipEntry?>) = file.outputStream().buffered(SLICE_SIZE).use { output ->
        ZipWriter.write(
            entries = entries,
            sink = object : ZipSink {
                override var position = 0L

                override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
                    output.write(bytes, offset, length)
                    position += length
                }
            },
        )
    }

    fun songFileName(index: Int) = "artist_$index-song_$index.cho"

    fun songText(index: Int, pictures: List<Int>, attachments: List<Attachment>, title: String = "Song $index", artist: String = "Artist $index") =
        "{title: $title}\n{artist: $artist}\n" +
            pictures.joinToString("") { "{image: src=p$it.png}\n" } +
            attachments.joinToString("") { "{meta: attachment ${it.fileName} Take ${it.index}}\n" } +
            "[C]La la [G]la $index\n"

    /** A picture the import takes for one: a PNG header, then bytes that deflate well, or noise that does not. */
    fun picture(seed: Int, size: Int, isCompressible: Boolean) = TestPictures.png(seed = seed, size = minOf(size, PNG_HEADER_SIZE)).let { header ->
        header + noise(seed = seed, size = size - header.size, isCompressible = isCompressible)
    }

    /** [size] bytes of [seed]: four letters at random where [isCompressible], so that they deflate to about a quarter. */
    fun noise(seed: Int, size: Int, isCompressible: Boolean): ByteArray {
        val bytes = Random(seed).nextBytes(size)
        if (isCompressible) for (index in bytes.indices) bytes[index] = ('a'.code + (bytes[index].toInt() and 3)).toByte()
        return bytes
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    /** A nested archive's bytes, with the songs it holds and the hashes of the files they name. */
    class Nested(val bytes: ByteArray, val songCount: Int, val fileHashes: Set<String>, val unpackedSize: Long)

    /**
     * A nested library past 65,535 entries, so `ZipWriter` writes it as ZIP64 by count: a few songs with their pictures,
     * and the AppleDouble files macOS packs beside every file, which the import passes over unread.
     */
    private suspend fun zip64ByCount(seed: Int): Nested {
        val songCount = 150
        val pictures = (0 until songCount).map { picture(seed = seed * 31 + it, size = 3_000 + it * 10, isCompressible = it % 2 == 0) }
        val entries = (0 until songCount).map { index ->
            ZipEntry("songs/counted_$index.cho", songText(index, listOf(index), emptyList(), title = "Counted $index", artist = "Nested").encodeToByteArray())
        } + pictures.mapIndexed { index, bytes -> ZipEntry("images/p$index.png", bytes) } +
            (0 until ZIP64_COUNT - 2 * songCount).map { ZipEntry("__MACOSX/songs/._counted_$it.cho", byteArrayOf(0, 5, 22, 7)) }
        return Nested(bytes = ZipWriter.write(entries), songCount = songCount, fileHashes = pictures.map(::sha256).toSet(), unpackedSize = entries.sumOf { it.bytes.size.toLong() })
    }

    /** A nested library written by Java's `ZipOutputStream`, which deflates every entry behind a data descriptor. */
    private fun withDataDescriptors(seed: Int): Nested {
        val songCount = 100
        val pictures = (0 until songCount).map { picture(seed = seed * 37 + it, size = 5_000 + it * 100, isCompressible = it % 3 == 0) }
        val recording = Attachment(index = 9_999, size = 2_000_000, extension = ".m4a", isCompressible = false)
        val recordingBytes = noise(seed = seed * 41, size = recording.size, isCompressible = false)
        val output = ByteArrayOutputStream()
        var unpacked = 0L
        ZipOutputStream(output).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(bytes)
                unpacked += bytes.size
                zip.closeEntry()
            }
            (0 until songCount).forEach { index ->
                val attachments = if (index == 0) listOf(recording) else emptyList()
                put("Streamed/streamed_$index.cho", songText(index, listOf(index), attachments, title = "Streamed $index", artist = "Nested").encodeToByteArray())
            }
            pictures.forEachIndexed { index, bytes -> put("Streamed/p$index.png", bytes) }
            put("Streamed/${recording.fileName}", recordingBytes)
        }
        return Nested(bytes = output.toByteArray(), songCount = songCount, fileHashes = pictures.map(::sha256).toSet() + sha256(recordingBytes), unpackedSize = unpacked)
    }

    /** A file a song attaches, named by [index] as Campfire's export names one by its content. */
    class Attachment(val index: Int, val size: Int, val extension: String, val isCompressible: Boolean) {
        val fileName get() = "%016x".format(0x5eed_0000_0000L + index) + extension
    }

    private const val SLICE_SIZE = 1024 * 1024
    private const val SONG_SIZE = 120L
    private const val PNG_HEADER_SIZE = 33
    private const val LARGE_PICTURE_SIZE = 7_900_000
    private const val LARGE_ATTACHMENT_SIZE = 49_000_000
    private const val ZIP64_COUNT = 65_600
}
