package com.pandulapeter.campfire.feature.home.cloud

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.repository.ChangeListener
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.home.shared.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.HomeFragmentViewModel
import com.pandulapeter.campfire.feature.home.shared.SongInfoViewModel
import com.pandulapeter.campfire.util.sort

/**
 * Handles events and logic for [CloudFragment].
 */
class CloudViewModel(homeCallbacks: HomeFragment.HomeCallbacks?, songInfoRepository: SongInfoRepository) : HomeFragmentViewModel(homeCallbacks, songInfoRepository) {
    val shouldShowErrorSnackbar = ObservableBoolean(false)
    val isLoading = ObservableBoolean(false)

    init {
        update(false)
    }

    fun update(isForceRefresh: Boolean) {
        if (!isLoading.get()) {
            isLoading.set(true)
            songInfoRepository.getCloudSongs(ChangeListener(
                onNext = { refreshAdapterItems(it) },
                onComplete = {
                    isLoading.set(false)
                },
                onError = {
                    shouldShowErrorSnackbar.set(true)
                    isLoading.set(false)
                }), isForceRefresh)
        }
    }

    fun addOrRemoveSongFromDownloaded(id: String) {
        if (songInfoRepository.isSongDownloaded(id)) {
            songInfoRepository.removeSongFromDownloaded(id)
        } else {
            songInfoRepository.addSongToDownloaded(id)
        }
        refreshAdapterItems(adapter.items.map { it.songInfo })
    }

    private fun refreshAdapterItems(newData: List<SongInfo>) {
        val downloadedItems = songInfoRepository.getDownloadedSongs()
        adapter.items = newData.sort().map { songInfo ->
            val isTinted = downloadedItems.contains(songInfo)
            SongInfoViewModel(
                songInfo = songInfo,
                actionDescription = if (isTinted) R.string.cloud_delete_from_downloaded_songs else R.string.cloud_download_song,
                actionIcon = R.drawable.ic_downloaded_24dp,
                isActionTinted = isTinted)
        }
    }
}
