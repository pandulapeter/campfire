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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

@Single
internal class SongRepositoryImpl(
    private val songLocalSource: SongLocalSource,
    private val songContentRepository: SongContentRepository,
) : BaseLocalDataRepository<List<Song>>(), SongRepository {

    override val songs = dataState

    /**
     * Held by everything that asks the storage for a free name and then writes under it. The two are separate trips
     * to the file system with a suspension between them, and a second asker getting in there is given the same name:
     * one file, written twice, and listed twice.
     */
    private val nameMutex = Mutex()

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
    override suspend fun saveSong(content: SongContent, expectedText: String?): Boolean {
        if (expectedText != null && songLocalSource.loadSongContent(content.fileName)?.text != expectedText) {
            // The cached text is what the refused change was built on, so it goes too: the caller's next read has to
            // reach the file rather than hand the same stale text back.
            songContentRepository.invalidate(content.fileName)
            return false
        }
        songLocalSource.saveSongContent(content)
        songContentRepository.invalidate(content.fileName)
        val updated = songLocalSource.loadSong(content.fileName) ?: return true
        updateData { current -> current.orEmpty().filterNot { it.fileName == updated.fileName } + updated }
        return true
    }

    override suspend fun createSong(title: String, artist: String, text: String): Song = nameMutex.withLock {
        songLocalSource.createSong(title = title, artist = artist, text = text).also { song ->
            // Replaced rather than appended, like every other change to the list: the file name is what the lists key
            // their rows by, so a name that is in the cache already - a file deleted behind the app's back whose name
            // has just been given out again - must not end up in it twice.
            updateData { current -> current.orEmpty().filterNot { it.fileName == song.fileName } + song }
        }
    }

    override fun importFileName(fallbackTitle: String, text: String) = songLocalSource.importFileName(fallbackTitle, text)

    override suspend fun importSong(fileName: String, text: String, shouldReplace: Boolean) = nameMutex.withLock {
        songLocalSource.importSong(fileName = fileName, text = text, shouldReplace = shouldReplace)
    }

    override suspend fun renameSong(song: Song): Song? = nameMutex.withLock {
        val renamed = songLocalSource.renameSong(song) ?: return@withLock null
        songContentRepository.invalidate(song.fileName)
        updateData { current ->
            current.orEmpty().filterNot { it.fileName == song.fileName || it.fileName == renamed.fileName } + renamed
        }
        renamed
    }

    override suspend fun deleteSong(fileName: String) {
        songLocalSource.deleteSong(fileName)
        songContentRepository.invalidate(fileName)
        updateData { current -> current.orEmpty().filterNot { it.fileName == fileName } }
    }
}
