package com.pandulapeter.campfire.feature.home.shared

import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.repository.SongDetailRepository

data class SongViewModel(
    private val songDetailRepository: SongDetailRepository,
    val song: Song,
    var isOnAnyPlaylists: Boolean = false,
    var shouldShowDragHandle: Boolean = false,
    val shouldShowPlaylistButton: Boolean = true,
    var downloadState: DownloadState = when {
        songDetailRepository.isSongDownloading(song.id) -> DownloadState.Downloading
        songDetailRepository.isSongDownloaded(song.id) -> if (songDetailRepository.getSongVersion(song.id) != song.version ?: 0) DownloadState.Downloaded.Deprecated else DownloadState.Downloaded.UpToDate
        else -> if (song.isNew) DownloadState.NotDownloaded.New else DownloadState.NotDownloaded.Old
    }
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