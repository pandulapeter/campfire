package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.repository.api.SongContentRepository
import com.pandulapeter.campfire.data.repository.api.SongRepository
import com.pandulapeter.campfire.data.repository.implementation.base.BaseLocalDataRepository
import com.pandulapeter.campfire.data.source.local.api.SongLocalSource
import kotlinx.coroutines.flow.first

internal class SongRepositoryImpl(
    private val songLocalSource: SongLocalSource,
    private val songContentRepository: SongContentRepository
) : BaseLocalDataRepository<List<Song>>(
    loadDataFromLocalSource = songLocalSource::loadSongs
), SongRepository {

    override val songs = dataState

    override suspend fun loadSongsIfNeeded() = loadDataIfNeeded()

    override suspend fun rescan() {
        songContentRepository.invalidate()
        reloadData()
    }

    /**
     * Only the changed file is re-read: parsing the whole library again to pick up one edited title would make every
     * keystroke in the editor (which autosaves) cost a full rescan.
     */
    override suspend fun saveSong(content: SongContent) {
        songLocalSource.saveSongContent(content)
        songContentRepository.invalidate(content.fileName)
        val updated = songLocalSource.loadSong(content.fileName) ?: return
        val current = songs.first().data.orEmpty()
        updateData(current.filterNot { it.fileName == updated.fileName } + updated)
    }

    override suspend fun createSong(title: String, artist: String, text: String): Song {
        val song = songLocalSource.createSong(title = title, artist = artist, text = text)
        updateData(songs.first().data.orEmpty() + song)
        return song
    }

    override suspend fun deleteSong(fileName: String) {
        songLocalSource.deleteSong(fileName)
        songContentRepository.invalidate(fileName)
        updateData(songs.first().data.orEmpty().filterNot { it.fileName == fileName })
    }
}
