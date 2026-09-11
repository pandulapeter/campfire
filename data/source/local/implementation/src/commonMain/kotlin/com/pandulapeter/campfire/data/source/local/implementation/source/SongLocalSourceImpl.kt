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

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.mapper.toSong
import com.pandulapeter.campfire.data.source.local.implementation.isNamed
import com.pandulapeter.campfire.data.source.local.implementation.knownExtension
import com.pandulapeter.campfire.data.source.local.implementation.songFileName
import com.pandulapeter.campfire.data.source.local.implementation.uniqueName
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StoredFileInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal class SongLocalSourceImpl(
    private val fileStorage: FileStorage,
) : SongLocalSource {

    /**
     * Reading and parsing every file is the slowest thing the app does at start, so the files are read in parallel,
     * [BATCH_SIZE] of them at a time. The batching is what bounds the concurrency - a library of thousands of songs
     * would otherwise have every one of its files open at once - and it is also what the screen is fed with, so that
     * a long scan fills the list as it goes instead of showing nothing until the last file is parsed.
     *
     * One unreadable file must not empty the whole list, so a failure skips that song instead of propagating.
     */
    override suspend fun loadSongs(onProgress: (List<Song>) -> Unit): List<Song> = coroutineScope {
        val songs = mutableListOf<Song>()
        val batches = fileStorage.list(StorageDirectory.SONGS).filter { it.name.isSongFileName() }.chunked(BATCH_SIZE)
        batches.forEachIndexed { index, batch ->
            songs += batch.map { async { it.readSong() } }.awaitAll().filterNotNull()
            // The last batch is what the return value already says, and publishing it would only have everything
            // downstream sort and group the same list a second time.
            if (index < batches.lastIndex) onProgress(songs.toList())
        }
        songs
    }

    override suspend fun loadSong(fileName: String): Song? = fileStorage.info(StorageDirectory.SONGS, fileName)?.readSong()

    /**
     * Campfire writes `.cho`, but a folder the user can also open in a file manager will hold whatever they put in
     * it, and every ChordPro extension names the same thing.
     */
    private fun String.isSongFileName() = LibraryFiles.SONG_EXTENSIONS.any { endsWith(it, ignoreCase = true) }

    override suspend fun loadSongContent(fileName: String) = fileStorage.readText(StorageDirectory.SONGS, fileName)
        ?.let { SongContent(fileName = fileName, text = it) }

    override suspend fun saveSongContent(content: SongContent) = fileStorage.writeText(StorageDirectory.SONGS, content.fileName, content.text)

    override suspend fun createSong(title: String, artist: String, text: String): Song {
        val fileName = fileStorage.uniqueName(StorageDirectory.SONGS, songFileName(title = title, artist = artist))
        fileStorage.writeText(StorageDirectory.SONGS, fileName, text)
        return loadSong(fileName) ?: throw IllegalStateException("The song \"$fileName\" disappeared right after it was written.")
    }

    override fun importFileName(desiredFileName: String?, text: String) = desiredFileName
        ?: ChordProParser.parseMetadata(text).let { songFileName(title = it.title.orEmpty(), artist = it.artist.orEmpty()) }

    override suspend fun importSong(fileName: String, text: String, shouldReplace: Boolean): Song {
        val name = if (shouldReplace) fileName else fileStorage.uniqueName(StorageDirectory.SONGS, fileName)
        fileStorage.writeText(StorageDirectory.SONGS, name, text)
        return loadSong(name) ?: throw IllegalStateException("The song \"$name\" disappeared right after it was written.")
    }

    override suspend fun renameSong(song: Song): Song? {
        val extension = song.fileName.knownExtension()
        val desired = songFileName(title = song.title, artist = song.artist, extension = extension)
        if (song.fileName.isNamed(desired)) return null
        val text = fileStorage.readText(StorageDirectory.SONGS, song.fileName) ?: return null
        val fileName = fileStorage.uniqueName(StorageDirectory.SONGS, desired)
        // Written before the old one is removed, so that a rename that fails halfway leaves the song twice over
        // rather than not at all.
        fileStorage.writeText(StorageDirectory.SONGS, fileName, text)
        fileStorage.delete(StorageDirectory.SONGS, song.fileName)
        return loadSong(fileName)
    }

    override suspend fun deleteSong(fileName: String) = fileStorage.delete(StorageDirectory.SONGS, fileName)

    override suspend fun exists(fileName: String) = fileStorage.exists(StorageDirectory.SONGS, fileName)

    private suspend fun StoredFileInfo.readSong(): Song? = try {
        fileStorage.readText(StorageDirectory.SONGS, name)?.let { text ->
            toSong(ChordProParser.summarize(text))
        }
    } catch (exception: CancellationException) {
        // A library scan that was cancelled is not a library of unreadable songs.
        throw exception
    } catch (exception: Exception) {
        println("Could not read the song \"$name\": ${exception.message}")
        null
    }

    private companion object {
        const val BATCH_SIZE = 64
    }
}
