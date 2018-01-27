package com.pandulapeter.campfire.feature.home.managePlaylists

import com.pandulapeter.campfire.data.model.Playlist


/**
 * Wraps all information that needs to appear on a single list item.
 */
data class PlaylistInfoViewModel(
    var playlist: Playlist,
    var shouldShowDragHandle: Boolean,
    var itemCount: Int
)