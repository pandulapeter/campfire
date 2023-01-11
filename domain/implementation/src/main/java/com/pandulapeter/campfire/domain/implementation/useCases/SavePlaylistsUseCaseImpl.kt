package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.domain.Playlist
import com.pandulapeter.campfire.data.repository.api.PlaylistRepository
import com.pandulapeter.campfire.domain.api.useCases.SavePlaylistsUseCase

class SavePlaylistsUseCaseImpl internal constructor(
    private val playlistRepository: PlaylistRepository
) : SavePlaylistsUseCase {

    override suspend operator fun invoke(playlists: List<Playlist>) = playlistRepository.savePlaylists(playlists)
}