package com.pandulapeter.campfire.feature.home.managePlaylists

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import org.koin.android.ext.android.inject

class ManagePlaylistsViewModel : CampfireViewModel(), PlaylistRepository.Subscriber {

    private val playlistRepository by inject<PlaylistRepository>()
    val adapter = ManagePlaylistsListAdapter()
    private var playlistToDeleteId: String? = null
    val shouldShowDeleteAllButton = ObservableBoolean()

    override fun subscribe() {
        playlistRepository.subscribe(this)
    }

    override fun unsubscribe() {
        playlistRepository.unsubscribe(this)
    }

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
        adapter.items = playlists.filter { it.id != playlistToDeleteId }.map { PlaylistViewModel(it) }
        shouldShowDeleteAllButton.set(playlists.size > 1)
    }
}