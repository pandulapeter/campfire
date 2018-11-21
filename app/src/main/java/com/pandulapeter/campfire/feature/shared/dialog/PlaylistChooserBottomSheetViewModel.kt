package com.pandulapeter.campfire.feature.shared.dialog

import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import androidx.databinding.ObservableFloat
import androidx.databinding.ObservableInt
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongRepository

class PlaylistChooserBottomSheetViewModel(
    val songId: String,
    private val initialToolbarContainerPadding: Int,
    private val finalToolbarElevation: Int,
    private val finalToolbarMargin: Int
) : SongRepository.Subscriber, PlaylistRepository.Subscriber {

    val songInfo = ObservableField<Song>()
    val shouldDismissDialog = ObservableBoolean()
    val shouldShowNewPlaylistDialog = ObservableBoolean()
    val closeAlpha = ObservableFloat()
    val closeTranslation = ObservableFloat()
    val toolbarTranslation = ObservableFloat()
    val appBarElevation = ObservableFloat()
    val containerAlpha = ObservableFloat()
    val containerPadding = ObservableInt(initialToolbarContainerPadding)
    val shouldUpdatePlaylists = ObservableBoolean()

    override fun onSongRepositoryDataUpdated(data: List<Song>) = songInfo.set(data.first { it.id == songId })

    override fun onSongRepositoryLoadingStateChanged(isLoading: Boolean) = Unit

    override fun onSongRepositoryUpdateError() = Unit

    override fun onPlaylistsUpdated(playlists: List<Playlist>) = shouldUpdatePlaylists.set(true)

    override fun onPlaylistOrderChanged(playlists: List<Playlist>) = shouldUpdatePlaylists.set(true)

    override fun onSongAddedToPlaylistForTheFirstTime(songId: String) = Unit

    override fun onSongRemovedFromAllPlaylists(songId: String) = Unit

    fun updateSlideState(slideOffset: Float, scrollViewOffset: Int) = Math.max(0f, 2 * slideOffset - 1).let { closenessToTop ->
        closeAlpha.set(closenessToTop)
        closeTranslation.set(-(1 - closenessToTop) * finalToolbarMargin / 4)
        toolbarTranslation.set(closenessToTop * finalToolbarMargin)
        if (scrollViewOffset == 0) {
            appBarElevation.set(closenessToTop * finalToolbarElevation)
            containerAlpha.set(closenessToTop)
            containerPadding.set(Math.round((1 - closenessToTop) * initialToolbarContainerPadding))
        }
    }

    fun onCloseButtonClicked() = shouldDismissDialog.set(true)

    fun onNewPlaylistButtonClicked() = shouldShowNewPlaylistDialog.set(true)
}