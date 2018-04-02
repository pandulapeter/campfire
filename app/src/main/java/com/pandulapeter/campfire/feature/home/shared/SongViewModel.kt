package com.pandulapeter.campfire.feature.home.shared

import com.pandulapeter.campfire.data.model.remote.Song

data class SongViewModel(
    val song: Song,
    var isOnAnyPlaylists: Boolean = false,
    var downloadState: DownloadState = DownloadState.NotDownloaded,
    var shouldShowDragHandle: Boolean = false,
    val shouldShowPlaylistButton: Boolean = true
) {
    val alertText get() = if (downloadState == DownloadState.Downloaded.Deprecated) "Update me" else null

    sealed class DownloadState {

        object NotDownloaded : DownloadState()

        object Downloading : DownloadState()

        sealed class Downloaded : DownloadState() {
            object UpToDate : Downloaded()
            object Deprecated : Downloaded()
        }
    }
}