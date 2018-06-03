package com.pandulapeter.campfire.feature.main.managePlaylists

import com.pandulapeter.campfire.data.model.local.Playlist

data class PlaylistViewModel(
    var playlist: Playlist,
    var shouldShowDragHandle: Boolean = false
) {
    var itemCount = playlist.songIds.size
}