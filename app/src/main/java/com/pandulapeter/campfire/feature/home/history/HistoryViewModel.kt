package com.pandulapeter.campfire.feature.home.history

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.data.repository.HistoryRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.SongListViewModel
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.list.SongInfoViewModel

/**
 * Handles events and logic for [HistoryFragment].
 */
class HistoryViewModel(
    homeCallbacks: HomeFragment.HomeCallbacks?,
    userPreferenceRepository: UserPreferenceRepository,
    songInfoRepository: SongInfoRepository,
    downloadedSongRepository: DownloadedSongRepository,
    playlistRepository: PlaylistRepository,
    private val historyRepository: HistoryRepository) : SongListViewModel(homeCallbacks, userPreferenceRepository, songInfoRepository, downloadedSongRepository, playlistRepository) {
    val shouldShowClearButton = ObservableBoolean(historyRepository.getHistory().isNotEmpty())
    val shouldShowConfirmationDialog = ObservableBoolean()

    override fun getAdapterItems() = historyRepository.getHistory()
        .mapNotNull { songInfoRepository.getSongInfo(it) }
        .filterWorkInProgress()
        .filterExplicit()
        .map { songInfo ->
            val isDownloaded = downloadedSongRepository.isSongDownloaded(songInfo.id)
            SongInfoViewModel(
                songInfo = songInfo,
                isDownloaded = isDownloaded,
                primaryActionDrawable = if (isDownloaded) {
                    if (playlistRepository.isSongInAnyPlaylist(songInfo.id)) R.drawable.ic_playlist_24dp else R.drawable.ic_playlist_border_24dp
                } else {
                    R.drawable.ic_download_24dp
                },
                primaryActionContentDescription = if (isDownloaded) R.string.manage_playlists else R.string.download,
                alertText = if (isDownloaded) {
                    if (downloadedSongRepository.getDownloadedSong(songInfo.id)?.version ?: 0 != songInfo.version ?: 0) R.string.new_version_available else null
                } else {
                    null //TODO: or "new"
                })
        }

    override fun onUpdate(updateType: UpdateType) {
        super.onUpdate(updateType)
        shouldShowClearButton.set(adapter.items.isNotEmpty())
    }

    fun removeSongFromHistory(songId: String) = historyRepository.removeFromHistory(songId)

    fun onClearButtonClicked() = shouldShowConfirmationDialog.set(true)

    fun clearHistory() = historyRepository.clearHistory()
}