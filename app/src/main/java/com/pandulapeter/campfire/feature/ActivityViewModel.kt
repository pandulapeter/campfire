package com.pandulapeter.campfire.feature

import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.feature.shared.InteractionBlocker
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.util.mutableLiveDataOf

class ActivityViewModel(
    val analyticsManager: AnalyticsManager,
    val appShortcutManager: AppShortcutManager,
    val preferenceDatabase: PreferenceDatabase,
    val playlistRepository: PlaylistRepository,
    interactionBlocker: InteractionBlocker
) : CampfireViewModel(interactionBlocker), PlaylistRepository.Subscriber {

    val playlists = mutableLiveDataOf(listOf<Playlist>())

    override fun subscribe() = playlistRepository.subscribe(this)

    override fun unsubscribe() = playlistRepository.unsubscribe(this)

    override fun onPlaylistsUpdated(playlists: List<Playlist>) {
        this.playlists.value = playlists
    }

    override fun onPlaylistOrderChanged(playlists: List<Playlist>) {
        this.playlists.value = playlists
    }

    override fun onSongAddedToPlaylistForTheFirstTime(songId: String) = Unit

    override fun onSongRemovedFromAllPlaylists(songId: String) = Unit
}