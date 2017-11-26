package com.pandulapeter.campfire.feature.home.cloud

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.home.shared.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.HomeFragmentViewModel
import com.pandulapeter.campfire.feature.home.shared.SongInfoViewModel
import com.pandulapeter.campfire.util.onPropertyChanged

/**
 * Handles events and logic for [CloudFragment].
 */
class CloudViewModel(homeCallbacks: HomeFragment.HomeCallbacks?, songInfoRepository: SongInfoRepository) : HomeFragmentViewModel(homeCallbacks, songInfoRepository) {
    val searchInputVisible = ObservableBoolean(songInfoRepository.cloudQuery.isNotEmpty())
    val query = ObservableField(songInfoRepository.cloudQuery)

    init {
        searchInputVisible.onPropertyChanged { query.set("") }
        query.onPropertyChanged { songInfoRepository.cloudQuery = it }
    }

    override fun getAdapterItems() = songInfoRepository.getCloudSongs().filter(query.get()).map { songInfo ->
        val isTinted = songInfoRepository.isSongDownloaded(songInfo.id)
        SongInfoViewModel(
            songInfo = songInfo,
            actionDescription = if (isTinted) R.string.cloud_delete_from_downloaded_songs else R.string.cloud_download_song,
            actionIcon = R.drawable.ic_downloaded_24dp,
            isActionTinted = isTinted)
    }

    fun addOrRemoveSongFromDownloaded(id: String) =
        if (songInfoRepository.isSongDownloaded(id)) {
            songInfoRepository.removeSongFromDownloaded(id)
        } else {
            songInfoRepository.addSongToDownloaded(id)
        }
}