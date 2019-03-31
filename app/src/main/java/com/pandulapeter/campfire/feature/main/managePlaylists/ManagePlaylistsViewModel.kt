package com.pandulapeter.campfire.feature.main.managePlaylists

import androidx.lifecycle.MutableLiveData
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.feature.CampfireActivity
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.feature.shared.InteractionBlocker
import com.pandulapeter.campfire.feature.shared.widget.StateLayout
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.util.mutableLiveDataOf
import java.util.Collections

class ManagePlaylistsViewModel(
    private val playlistRepository: PlaylistRepository,
    private val appShortcutManager: AppShortcutManager,
    interactionBlocker: InteractionBlocker,
    preferenceDatabase: PreferenceDatabase
) : CampfireViewModel(interactionBlocker), PlaylistRepository.Subscriber {

    private var playlistToDeleteId: String? = null
        set(value) {
            field = value
            playlistRepository.hiddenPlaylistId = value
        }
    val shouldShowDeleteAllButton = mutableLiveDataOf(false)
    val playlistCount = mutableLiveDataOf(0)
    val state = mutableLiveDataOf(StateLayout.State.LOADING)
    val items = mutableLiveDataOf(emptyList<PlaylistViewModel>())
    val moveEvent = MutableLiveData<Pair<Int, Int>?>()

    init {
        preferenceDatabase.lastScreen = CampfireActivity.SCREEN_MANAGE_PLAYLISTS
    }

    override fun subscribe() = playlistRepository.subscribe(this)

    override fun unsubscribe() = playlistRepository.unsubscribe(this)

    override fun onPlaylistsUpdated(playlists: List<Playlist>) {
        if (playlistRepository.isCacheLoaded()) {
            updateAdapterItems(playlists.sortedBy { it.order })
            state.value = StateLayout.State.NORMAL
        }
    }

    override fun onPlaylistOrderChanged(playlists: List<Playlist>) = Unit

    override fun onSongAddedToPlaylistForTheFirstTime(songId: String) = Unit

    override fun onSongRemovedFromAllPlaylists(songId: String) = Unit

    fun deleteAllPlaylists() = playlistRepository.deleteAllPlaylists()

    fun deletePlaylistTemporarily(playlistId: String) {
        playlistToDeleteId = playlistId
        updateAdapterItems(playlistRepository.cache)
    }

    fun cancelDeletePlaylist() {
        playlistToDeleteId = null
        updateAdapterItems(playlistRepository.cache)
    }

    fun hasPlaylistToDelete() = playlistToDeleteId != null

    fun deletePlaylistPermanently() {
        playlistToDeleteId?.let {
            playlistRepository.deletePlaylist(it)
            appShortcutManager.onPlaylistDeleted(it)
            playlistToDeleteId = null
        }
    }

    fun swapSongsInPlaylist(originalPosition: Int, targetPosition: Int) {
        items.value.orEmpty().let { items ->
            if (originalPosition < targetPosition) {
                for (i in originalPosition until targetPosition) {
                    Collections.swap(items, i, i + 1)
                }
            } else {
                for (i in originalPosition downTo targetPosition + 1) {
                    Collections.swap(items, i, i - 1)
                }
            }
            moveEvent.value = originalPosition to targetPosition
            items.map { it.playlist }.forEachIndexed { index, playlist -> playlistRepository.updatePlaylistOrder(playlist.id, index) }
        }
    }

    private fun updateAdapterItems(playlists: List<Playlist>) {
        playlists
            .filter { it.id != playlistToDeleteId }
            .sortedBy { it.order }
            .map { PlaylistViewModel(it) }
            .run {
                forEach { it.shouldShowDragHandle = it.playlist.id != Playlist.FAVORITES_ID && size > 2 }
                items.value = this
                shouldShowDeleteAllButton.value = size > 1
                playlistCount.value = size
            }
    }
}