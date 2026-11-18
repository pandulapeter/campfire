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

import com.pandulapeter.campfire.chordpro.edit.ChordProPrettifier
import com.pandulapeter.campfire.data.formats.zip.ZipEntry
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

/**
 * The archives of many entries the large-archive test imports, each written a slice at a time by `ZipWriter` (see
 * [LargeArchives]), and libraries of many files written straight into a storage's folder, as the app would have filled
 * it over the years.
 */
internal object ManyEntryArchives {

    /**
     * Songs each showing [PICTURES_PER_SONG] pictures of their own, and the AppleDouble file macOS packs beside every
     * file, as a folder zipped on a Mac comes: [entryCount] entries in all, ZIP64 past 65,534.
     */
    suspend fun songsWithPictures(file: File, entryCount: Int): LargeArchive {
        val songCount = entryCount / 2 / (PICTURES_PER_SONG + 1)
        val pictureCount = songCount * PICTURES_PER_SONG
        val hashes = mutableSetOf<String>()
        val entries = (0 until songCount).map { index ->
            val pictures = (0 until PICTURES_PER_SONG).map { index * PICTURES_PER_SONG + it }
            suspend { ZipEntry("Songs/${LargeArchives.songFileName(index)}", LargeArchives.songText(index, pictures, emptyList()).encodeToByteArray()) }
        } + (0 until pictureCount).map { index ->
            suspend { ZipEntry("Songs/p$index.png", LargeArchives.picture(seed = index, size = 200 + index % 300, isCompressible = index % 2 == 0).also { hashes += LargeArchives.sha256(it) }) }
        } + (0 until entryCount - songCount - pictureCount).map { index ->
            suspend { ZipEntry("__MACOSX/Songs/._$index", APPLE_DOUBLE) }
        }
        val summary = LargeArchives.write(file, entries)
        check(summary.entryCount == entryCount)
        return LargeArchive(file = file, songCount = songCount, setlistCount = 0, fileHashes = hashes, unpackedSize = summary.unpackedSize)
    }

    /**
     * A music folder zipped by mistake, with a few songs in it: [entryCount] entries, nearly all recordings under names
     * of the length such a folder has (an artist, an album, and a numbered track title, about 90 bytes, or [nameLength]
     * bytes each where that is given, as deeper folders and longer titles make them), which the import does not read
     * and reports as left out.
     */
    suspend fun musicFolder(file: File, entryCount: Int, songCount: Int = 100, nameLength: Int? = null): LargeArchive {
        val random = Random(entryCount)
        val recording = ByteArray(RECORDING_SIZE) { it.toByte() }
        val entries = (0 until songCount).map { index ->
            suspend { ZipEntry("Music/Lyrics/${LargeArchives.songFileName(index)}", LargeArchives.songText(index, emptyList(), emptyList()).encodeToByteArray()) }
        } + (0 until entryCount - songCount).map { index ->
            suspend {
                val title = "Music/Artist name number ${index / 1_000}/Album title of some length ${index / 12}/" +
                    "${index % 12 + 1} - A track title as long as most ${random.nextInt(1_000_000)}"
                ZipEntry((if (nameLength == null) title else title.padEnd(nameLength - MP3.length, '_')) + MP3, recording)
            }
        }
        val summary = LargeArchives.write(file, entries)
        check(summary.entryCount == entryCount)
        return LargeArchive(file = file, songCount = songCount, setlistCount = 0, fileHashes = emptySet(), unpackedSize = summary.unpackedSize)
    }

