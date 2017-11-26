package com.pandulapeter.campfire.feature.home.shared

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.data.repository.SongInfoRepository

/**
 * Parent class for view models that handle the Fragments that can be seen on the main screen.
 *
 * Handles events and logic for subclasses of [HomeFragment].
 */
abstract class HomeFragmentViewModel(
    private val homeCallbacks: HomeFragment.HomeCallbacks?,
    protected val songInfoRepository: SongInfoRepository) {
    val isLoading = ObservableBoolean(false) //TODO: Subscibe to the repository's loading state.
    val shouldShowErrorSnackbar = ObservableBoolean(false)
    val adapter = SongInfoAdapter()

    abstract protected fun getAdapterItems(): List<SongInfoViewModel>

    open fun updateAdapter() {
        adapter.items = getAdapterItems()
    }

    fun forceRefresh() {
        if (!isLoading.get()) {
            isLoading.set(true)
            songInfoRepository.updateDataSet()
            //TODO: In the callback, set isLoading to false and update the adapter.
        }
    }

    fun showViewOptions() {
        homeCallbacks?.showViewOptions()
    }

    fun addOrRemoveSongFromDownloaded(id: String) {
        if (songInfoRepository.isSongDownloaded(id)) {
            songInfoRepository.removeSongFromDownloaded(id)
        } else {
            songInfoRepository.addSongToDownloaded(id)
        }
        updateAdapter()
    }

    fun addSongToDownloaded(id: String) {
        songInfoRepository.addSongToDownloaded(id)
        updateAdapter()
    }

    fun removeSongFromDownloaded(id: String) {
        songInfoRepository.removeSongFromDownloaded(id)
        updateAdapter()
    }

    fun addOrRemoveSongFromFavorites(id: String) {
        if (songInfoRepository.isSongFavorite(id)) {
            songInfoRepository.removeSongFromFavorites(id)
        } else {
            songInfoRepository.addSongToFavorites(id)
        }
        updateAdapter()
    }

    fun addSongToFavorites(id: String, position: Int? = null) {
        songInfoRepository.addSongToFavorites(id, position)
        updateAdapter()
    }


    fun removeSongFromFavorites(id: String) {
        songInfoRepository.removeSongFromFavorites(id)
        updateAdapter()
    }

    fun swapSongsInFavorites(originalPosition: Int, targetPosition: Int) {
        songInfoRepository.swapSongFavoritesPositions(originalPosition, targetPosition)
        updateAdapter()
    }
}