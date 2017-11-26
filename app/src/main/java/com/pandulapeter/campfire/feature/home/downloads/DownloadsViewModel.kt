package com.pandulapeter.campfire.feature.home.downloads

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.home.shared.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.HomeFragmentViewModel
import com.pandulapeter.campfire.feature.home.shared.SongInfoViewModel
import com.pandulapeter.campfire.util.onPropertyChanged

/**
 * Handles events and logic for [DownloadsFragment].
 */
class DownloadsViewModel(homeCallbacks: HomeFragment.HomeCallbacks?, songInfoRepository: SongInfoRepository) : HomeFragmentViewModel(homeCallbacks, songInfoRepository) {
    val searchInputVisible = ObservableBoolean(songInfoRepository.downloadsQuery.isNotEmpty())
    val query = ObservableField(songInfoRepository.downloadsQuery)

    init {
        searchInputVisible.onPropertyChanged { query.set("") }
        query.onPropertyChanged { songInfoRepository.downloadsQuery = it }
    }

    override fun getAdapterItems() = songInfoRepository.getDownloadsSongs().filter(query.get()).map { songInfo ->
        val isTinted = songInfoRepository.isSongFavorite(songInfo.id)
        SongInfoViewModel(
            songInfo = songInfo,
            actionDescription = if (isTinted) R.string.downloads_remove_from_favorites else R.string.downloads_add_to_favorites,
            actionIcon = R.drawable.ic_favorites_24dp,
            isActionTinted = isTinted)
    }

    fun addSongToDownloads(id: String) = songInfoRepository.addSongToDownloads(id)

    fun removeSongFromDownloads(id: String) = songInfoRepository.removeSongFromDownloads(id)

    fun addOrRemoveSongFromFavorites(id: String) =
        if (songInfoRepository.isSongFavorite(id)) {
            songInfoRepository.removeSongFromFavorites(id)
        } else {
            songInfoRepository.addSongToFavorites(id)
        }
}