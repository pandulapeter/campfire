package com.pandulapeter.campfire.feature.home.managePlaylists

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.databinding.ObservableInt
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.feature.shared.widget.StateLayout
import org.koin.android.ext.android.inject
import java.util.*

class ManagePlaylistsViewModel : CampfireViewModel(), PlaylistRepository.Subscriber {

    private val playlistRepository by inject<PlaylistRepository>()
    private var playlistToDeleteId: String? = null
    val adapter = ManagePlaylistListAdapter()
    val shouldShowDeleteAllButton = ObservableBoolean()
    val playlistCount = ObservableInt()
    val state = ObservableField<StateLayout.State>(StateLayout.State.LOADING)

    override fun subscribe() = playlistRepository.subscribe(this)

    override fun unsubscribe() = playlistRepository.unsubscribe(this)

    override fun onPlaylistsUpdated(playlists: List<Playlist>) {
        if (playlistRepository.isCacheLoaded()) {
            updateAdapterItems(playlists)
            state.set(StateLayout.State.NORMAL)
        }
    }

    override fun onPlaylistOrderChanged(playlists: List<Playlist>) = Unit

    fun deleteAllPlaylists() = playlistRepository.deleteAllPlaylists()

    fun deletePlaylistTemporarily(playlistId: String) {
        playlistToDeleteId = playlistId
        updateAdapterItems(playlistRepository.cache)
    }

    fun cancelDeletePlaylist() {
        playlistToDeleteId = null
        updateAdapterItems(playlistRepository.cache)
    }

    fun deletePlaylistPermanently() {
        playlistToDeleteId?.let {
            playlistRepository.deletePlaylist(it)
            playlistToDeleteId = null
        }
    }

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
        adapter.items.map { it.playlist }.forEachIndexed { index, playlist -> playlistRepository.updatePlaylistOrder(playlist.id, index) }
    }

    private fun updateAdapterItems(playlists: List<Playlist>) {
        playlists
            .filter { it.id != playlistToDeleteId }
            .sortedBy { it.order }
            .map { PlaylistViewModel(it) }
            .run {
                forEach { it.shouldShowDragHandle = it.playlist.id != Playlist.FAVORITES_ID && size > 2 }
                adapter.items = this
                shouldShowDeleteAllButton.set(size > 1)
                playlistCount.set(size)
            }
    }
}