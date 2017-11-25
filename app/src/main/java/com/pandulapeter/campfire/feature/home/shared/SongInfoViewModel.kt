package com.pandulapeter.campfire.feature.home.shared

import com.pandulapeter.campfire.data.model.SongInfo

/**
 * Wraps all information that needs to appear on a list item.
 */
data class SongInfoViewModel(
    val songInfo: SongInfo,
    val isActionTinted: Boolean
)