    /**
     * An archive whose ZIP64 end record declares [declaredCount] entries in a central directory of none: what a reader
     * that allocates for the declared count before reading the records would run out of the heap on.
     */
    fun declaringEntries(file: File, declaredCount: Long) {
        val buffer = ByteBuffer.allocate(56 + 20 + 22).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0x06064B50).putLong(44).putShort(45).putShort(45).putInt(0).putInt(0)
        buffer.putLong(declaredCount).putLong(declaredCount).putLong(0).putLong(0)
        buffer.putInt(0x07064B50).putInt(0).putLong(0).putInt(1)
        buffer.putInt(0x06054B50).putShort(0).putShort(0).putShort(-1).putShort(-1).putInt(0).putInt(-1).putShort(0)
        file.writeBytes(buffer.array())
    }

    /**
     * Fills [root]'s library as years of use would: [songCount] songs, each showing [picturesPerSong] small pictures of
     * its own, every hundredth attaching a recording, and [attachmentSize] bytes of recordings more (each within the
     * 50 MB limit) attached across them; and a setlist. Answers how many files it wrote.
     */
    fun fillLibrary(root: File, songCount: Int, picturesPerSong: Int, attachmentSize: Long): Int {
        val songs = File(root, "library/songs").apply { mkdirs() }
        val files = File(root, "library/files").apply { mkdirs() }
        val setlists = File(root, "library/setlists").apply { mkdirs() }
        fun keep(bytes: ByteArray, extension: String) = (LargeArchives.sha256(bytes).take(16) + extension).also { File(files, it).writeBytes(bytes) }
        var remaining = attachmentSize
        val bigAttachments = mutableMapOf<Int, String>()
        var next = 0
        while (remaining > 0) {
            val size = minOf(remaining, BIG_ATTACHMENT_SIZE.toLong()).toInt()
            val bytes = LargeArchives.noise(seed = 1_000_000 + next, size = size, isCompressible = false)
            bigAttachments[next * 37 % songCount] = keep(bytes, ".mp3")
            remaining -= size
            next++
        }
        repeat(songCount) { index ->
            val pictures = (0 until picturesPerSong).map { keep(LargeArchives.picture(seed = index * picturesPerSong + it, size = 100 + it, isCompressible = false), ".png") }
            val attachments = listOfNotNull(
                if (index % 100 == 0) keep(LargeArchives.noise(seed = -index - 1, size = 3_000, isCompressible = true), ".pdf") else null,
                bigAttachments[index],
            )
            val text = "{title: Song $index}\n{artist: Artist $index}\n" + pictures.joinToString("") { "{image: src=$it}\n" } +
                attachments.joinToString("") { "{meta: attachment $it Take}\n" } + "[C]La la [G]la $index\n"
            // As the app writes a song it imports or saves, so that one exported and imported again comes back the same.
            File(songs, LargeArchives.songFileName(index)).writeText(ChordProPrettifier.prettify(text))
        }
        File(setlists, "gig.setlist.json").writeText("""{"title": "Gig", "songs": [{"fileName": "${LargeArchives.songFileName(0)}"}]}""")
        return songs.list().orEmpty().size + files.list().orEmpty().size + 1
    }

    /**
     * Fills [root]'s library with [fileCount] files in all: songs showing [PICTURES_PER_SONG] small pictures each, and
     * the last file of the folder an attachment as large as one can be that deflates, which the export holds twice
     * (read whole, and deflated into a buffer of its size) when every other entry's bookkeeping is already held.
     */
    fun fillLibraryWithDeflatingAttachment(root: File, fileCount: Int) {
        val songs = File(root, "library/songs").apply { mkdirs() }
        val files = File(root, "library/files").apply { mkdirs() }
        File(root, "library/setlists").mkdirs()
        val attachment = "ffffffffffffffff.pdf"
        File(files, attachment).writeBytes(LargeArchives.noise(seed = 1, size = ImportLimits.MAX_ATTACHMENT_FILE_SIZE.toInt(), isCompressible = true))
        var remaining = fileCount - 1
        var index = 0
        while (remaining > 0) {
            val pictures = (0 until minOf(PICTURES_PER_SONG, remaining - 1)).map {
                val bytes = LargeArchives.picture(seed = index * PICTURES_PER_SONG + it, size = 100 + it, isCompressible = false)
                // Below the attachment's name, so that it is the last entry written.
                (LargeArchives.sha256(bytes).take(16).replace('f', 'e') + ".png").also { name -> File(files, name).writeBytes(bytes) }
            }
            val attached = if (index == 0) "{meta: attachment $attachment Take}\n" else ""
            val text = "{title: Song $index}\n{artist: Artist $index}\n" + pictures.joinToString("") { "{image: src=$it}\n" } + attached + "[C]La la [G]la $index\n"
            File(songs, LargeArchives.songFileName(index)).writeText(ChordProPrettifier.prettify(text))
            remaining -= 1 + pictures.size
            index++
        }
    }

    private val APPLE_DOUBLE = byteArrayOf(0, 5, 22, 7, 0, 2, 0, 0)
    private const val RECORDING_SIZE = 16
    private const val MP3 = ".mp3"
    private const val PICTURES_PER_SONG = 9
    private const val BIG_ATTACHMENT_SIZE = 45_000_000
}
