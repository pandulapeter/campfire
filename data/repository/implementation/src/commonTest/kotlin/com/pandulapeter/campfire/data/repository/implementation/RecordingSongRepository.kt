/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.repository.api.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Stands in for the song list that sync tells to read the library again, and records what it was told: how many whole
 * rescans, and which files it was asked to refresh. [onRescan] runs on every rescan, which is where a test sees what the
 * library held at that moment. The list has been read unless a test says otherwise through [songs], and
 * [onLoadIfNeeded] is where it finishes a read it left going.
 */
internal class RecordingSongRepository(
    private val onRescan: () -> Unit = {},
    override val songs: MutableStateFlow<DataState<List<Song>>> = MutableStateFlow(DataState.Idle(emptyList())),
    private val onLoadIfNeeded: suspend () -> Unit = {},
    private val onRefresh: suspend (Set<String>) -> Unit = {},
) : SongRepository {

    var rescanCount = 0
    val refreshed = mutableListOf<String>()

    override suspend fun loadSongsIfNeeded(): List<Song>? {
        onLoadIfNeeded()
        return songs.value.data
    }

    override suspend fun loadSongFileSizes(): Map<String, Long> = throw UnsupportedOperationException()

    override suspend fun rescan() {
        rescanCount++
        onRescan()
    }

    override suspend fun refresh(fileNames: Set<String>) {
        onRefresh(fileNames)
        refreshed += fileNames
    }

    override suspend fun adoptImported(songs: Collection<Song>) = throw UnsupportedOperationException()

    override suspend fun saveSong(content: SongContent, expectedText: String?): Boolean = throw UnsupportedOperationException()

    override suspend fun createSong(title: String, artist: String, text: String): Song = throw UnsupportedOperationException()

    override fun importFileName(fallbackTitle: String, text: String): String = throw UnsupportedOperationException()

    override suspend fun importSong(fileName: String, text: String, shouldReplace: Boolean): Song =
        throw UnsupportedOperationException()

    override suspend fun renameSong(song: Song): Song? = throw UnsupportedOperationException()

    override suspend fun deleteSong(fileName: String): Unit = throw UnsupportedOperationException()
    override suspend fun deleteAllSongs(): Unit = throw UnsupportedOperationException()
}
