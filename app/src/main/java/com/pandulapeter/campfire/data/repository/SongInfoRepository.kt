package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.data.model.DownloadedSong
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.network.NetworkManager
import com.pandulapeter.campfire.data.storage.DataStorageManager
import com.pandulapeter.campfire.data.storage.PreferenceStorageManager
import com.pandulapeter.campfire.util.enqueueCall
import java.util.Collections

/**
 * Wraps caching and updating of [SongInfo] objects.
 */
class SongInfoRepository(
    private val preferenceStorageManager: PreferenceStorageManager,
    private val dataStorageManager: DataStorageManager,
    private val networkManager: NetworkManager,
    private val languageRepository: LanguageRepository) : Repository() {
    private var dataSet = dataStorageManager.cloudCache
        get() {
            if (!isLoading && System.currentTimeMillis() - preferenceStorageManager.lastUpdateTimestamp > CACHE_VALIDITY_LIMIT) {
                updateDataSet()
            }
            return field
        }
        set(value) {
            if (field != value) {
                field = value
                languageRepository.updateLanguages(value)
                notifySubscribers()
            }
        }
    var isLoading = false
        set(value) {
            if (field != value) {
                field = value
                notifySubscribers()
            }
        }

    init {
        languageRepository.updateLanguages(dataSet)
    }

    fun updateDataSet(onError: () -> Unit = {}) {
        isLoading = true
        networkManager.service.getLibrary().enqueueCall(
            onSuccess = {
                dataSet = it
                isLoading = false
                dataStorageManager.cloudCache = dataSet
                preferenceStorageManager.lastUpdateTimestamp = System.currentTimeMillis()
            },
            onFailure = {
                isLoading = false
                onError()
            })
    }

    fun getLibrarySongs() = dataSet

    fun getDownloadedSongs() = dataStorageManager.downloads

    fun isSongDownloaded(id: String) = getDownloadedSongs().map { it.id }.contains(id)

    //TODO: Also save the SongDetail object.
    fun addSongToDownloads(songInfo: SongInfo) {
        dataStorageManager.downloads = getDownloadedSongs().filter { it.id != songInfo.id }.toMutableList().apply { add(DownloadedSong(songInfo.id, songInfo.version ?: 0)) }
        notifySubscribers()
    }

    fun removeSongFromDownloads(id: String) {
        removeSongFromFavorites(id)
        if (isSongDownloaded(id)) {
            dataStorageManager.downloads = getDownloadedSongs().filter { it.id != id }
            notifySubscribers()
        }
    }

    //TODO: Move to PlaylistRepository.
    fun getFavoriteSongs() = dataStorageManager.favorites.mapNotNull { id -> dataSet.find { id == it.id } }

    //TODO: Move to PlaylistRepository.
    fun isSongFavorite(id: String) = dataStorageManager.favorites.contains(id)

    //TODO: Move to PlaylistRepository.
    fun addSongToFavorites(id: String, position: Int? = null) {
        if (!isSongFavorite(id)) {
            dataStorageManager.favorites = dataStorageManager.favorites.toMutableList().apply {
                if (position == null) add(id) else add(position, id)
            }
            notifySubscribers()
        }
    }

    //TODO: Move to PlaylistRepository.
    fun removeSongFromFavorites(id: String) {
        if (isSongFavorite(id)) {
            dataStorageManager.favorites = dataStorageManager.favorites.toMutableList().apply { remove(id) }
            notifySubscribers()
        }
    }

    //TODO: Move to PlaylistRepository.
    fun setFavorites(songIds: List<String>) {
        dataStorageManager.favorites = songIds
        notifySubscribers()
    }

    //TODO: Move to PlaylistRepository.
    fun shuffleFavorites() {
        val list = dataStorageManager.favorites.toMutableList()
        if (list.size > SHUFFLE_LIMIT) {
            val newList = list.toMutableList()
            while (newList == list) {
                Collections.shuffle(newList)
            }
            dataStorageManager.favorites = newList
            notifySubscribers()
        }
    }

    companion object {
        const val SHUFFLE_LIMIT = 2
        private const val CACHE_VALIDITY_LIMIT = 1000 * 60 * 60 * 24
    }
}