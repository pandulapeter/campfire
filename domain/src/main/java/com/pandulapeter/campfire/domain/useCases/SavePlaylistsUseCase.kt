package com.pandulapeter.campfire.domain.useCases

import com.pandulapeter.campfire.data.model.domain.Playlist
import com.pandulapeter.campfire.data.repository.api.PlaylistRepository

class SavePlaylistsUseCase internal constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(
        playlists: List<Playlist>
    ) = playlistRepository.savePlaylists(playlists)
}