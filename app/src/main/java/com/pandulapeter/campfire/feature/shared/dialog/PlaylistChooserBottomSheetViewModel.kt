package com.pandulapeter.campfire.feature.shared.dialog

import android.content.Context
import androidx.databinding.ObservableInt
import androidx.lifecycle.MutableLiveData
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.feature.shared.InteractionBlocker
import com.pandulapeter.campfire.util.dimension
import com.pandulapeter.campfire.util.mutableLiveDataOf

class PlaylistChooserBottomSheetViewModel(
    val songId: String,
    context: Context,
    interactionBlocker: InteractionBlocker
) : CampfireViewModel(interactionBlocker), SongRepository.Subscriber, PlaylistRepository.Subscriber {

    val songInfo = MutableLiveData<Song>()
    val shouldDismissDialog = MutableLiveData<Boolean?>()
    val shouldShowNewPlaylistDialog = MutableLiveData<Boolean?>()
    val closeAlpha = mutableLiveDataOf(0f)
    val closeTranslation = mutableLiveDataOf(0f)
    val toolbarTranslation = mutableLiveDataOf(0f)
    val appBarElevation = mutableLiveDataOf(0f)
    val containerAlpha = mutableLiveDataOf(0f)
    val shouldUpdatePlaylists = MutableLiveData<Boolean?>()
    private val initialToolbarContainerPadding = context.dimension(R.dimen.content_padding)
    private val finalToolbarElevation = context.dimension(R.dimen.bottom_sheet_toolbar_elevation)
    private val finalToolbarMargin = context.dimension(R.dimen.bottom_sheet_toolbar_margin)
    val containerPadding = ObservableInt(initialToolbarContainerPadding)

    override fun onSongRepositoryDataUpdated(data: List<Song>) {
        songInfo.value = data.first { it.id == songId }
    }

    override fun onSongRepositoryLoadingStateChanged(isLoading: Boolean) = Unit

    override fun onSongRepositoryUpdateError() = Unit

    override fun onPlaylistsUpdated(playlists: List<Playlist>) {
        shouldUpdatePlaylists.value = true
    }

    override fun onPlaylistOrderChanged(playlists: List<Playlist>) {
        shouldUpdatePlaylists.value = true
    }

    override fun onSongAddedToPlaylistForTheFirstTime(songId: String) = Unit

    override fun onSongRemovedFromAllPlaylists(songId: String) = Unit

    fun updateSlideState(slideOffset: Float, scrollViewOffset: Int) = Math.max(0f, 2 * slideOffset - 1).let { closenessToTop ->
        closeAlpha.value = closenessToTop
        closeTranslation.value = -(1 - closenessToTop) * finalToolbarMargin / 4
        toolbarTranslation.value = closenessToTop * finalToolbarMargin
        if (scrollViewOffset == 0) {
            appBarElevation.value = closenessToTop * finalToolbarElevation
            containerAlpha.value = closenessToTop
            containerPadding.set(Math.round((1 - closenessToTop) * initialToolbarContainerPadding))
        }
    }

    fun onCloseButtonClicked() {
        shouldDismissDialog.value = true
    }

    fun onNewPlaylistButtonClicked() {
        shouldShowNewPlaylistDialog.value = true
    }
}