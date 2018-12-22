package com.pandulapeter.campfire.feature.detail

import androidx.lifecycle.MutableLiveData
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.feature.shared.InteractionBlocker
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.mutableLiveDataOf

class DetailViewModel(
    private val playlistRepository: PlaylistRepository,
    private val analyticsManager: AnalyticsManager,
    interactionBlocker: InteractionBlocker
) : CampfireViewModel(interactionBlocker), PlaylistRepository.Subscriber {

    val shouldUpdatePlaylistIcon = MutableLiveData<Boolean?>()
    val songId = mutableLiveDataOf("")

    init {
        songId.observeForever { updatePlaylistIconState() }
    }

    override fun subscribe() = playlistRepository.subscribe(this)

    override fun unsubscribe() = playlistRepository.unsubscribe(this)

    override fun onPlaylistsUpdated(playlists: List<Playlist>) = updatePlaylistIconState()

    override fun onPlaylistOrderChanged(playlists: List<Playlist>) = updatePlaylistIconState()

    override fun onSongAddedToPlaylistForTheFirstTime(songId: String) {
        if (songId == this.songId.value) {
            updatePlaylistIconState()
        }
    }

    override fun onSongRemovedFromAllPlaylists(songId: String) {
        if (songId == this.songId.value) {
            updatePlaylistIconState()
        }
    }

    fun areThereMoreThanOnePlaylists() = playlistRepository.cache.size > 1

    fun toggleFavoritesState() {
        songId.value?.also {
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

    fun isSongInAnyPlaylists() = songId.value?.let { playlistRepository.isSongInAnyPlaylist(it) } ?: false

    private fun updatePlaylistIconState() {
        songId.value?.let { shouldUpdatePlaylistIcon.value = playlistRepository.isSongInAnyPlaylist(it) }
    }
}