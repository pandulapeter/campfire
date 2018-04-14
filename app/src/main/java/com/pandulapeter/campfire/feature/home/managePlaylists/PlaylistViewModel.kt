package com.pandulapeter.campfire.feature.home.managePlaylists

import com.pandulapeter.campfire.data.model.local.Playlist

data class PlaylistViewModel(
    var playlist: Playlist,
    var shouldShowDragHandle: Boolean = false
) {
    var itemCount = playlist.songIds.size
}