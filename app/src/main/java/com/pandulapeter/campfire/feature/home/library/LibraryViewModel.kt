package com.pandulapeter.campfire.feature.home.library

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.home.shared.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.HomeFragmentViewModel
import com.pandulapeter.campfire.feature.home.shared.SongInfoViewModel
import com.pandulapeter.campfire.util.onPropertyChanged

/**
 * Handles events and logic for [LibraryFragment].
 */
class LibraryViewModel(homeCallbacks: HomeFragment.HomeCallbacks?,
                       songInfoRepository: SongInfoRepository,
                       private val showViewOptionsCallback: () -> Unit) : HomeFragmentViewModel(homeCallbacks, songInfoRepository) {
    val searchInputVisible = ObservableBoolean(songInfoRepository.cloudQuery.isNotEmpty())
    val isSortedByTitle = ObservableBoolean(songInfoRepository.isSortedByTitle)
    val query = ObservableField(songInfoRepository.cloudQuery)

    init {
        searchInputVisible.onPropertyChanged { query.set("") }
        isSortedByTitle.onPropertyChanged { songInfoRepository.isSortedByTitle = it }
        query.onPropertyChanged { songInfoRepository.cloudQuery = it }
    }

    override fun getAdapterItems() = songInfoRepository.getCloudSongs().filter(query.get()).map { songInfo ->
        val isTinted = songInfoRepository.isSongDownloads(songInfo.id)
        SongInfoViewModel(
            songInfo = songInfo,
            actionIcon = R.drawable.ic_downloads_24dp,
            isActionTinted = isTinted)
    }

    fun addOrRemoveSongFromDownloads(id: String) =
        if (songInfoRepository.isSongDownloads(id)) {
            songInfoRepository.removeSongFromDownloads(id)
        } else {
            songInfoRepository.addSongToDownloads(id)
        }

    fun showViewOptions() = showViewOptionsCallback()
}