package com.pandulapeter.campfire.feature.home.shared

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.Subscriber

/**
 * Parent class for view models that handle the Fragments that can be seen on the main screen.
 *
 * Handles events and logic for subclasses of [HomeFragment].
 */
abstract class HomeFragmentViewModel(
    private val homeCallbacks: HomeFragment.HomeCallbacks?,
    protected val songInfoRepository: SongInfoRepository) : Subscriber {
    val isLoading = ObservableBoolean(false)
    val shouldShowErrorSnackbar = ObservableBoolean(false)
    val adapter = SongInfoAdapter()

    override fun onUpdate() {
        adapter.items = getAdapterItems()
        isLoading.set(songInfoRepository.isLoading)
    }

    abstract protected fun getAdapterItems(): List<SongInfoViewModel>

    fun forceRefresh() = songInfoRepository.updateDataSet()

    fun showViewOptions() {
        homeCallbacks?.showViewOptions()
    }

    fun addSongToFavorites(id: String, position: Int? = null) = songInfoRepository.addSongToFavorites(id, position)
}