package com.pandulapeter.campfire.feature.home.shared

import com.pandulapeter.campfire.data.model.remote.Song

data class SongViewModel(
    val song: Song,
    var isOnAnyPlaylists: Boolean = false,
    var downloadState: DownloadState = if (song.isNew) DownloadState.NotDownloaded.New else DownloadState.NotDownloaded.Old,
    var shouldShowDragHandle: Boolean = false,
    val shouldShowPlaylistButton: Boolean = true
) {
    val alertText
        get() = when (downloadState) {
            DownloadState.Downloaded.Deprecated -> "Update me"
            DownloadState.NotDownloaded.New -> "New"
            else -> null
        }

    sealed class DownloadState {

        sealed class NotDownloaded : DownloadState() {
            object Old : NotDownloaded()
            object New : NotDownloaded()
        }

        object Downloading : DownloadState()

        sealed class Downloaded : DownloadState() {
            object UpToDate : Downloaded()
            object Deprecated : Downloaded()
        }
    }
}