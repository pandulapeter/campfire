/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation.sync

import com.pandulapeter.campfire.data.model.domain.LibraryFileKind
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource

/**
 * The songs of a [FakeLibraryFileLocalSource] as the song repository sees them, so that a test can run the repository
 * and [SyncEngine] over one library. Its writes go straight into the map rather than through
 * [FakeLibraryFileLocalSource.writeLibraryFile], whose hooks are where a test stands in the middle of a sync run's own
 * writes. Only the calls a save and a deletion make are answered.
 */
internal class LibrarySongLocalSource(private val library: FakeLibraryFileLocalSource) : SongLocalSource {

    override suspend fun loadSongs(onProgress: (List<Song>) -> Unit) =
        library.files.keys.filter { it.kind == LibraryFileKind.SONG }.map { song(it.name) }

    override suspend fun loadSongFileSizes() = throw UnsupportedOperationException()

    override suspend fun loadSong(fileName: String) = if (key(fileName) in library.files) song(fileName) else null

    override suspend fun loadSongContent(fileName: String) =
        library.files[key(fileName)]?.let { SongContent(fileName = fileName, text = it.decodeToString()) }

    override suspend fun saveSongContent(content: SongContent) {
        library.files[key(content.fileName)] = content.text.encodeToByteArray()
    }

    override suspend fun createSong(title: String, artist: String, text: String) = throw UnsupportedOperationException()

    override fun importFileName(fallbackTitle: String, text: String) = throw UnsupportedOperationException()

    override suspend fun importSong(fileName: String, text: String, shouldReplace: Boolean) = throw UnsupportedOperationException()

    override suspend fun renameSong(song: Song) = throw UnsupportedOperationException()

    override suspend fun deleteSong(fileName: String) {
        library.files -= key(fileName)
    }

    override suspend fun exists(fileName: String) = key(fileName) in library.files

    private fun key(fileName: String) = SyncKey(kind = LibraryFileKind.SONG, name = fileName)

    private fun song(fileName: String) = Song(
        fileName = fileName,
        title = fileName,
        artist = "",
        key = null,
        transpose = 0,
        tags = emptyList(),
        languages = emptyList(),
        hasChords = false,
        canUpdateFileName = false,
        lastModified = 0L,
        size = 0L,
    )
}
