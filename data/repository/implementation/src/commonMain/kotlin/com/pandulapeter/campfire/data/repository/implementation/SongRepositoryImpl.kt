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

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.repository.api.SongContentRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.implementation.base.BaseLocalDataRepository
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource

internal class SongRepositoryImpl(
    private val songLocalSource: SongLocalSource,
    private val songContentRepository: SongContentRepository,
) : BaseLocalDataRepository<List<Song>>(), SongRepository {

    override val songs = dataState

    /** The scan hands its batches straight on, so the song list fills up while the rest of the library is read. */
    override suspend fun loadDataFromLocalSource() = songLocalSource.loadSongs(onProgress = ::publishPartialData)

    override suspend fun loadSongsIfNeeded() = loadDataIfNeeded()

    override suspend fun rescan() {
        songContentRepository.invalidate()
        reloadData()
    }

    /**
     * Only the changed file is re-read: parsing the whole library again to pick up one edited title would make every
     * save in the editor cost a full rescan.
     */
    override suspend fun saveSong(content: SongContent) {
        songLocalSource.saveSongContent(content)
        songContentRepository.invalidate(content.fileName)
        val updated = songLocalSource.loadSong(content.fileName) ?: return
        updateData { current -> current.orEmpty().filterNot { it.fileName == updated.fileName } + updated }
    }

    override suspend fun createSong(title: String, artist: String, text: String): Song {
        val song = songLocalSource.createSong(title = title, artist = artist, text = text)
        updateData { current -> current.orEmpty() + song }
        return song
    }

    override suspend fun importSong(desiredFileName: String?, text: String) = songLocalSource.importSong(desiredFileName, text)

    override suspend fun deleteSong(fileName: String) {
        songLocalSource.deleteSong(fileName)
        songContentRepository.invalidate(fileName)
        updateData { current -> current.orEmpty().filterNot { it.fileName == fileName } }
    }
}
