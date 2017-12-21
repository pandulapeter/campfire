package com.pandulapeter.campfire.feature.home.managedownloads

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.SongListViewModel
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.list.SongInfoViewModel

/**
 * Handles events and logic for [ManageDownloadsFragment].
 */
class ManageDownloadsViewModel(
    homeCallbacks: HomeFragment.HomeCallbacks?,
    userPreferenceRepository: UserPreferenceRepository,
    songInfoRepository: SongInfoRepository,
    downloadedSongRepository: DownloadedSongRepository,
    playlistRepository: PlaylistRepository) : SongListViewModel(homeCallbacks, userPreferenceRepository, songInfoRepository, downloadedSongRepository, playlistRepository) {
    val shouldShowDeleteAllButton = ObservableBoolean(downloadedSongRepository.getDownloadedSongIds().isNotEmpty())
    val shouldShowConfirmationDialog = ObservableBoolean()

    //TODO: Find a meaningful way to sort these items.
    override fun getAdapterItems() = downloadedSongRepository.getDownloadedSongIds()
        .mapNotNull { songInfoRepository.getSongInfo(it) }
        .filterWorkInProgress()
        .filterExplicit()

    override fun onUpdateDone(items: List<SongInfoViewModel>) {
        super.onUpdateDone(items)
        shouldShowDeleteAllButton.set(items.isNotEmpty())
    }

    fun onDeleteAllButtonClicked() = shouldShowConfirmationDialog.set(true)

    fun removeSongFromDownloads(songId: String) = downloadedSongRepository.removeSongFromDownloads(songId)

    fun deleteAllDownloads() = downloadedSongRepository.clearDownloads()
}