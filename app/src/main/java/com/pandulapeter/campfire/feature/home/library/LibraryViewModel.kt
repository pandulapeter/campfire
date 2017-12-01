package com.pandulapeter.campfire.feature.home.library

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.SongListViewModel
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.list.SongInfoViewModel
import com.pandulapeter.campfire.util.onPropertyChanged

/**
 * Handles events and logic for [LibraryFragment].
 */
class LibraryViewModel(homeCallbacks: HomeFragment.HomeCallbacks?,
                       songInfoRepository: SongInfoRepository,
                       private val showViewOptionsCallback: () -> Unit) : SongListViewModel(homeCallbacks, songInfoRepository) {
    val searchInputVisible = ObservableBoolean(songInfoRepository.cloudQuery.isNotEmpty())
    val isLoading = ObservableBoolean(songInfoRepository.isLoading)
    val isSortedByTitle = ObservableBoolean(songInfoRepository.isSortedByTitle)
    val query = ObservableField(songInfoRepository.cloudQuery)
    val shouldShowErrorSnackbar = ObservableBoolean(false)

    init {
        searchInputVisible.onPropertyChanged { query.set("") }
        isSortedByTitle.onPropertyChanged { songInfoRepository.isSortedByTitle = it }
        query.onPropertyChanged { songInfoRepository.cloudQuery = it }
    }

    override fun getAdapterItems(): List<SongInfoViewModel> {
        val downloadedSongIds = songInfoRepository.getDownloadsSongs().map { it.id }
        return songInfoRepository.getCloudSongs().filter(query.get()).map { SongInfoViewModel(it, downloadedSongIds.contains(it.id)) }
    }

    override fun onUpdate() {
        super.onUpdate()
        isLoading.set(songInfoRepository.isLoading)
    }

    fun forceRefresh() = songInfoRepository.updateDataSet { shouldShowErrorSnackbar.set(true) }

    fun addSongToFavorites(id: String, position: Int? = null) = songInfoRepository.addSongToFavorites(id, position)

    fun addOrRemoveSongFromDownloads(id: String) =
        if (songInfoRepository.isSongDownloads(id)) {
            songInfoRepository.removeSongFromDownloads(id)
        } else {
            songInfoRepository.addSongToDownloads(id)
        }

    fun showViewOptions() = showViewOptionsCallback()


    //TODO: Handle special characters, prioritize results that begin with the query.
    private fun List<SongInfo>.filter(query: String) = filter {
        it.title.contains(query, true) || it.artist.contains(query, true)
    }
}