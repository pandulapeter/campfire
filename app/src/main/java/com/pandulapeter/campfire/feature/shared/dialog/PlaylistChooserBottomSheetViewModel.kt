package com.pandulapeter.campfire.feature.shared.dialog

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.databinding.ObservableFloat
import android.databinding.ObservableInt
import android.graphics.Color
import android.support.annotation.ColorInt
import com.pandulapeter.campfire.data.model.Playlist
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.shared.Subscriber
import com.pandulapeter.campfire.data.repository.shared.UpdateType


/**
 * ViewModel for [PlaylistChooserBottomSheetFragment].
 */
class PlaylistChooserBottomSheetViewModel(
    private val songInfoRepository: SongInfoRepository,
    private val playlistRepository: PlaylistRepository,
    val songId: String,
    @ColorInt private val titleCollapsedColor: Int,
    @ColorInt private val titleExpandedColor: Int,
    @ColorInt private val subtitleCollapsedColor: Int,
    @ColorInt private val subtitleExpandedColor: Int,
    private val initialToolbarContainerPadding: Int,
    private val finalToolbarElevation: Int,
    private val finalToolbarMargin: Int
) : Subscriber {
    val songInfo = ObservableField<SongInfo>()
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
    val playlists = ObservableField(listOf<Playlist>())

    override fun onUpdate(updateType: UpdateType) {
        when (updateType) {
            is UpdateType.LibraryCacheUpdated -> songInfo.set(songInfoRepository.getLibrarySongs().first { it.id == songId })
            is UpdateType.PlaylistsUpdated,
            is UpdateType.NewPlaylistsCreated,
            is UpdateType.PlaylistRenamed,
            is UpdateType.PlaylistDeleted -> playlists.set(playlistRepository.getPlaylists())
        }
    }

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