package com.pandulapeter.campfire.feature.home.managePlaylists

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import org.koin.android.ext.android.inject

class ManagePlaylistsViewModel : CampfireViewModel(), PlaylistRepository.Subscriber {

    private val playlistRepository by inject<PlaylistRepository>()
    val adapter = ManagePlaylistsListAdapter()
    val shouldShowDeleteAllButton = ObservableBoolean()

    override fun subscribe() {
        playlistRepository.subscribe(this)
    }

    override fun unsubscribe() {
        playlistRepository.unsubscribe(this)
    }

    override fun onPlaylistsUpdated(playlists: List<Playlist>) {
        adapter.items = playlists.map { PlaylistViewModel(it) }
        shouldShowDeleteAllButton.set(playlists.size > 1)
    }

    fun deleteAllPlaylists() = playlistRepository.deleteAllPlaylists()
}