package com.pandulapeter.campfire.old.feature.home.shared.songInfoList

import com.pandulapeter.campfire.old.data.model.SongInfo
import com.pandulapeter.campfire.old.data.repository.DownloadedSongRepository

/**
 * Wraps all information that needs to appear on a single list item.
 */
data class SongInfoViewModel(
    var songInfo: SongInfo,
    var downloadState: DownloadedSongRepository.DownloadState,
    var isSongOnAnyPlaylist: Boolean,
    var shouldShowPlaylistButton: Boolean = true,
    var shouldShowDragHandle: Boolean = false,
    private val updateText: String? = null,
    private val newText: String? = null,
    private var text: String? = null
) {
    val alertText
        get() = text ?: when (downloadState) {
            DownloadedSongRepository.DownloadState.Downloaded.Deprecated -> updateText
            DownloadedSongRepository.DownloadState.NotDownloaded.New -> newText
            else -> null
        }
}