package com.pandulapeter.campfire.feature.home.library

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.repository.ChangeListener
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.home.shared.SongInfoAdapter
import com.pandulapeter.campfire.feature.home.shared.SongInfoViewModel

/**
 * Handles events and logic for [LibraryFragment].
 */
class LibraryViewModel(private val songInfoRepository: SongInfoRepository) {

    val adapter = SongInfoAdapter()
    val shouldShowErrorSnackbar = ObservableBoolean(false)
    val isLoading = ObservableBoolean(false)

    init {
        update(false)
    }

    fun update(isForceRefresh: Boolean) {
        if (!isLoading.get()) {
            isLoading.set(true)
            songInfoRepository.getLibrary(ChangeListener(
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

    fun addOrRemoveSongFromDownloaded(songInfo: SongInfo) {
        if (songInfoRepository.getDownloaded().contains(songInfo)) {
            songInfoRepository.removeSongFromDownloaded(songInfo)
        } else {
            songInfoRepository.addSongToDownloaded(songInfo)
        }
        refreshAdapterItems(adapter.items.map { it.songInfo })
    }

    private fun refreshAdapterItems(newData: List<SongInfo>) {
        val downloadedItems = songInfoRepository.getDownloaded()
        adapter.items = newData.map { SongInfoViewModel(it, downloadedItems.contains(it)) }
    }
}
