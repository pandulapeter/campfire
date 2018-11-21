package com.pandulapeter.campfire.feature.detail

import androidx.databinding.ObservableField
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.onPropertyChanged
import org.koin.android.ext.android.inject

class DetailViewModel(private val updatePlaylistIcon: (Boolean) -> Unit) : CampfireViewModel(), PlaylistRepository.Subscriber {

    private val playlistRepository by inject<PlaylistRepository>()
    private val analyticsManager by inject<AnalyticsManager>()
    val songId = ObservableField("")

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
        songId.get()?.also {
            if (playlistRepository.isSongInPlaylist(Playlist.FAVORITES_ID, it)) {
                analyticsManager.onSongPlaylistStateChanged(
                    it,
                    playlistRepository.getPlaylistCountForSong(it) - 1,
                    AnalyticsManager.PARAM_VALUE_SCREEN_SONG_DETAIL,
                    playlistRepository.cache.size > 1
                )
                playlistRepository.removeSongFromPlaylist(Playlist.FAVORITES_ID, it)
            } else {
                analyticsManager.onSongPlaylistStateChanged(
                    it, playlistRepository.getPlaylistCountForSong(it) + 1,
                    AnalyticsManager.PARAM_VALUE_SCREEN_SONG_DETAIL,
                    playlistRepository.cache.size > 1
                )
                playlistRepository.addSongToPlaylist(Playlist.FAVORITES_ID, it)
            }
        }
    }

    fun isSongInAnyPlaylists() = songId.get()?.let { playlistRepository.isSongInAnyPlaylist(it) } ?: false

    private fun updatePlaylistIconState() {
        songId.get()?.let { updatePlaylistIcon(playlistRepository.isSongInAnyPlaylist(it)) }
    }
}