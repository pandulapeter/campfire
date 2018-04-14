package com.pandulapeter.campfire.feature.home.managePlaylists

import android.databinding.ObservableBoolean
import android.databinding.ObservableInt
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import org.koin.android.ext.android.inject

class ManagePlaylistsViewModel : CampfireViewModel(), PlaylistRepository.Subscriber {

    private val playlistRepository by inject<PlaylistRepository>()
    private var playlistToDeleteId: String? = null
    val adapter = ManagePlaylistListAdapter()
    val shouldShowDeleteAllButton = ObservableBoolean()
    val playlistCount = ObservableInt()

    override fun subscribe() = playlistRepository.subscribe(this)

    override fun unsubscribe() = playlistRepository.unsubscribe(this)

    override fun onPlaylistsUpdated(playlists: List<Playlist>) = updateAdapterItems(playlists)

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

    private fun updateAdapterItems(playlists: List<Playlist>) {
        playlists.filter { it.id != playlistToDeleteId }.map { PlaylistViewModel(it) }.run {
            forEach { it.shouldShowDragHandle = size > 2 }
            adapter.items = this
            shouldShowDeleteAllButton.set(size > 1)
            playlistCount.set(size)
        }
    }
}