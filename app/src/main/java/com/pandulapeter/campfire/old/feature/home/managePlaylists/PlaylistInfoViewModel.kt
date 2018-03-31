package com.pandulapeter.campfire.old.feature.home.managePlaylists

import com.pandulapeter.campfire.old.data.model.Playlist


/**
 * Wraps all information that needs to appear on a single list item.
 */
data class PlaylistInfoViewModel(
    var playlist: Playlist,
    var shouldShowDragHandle: Boolean,
    var itemCount: Int
)