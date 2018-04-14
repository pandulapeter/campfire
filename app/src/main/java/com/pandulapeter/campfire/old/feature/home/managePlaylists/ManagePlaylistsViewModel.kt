package com.pandulapeter.campfire.old.feature.home.managePlaylists

import android.databinding.ObservableBoolean
import android.databinding.ObservableInt
import com.pandulapeter.campfire.feature.home.managePlaylists.PlaylistViewModel
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.old.data.model.Playlist
import com.pandulapeter.campfire.old.data.repository.PlaylistRepository
import com.pandulapeter.campfire.old.data.repository.shared.Subscriber
import com.pandulapeter.campfire.old.data.repository.shared.UpdateType
import com.pandulapeter.campfire.old.feature.home.shared.homeChild.HomeChildViewModel
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.cancel
import java.util.*
import kotlin.coroutines.experimental.CoroutineContext

/**
 * Handles events and logic for [ManagePlaylistsFragment].
 */
class ManagePlaylistsViewModel(
    analyticsManager: AnalyticsManager,
    private val playlistRepository: PlaylistRepository
) : HomeChildViewModel(analyticsManager), Subscriber {
    val adapter = ManagePlaylistsListAdapter()
    val itemCount = ObservableInt(playlistRepository.getPlaylists().size)
    val shouldShowNewPlaylistButton = ObservableBoolean()
    val shouldShowNewPlaylistDialog = ObservableBoolean()
    private var coroutine: CoroutineContext? = null

    override fun onUpdate(updateType: UpdateType) {
        when (updateType) {
            is UpdateType.PlaylistsUpdated,
            is UpdateType.NewPlaylistsCreated,
            is UpdateType.PlaylistRenamed,
            is UpdateType.PlaylistDeleted -> {
                coroutine?.cancel()
                coroutine = async(UI) {
                    adapter.items = async(CommonPool) { getAdapterItems() }.await().toMutableList()
                    itemCount.set(playlistRepository.getPlaylists().size)
                    //TODO: It might be a good idea to show separate hints for rearrange and delete.
//                    shouldShowHintSnackbar.set(firstTimeUserExperienceManager.shouldShowManagePlaylistsHint && itemCount.get() > 2)
                    shouldShowNewPlaylistButton.set(itemCount.get() < Playlist.MAXIMUM_PLAYLIST_COUNT)
                }
            }
        }
    }

    fun onNewPlaylistButtonClicked() = shouldShowNewPlaylistDialog.set(true)

    fun swapSongsInPlaylist(originalPosition: Int, targetPosition: Int) {
        if (originalPosition < targetPosition) {
            for (i in originalPosition until targetPosition) {
                Collections.swap(adapter.items, i, i + 1)
            }
        } else {
            for (i in originalPosition downTo targetPosition + 1) {
                Collections.swap(adapter.items, i, i - 1)
            }
        }
        adapter.notifyItemMoved(originalPosition, targetPosition)
//        playlistRepository.updatePlaylistOrder(adapter.items.map { it.playlist })
    }

    fun deletePlaylist(playlistId: Int) {
        playlistRepository.deletePlaylist(playlistId)
    }

    private fun getAdapterItems(): List<PlaylistViewModel> {
//        val playlists = playlistRepository.getPlaylists()
//        return playlists.map {
//            PlaylistViewModel(
////                playlist = it,
//                shouldShowDragHandle = it.id != Playlist.FAVORITES_ID && playlists.size > 2,
//                itemCount = playlistRepository.getPlaylistSongIds(it.id).size
//            )
//        }
        return listOf()
    }
}