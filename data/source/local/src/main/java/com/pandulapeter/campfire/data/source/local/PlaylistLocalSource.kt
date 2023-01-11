package com.pandulapeter.campfire.data.source.local

import com.pandulapeter.campfire.data.model.domain.Playlist

interface PlaylistLocalSource {

    suspend fun loadPlaylists(): List<Playlist>

    suspend fun savePlaylists(playlists: List<Playlist>)
}