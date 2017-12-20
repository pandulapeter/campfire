package com.pandulapeter.campfire.feature.home.shared.songlistfragment.list

import android.support.annotation.StringRes
import com.pandulapeter.campfire.data.model.SongInfo

/**
 * Wraps all information that needs to appear on a single list item.
 */
data class SongInfoViewModel(
    val songInfo: SongInfo,
    val isSongDownloaded: Boolean,
    val isSongLoading: Boolean,
    val isSongOnAnyPlaylist: Boolean,
    val shouldShowDragHandle: Boolean,
    val shouldShowPlaylistButton: Boolean,
    val shouldShowDownloadButton: Boolean,
    @StringRes val alertText: Int? = null)