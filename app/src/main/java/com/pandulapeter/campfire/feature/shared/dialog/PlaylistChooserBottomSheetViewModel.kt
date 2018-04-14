package com.pandulapeter.campfire.feature.shared.dialog

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.databinding.ObservableFloat
import android.databinding.ObservableInt
import android.graphics.Color
import android.support.annotation.ColorInt
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongRepository

class PlaylistChooserBottomSheetViewModel(
    val songId: String,
    @ColorInt private val titleCollapsedColor: Int,
    @ColorInt private val titleExpandedColor: Int,
    @ColorInt private val subtitleCollapsedColor: Int,
    @ColorInt private val subtitleExpandedColor: Int,
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
    val toolbarTitleColor = ObservableInt(titleExpandedColor)
    val toolbarSubtitleColor = ObservableInt(subtitleExpandedColor)
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
        if (titleCollapsedColor != titleExpandedColor) {
            toolbarTitleColor.set(blendColors(titleCollapsedColor, titleExpandedColor, closenessToTop))
            toolbarSubtitleColor.set(blendColors(subtitleCollapsedColor, subtitleExpandedColor, closenessToTop))
        }
        if (scrollViewOffset == 0) {
            appBarElevation.set(closenessToTop * finalToolbarElevation)
            containerAlpha.set(closenessToTop)
            containerPadding.set(Math.round((1 - closenessToTop) * initialToolbarContainerPadding))
        }
    }

    fun onCloseButtonClicked() = shouldDismissDialog.set(true)

    fun onNewPlaylistButtonClicked() = shouldShowNewPlaylistDialog.set(true)

    @ColorInt
    private fun blendColors(@ColorInt color1: Int, @ColorInt color2: Int, ratio: Float) = (1f - ratio).let { inverseRatio ->
        Color.argb(
            (Color.alpha(color1) * ratio + Color.alpha(color2) * inverseRatio).toInt(),
            (Color.red(color1) * ratio + Color.red(color2) * inverseRatio).toInt(),
            (Color.green(color1) * ratio + Color.green(color2) * inverseRatio).toInt(),
            (Color.blue(color1) * ratio + Color.blue(color2) * inverseRatio).toInt()
        )
    }
}