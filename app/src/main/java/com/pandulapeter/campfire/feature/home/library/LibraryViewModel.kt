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
    val searchInputVisible = ObservableBoolean(songInfoRepository.query.isNotEmpty())
    val isLoading = ObservableBoolean(songInfoRepository.isLoading)
    val isSortedByTitle = ObservableBoolean(songInfoRepository.isSortedByTitle)
    val shouldShowDownloadedOnly = ObservableBoolean(songInfoRepository.shouldShowDownloadedOnly)
    val query = ObservableField(songInfoRepository.query)
    val shouldShowErrorSnackbar = ObservableBoolean(false)

    init {
        searchInputVisible.onPropertyChanged { query.set("") }
        isSortedByTitle.onPropertyChanged { songInfoRepository.isSortedByTitle = it }
        shouldShowDownloadedOnly.onPropertyChanged { songInfoRepository.shouldShowDownloadedOnly = it }
        query.onPropertyChanged { songInfoRepository.query = it }
    }

    override fun getAdapterItems(): List<SongInfoViewModel> {
        val downloadedSongs = songInfoRepository.getDownloadedSongs()
        val downloadedSongIds = downloadedSongs.map { it.id }
        return songInfoRepository.getLibrarySongs().filter(query.get()).map { songInfo ->
            SongInfoViewModel(
                songInfo,
                downloadedSongIds.contains(songInfo.id),
                downloadedSongs.firstOrNull { songInfo.id == it.id }?.version?.compareTo(songInfo.version) ?: 0 < 0)
        }
    }

    override fun onUpdate() {
        super.onUpdate()
        isLoading.set(songInfoRepository.isLoading)
    }

    fun forceRefresh() = songInfoRepository.updateDataSet { shouldShowErrorSnackbar.set(true) }

    fun addSongToFavorites(id: String, position: Int? = null) = songInfoRepository.addSongToFavorites(id, position)

    fun addOrRemoveSongFromDownloads(songInfo: SongInfo) =
        if (songInfoRepository.isSongDownloaded(songInfo.id)) {
            songInfoRepository.removeSongFromDownloads(songInfo.id)
        } else {
            songInfoRepository.addSongToDownloads(songInfo)
        }

    fun showViewOptions() = showViewOptionsCallback()


    //TODO: Handle special characters, prioritize results that begin with the query.
    private fun List<SongInfo>.filter(query: String) = filter {
        it.title.contains(query, true) || it.artist.contains(query, true)
    }
}