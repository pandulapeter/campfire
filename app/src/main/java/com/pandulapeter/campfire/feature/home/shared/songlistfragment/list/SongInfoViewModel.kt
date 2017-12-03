package com.pandulapeter.campfire.feature.home.shared.songlistfragment.list

import com.pandulapeter.campfire.data.model.SongInfo

/**
 * Wraps all information that needs to appear on a list item.
 */
data class SongInfoViewModel(
    val songInfo: SongInfo,
    val shouldShowAction: Boolean,
    val isDownloaded: Boolean,
    val shouldBeUpdated: Boolean)