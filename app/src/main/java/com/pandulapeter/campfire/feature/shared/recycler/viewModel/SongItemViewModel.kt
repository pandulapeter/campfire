package com.pandulapeter.campfire.feature.shared.recycler.viewModel

import android.content.Context
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongDetailRepository

data class SongItemViewModel(
    private val context: Context,
    private val songDetailRepository: SongDetailRepository,
    private val playlistRepository: PlaylistRepository,
    val song: Song,
    var shouldShowDragHandle: Boolean = false,
    val shouldShowPlaylistButton: Boolean = true,
    var downloadState: DownloadState = when {
        songDetailRepository.isSongDownloading(song.id) -> DownloadState.Downloading
        songDetailRepository.isSongDownloaded(song.id) -> if (songDetailRepository.getSongVersion(song.id) != song.version ?: 0) DownloadState.Downloaded.Deprecated else DownloadState.Downloaded.UpToDate
        else -> if (song.isNew) DownloadState.NotDownloaded.New else DownloadState.NotDownloaded.Old
    },
    var isOnAnyPlaylists: Boolean = playlistRepository.isSongInAnyPlaylist(song.id)
) : ItemViewModel {
    val alertText
        get() = when (downloadState) {
            DownloadState.Downloaded.Deprecated -> context.getString(R.string.new_version_available)
            DownloadState.NotDownloaded.New -> context.getString(R.string.new_tag)
            else -> null
        }

    override fun getItemId() = song.id.hashCode().toLong()

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