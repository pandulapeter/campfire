package com.pandulapeter.campfire.feature.home.managePlaylists

import android.databinding.ObservableBoolean
import android.databinding.ObservableInt
import com.pandulapeter.campfire.data.model.Playlist
import com.pandulapeter.campfire.data.repository.FirstTimeUserExperienceRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.shared.Subscriber
import com.pandulapeter.campfire.data.repository.shared.UpdateType
import com.pandulapeter.campfire.feature.home.shared.homeChild.HomeChildViewModel
import com.pandulapeter.campfire.networking.AnalyticsManager
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.cancel
import kotlin.coroutines.experimental.CoroutineContext

/**
 * Handles events and logic for [ManagePlaylistsFragment].
 */
class ManagePlaylistsViewModel(
    analyticsManager: AnalyticsManager,
    private val firstTimeUserExperienceRepository: FirstTimeUserExperienceRepository,
    private val playlistRepository: PlaylistRepository
) : HomeChildViewModel(analyticsManager), Subscriber {
    val adapter = ManagePlaylistsListAdapter()
    val shouldShowHintSnackbar = ObservableBoolean()
    val itemCount = ObservableInt(playlistRepository.getPlaylists().size)
    val shouldShowNewPlaylistButton = ObservableBoolean()
    val shouldShowNewPlaylistDialog = ObservableBoolean()
    private var coroutine: CoroutineContext? = null

    override fun onUpdate(updateType: UpdateType) {
        when (updateType) {
            is UpdateType.PlaylistsUpdated,
            is UpdateType.NewPlaylistsCreated, //TODO: Optimize by events.
            is UpdateType.PlaylistRenamed,
            is UpdateType.PlaylistDeleted -> {
                coroutine?.cancel()
                coroutine = async(UI) {
                    adapter.items = async(CommonPool) { getAdapterItems() }.await().toMutableList()
                    shouldShowHintSnackbar.set(firstTimeUserExperienceRepository.shouldShowManagePlaylistsHint)
                    itemCount.set(playlistRepository.getPlaylists().size)
                    shouldShowNewPlaylistButton.set(true)
                }
            }
        }
    }

    fun onNewPlaylistButtonClicked() = shouldShowNewPlaylistDialog.set(true)

    private fun getAdapterItems() = playlistRepository.getPlaylists().map {
        PlaylistInfoViewModel(
            playlist = it,
            shouldShowDragHandle = it.id != Playlist.FAVORITES_ID,
            itemCount = playlistRepository.getPlaylistSongIds(it.id).size
        )
    }
}