package com.pandulapeter.campfire.feature.detail

import android.databinding.ObservableField
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.util.onPropertyChanged
import org.koin.android.ext.android.inject

class DetailViewModel(id: String, private val updatePlaylistIcon: (Boolean) -> Unit) : CampfireViewModel(), PlaylistRepository.Subscriber {

    private val playlistRepository by inject<PlaylistRepository>()
    val songId = ObservableField(id)

    init {
        songId.onPropertyChanged { updatePlaylistIconState() }
    }

    override fun subscribe() = playlistRepository.subscribe(this)

    override fun unsubscribe() = playlistRepository.unsubscribe(this)

    override fun onPlaylistsUpdated(playlists: List<Playlist>) = updatePlaylistIconState()

    override fun onPlaylistOrderChanged(playlists: List<Playlist>) = updatePlaylistIconState()

    override fun onSongAddedToPlaylistForTheFirstTime(songId: String) {
        if (songId == this.songId.get()) {
            updatePlaylistIconState()
        }
    }

    override fun onSongRemovedFromAllPlaylists(songId: String) {
        if (songId == this.songId.get()) {
            updatePlaylistIconState()
        }
    }

    fun areThereMoreThanOnePlaylists() = playlistRepository.cache.size > 1

    fun toggleFavoritesState() {
        songId.get()?.let {
            if (playlistRepository.isSongInPlaylist(Playlist.FAVORITES_ID, it)) {
                playlistRepository.removeSongFromPlaylist(Playlist.FAVORITES_ID, it)
            } else {
                playlistRepository.addSongToPlaylist(Playlist.FAVORITES_ID, it)
            }
        }
    }

    fun isSongInAnyPlaylists() = songId.get()?.let { playlistRepository.isSongInAnyPlaylist(it) } ?: false

    private fun updatePlaylistIconState() {
        songId.get()?.let { updatePlaylistIcon(playlistRepository.isSongInAnyPlaylist(it)) }
    }
}