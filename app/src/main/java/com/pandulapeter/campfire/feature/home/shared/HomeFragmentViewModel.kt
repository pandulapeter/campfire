package com.pandulapeter.campfire.feature.home.shared

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.Subscriber
import com.pandulapeter.campfire.feature.shared.CampfireViewModel

/**
 * Parent class for view models that handle the Fragments that can be seen on the main screen.
 *
 * Handles events and logic for subclasses of [HomeFragment].
 */
abstract class HomeFragmentViewModel(
    private val homeCallbacks: HomeFragment.HomeCallbacks?,
    protected val songInfoRepository: SongInfoRepository) : CampfireViewModel(), Subscriber {
    val isLoading = ObservableBoolean(songInfoRepository.isLoading)
    val shouldShowErrorSnackbar = ObservableBoolean(false)
    val adapter = SongInfoAdapter()

    override fun onUpdate() {
        adapter.items = getAdapterItems()
        isLoading.set(songInfoRepository.isLoading)
    }

    abstract fun getAdapterItems(): List<SongInfoViewModel>

    fun forceRefresh() = songInfoRepository.updateDataSet { shouldShowErrorSnackbar.set(true) }

    fun showMenu() {
        homeCallbacks?.showMenu()
    }

    fun showViewOptions() {
        homeCallbacks?.showViewOptions()
    }

    fun addSongToFavorites(id: String, position: Int? = null) = songInfoRepository.addSongToFavorites(id, position)

    //TODO: Handle special characters, prioritize results that begin with the query.
    protected fun List<SongInfo>.filter(query: String) = filter {
        it.title.contains(query, true) || it.artist.contains(query, true)
    }
}