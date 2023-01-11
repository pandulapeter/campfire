package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.Playlist

interface SavePlaylistsUseCase {

    suspend operator fun invoke(playlists: List<Playlist>)
}