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
import com.pandulapeter.campfire.data.source.local.implementation.songFileName
import com.pandulapeter.campfire.data.source.local.implementation.uniqueName
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StoredFileInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal class SongLocalSourceImpl(
    private val fileStorage: FileStorage
) : SongLocalSource {

    /**
     * Reading and parsing every file is the slowest thing the app does at start, so the files are read in parallel.
     * One unreadable file must not empty the whole list, so a failure skips that song instead of propagating.
     */
    override suspend fun loadSongs(): List<Song> = coroutineScope {
        fileStorage.list(StorageDirectory.SONGS)
            .filter { it.name.isSongFileName() }
            .map { async { it.readSong() } }
            .awaitAll()
            .filterNotNull()
    }

    override suspend fun loadSong(fileName: String): Song? = fileStorage.list(StorageDirectory.SONGS)
        .firstOrNull { it.name == fileName }
        ?.readSong()

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

    override suspend fun importSong(desiredFileName: String?, text: String): Song {
        val desired = desiredFileName ?: ChordProParser.parseMetadata(text).let { songFileName(title = it.title.orEmpty(), artist = it.artist.orEmpty()) }
        val fileName = fileStorage.uniqueName(StorageDirectory.SONGS, desired)
        fileStorage.writeText(StorageDirectory.SONGS, fileName, text)
        return loadSong(fileName) ?: throw IllegalStateException("The song \"$fileName\" disappeared right after it was written.")
    }

    override suspend fun deleteSong(fileName: String) = fileStorage.delete(StorageDirectory.SONGS, fileName)

    override suspend fun exists(fileName: String) = fileStorage.exists(StorageDirectory.SONGS, fileName)

    private suspend fun StoredFileInfo.readSong(): Song? = try {
        fileStorage.readText(StorageDirectory.SONGS, name)?.let { text ->
            toSong(metadata = ChordProParser.parseMetadata(text), hasChords = ChordProParser.hasChords(text))
        }
    } catch (exception: Exception) {
        println("Could not read the song \"$name\": ${exception.message}")
        null
    }
}
