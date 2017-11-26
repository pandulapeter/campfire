package com.pandulapeter.campfire.feature.home.shared

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.Subscriber
import com.pandulapeter.campfire.util.onPropertyChanged

/**
 * Parent class for view models that handle the Fragments that can be seen on the main screen.
 *
 * Handles events and logic for subclasses of [HomeFragment].
 */
abstract class HomeFragmentViewModel(
    private val homeCallbacks: HomeFragment.HomeCallbacks?,
    protected val songInfoRepository: SongInfoRepository) : Subscriber {
    val isLoading = ObservableBoolean(songInfoRepository.isLoading)
    val shouldShowErrorSnackbar = ObservableBoolean(false)
    val searchInputVisible = ObservableBoolean(false)
    val query = ObservableField("")
    val adapter = SongInfoAdapter()

    init {
        searchInputVisible.onPropertyChanged { onUpdate() }
        query.onPropertyChanged { onUpdate() }
    }

    override fun onUpdate() {
        adapter.items = getAdapterItems()
        isLoading.set(songInfoRepository.isLoading)
    }

    abstract protected fun getAdapterItems(): List<SongInfoViewModel>

    fun forceRefresh() = songInfoRepository.updateDataSet { shouldShowErrorSnackbar.set(true) }

    fun showViewOptions() {
        homeCallbacks?.showViewOptions()
    }

    fun addSongToFavorites(id: String, position: Int? = null) = songInfoRepository.addSongToFavorites(id, position)

    //TODO: Handle special characters, prioritize results that begin with the query.
    protected fun List<SongInfo>.filter(query: String) =
        if (searchInputVisible.get()) filter {
            it.title.contains(query, true) || it.artist.contains(query, true)
        } else this
}