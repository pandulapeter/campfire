package com.pandulapeter.campfire.data.source.local.implementationDesktop.source

import com.pandulapeter.campfire.data.model.domain.Playlist
import com.pandulapeter.campfire.data.source.local.api.PlaylistLocalSource

internal class PlaylistLocalSourceImpl : PlaylistLocalSource {

    override suspend fun loadPlaylists() = emptyList<Playlist>() // TODO

    override suspend fun savePlaylists(playlists: List<Playlist>) = Unit // TODO
}