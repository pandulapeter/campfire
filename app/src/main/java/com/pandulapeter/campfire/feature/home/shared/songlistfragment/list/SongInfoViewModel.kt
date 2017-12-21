package com.pandulapeter.campfire.feature.home.shared.songlistfragment.list

import android.support.annotation.StringRes
import com.pandulapeter.campfire.data.model.SongInfo

/**
 * Wraps all information that needs to appear on a single list item.
 */
data class SongInfoViewModel(
    var songInfo: SongInfo,
    var isSongDownloaded: Boolean,
    var isSongLoading: Boolean,
    var isSongOnAnyPlaylist: Boolean,
    var shouldShowDragHandle: Boolean,
    var shouldShowPlaylistButton: Boolean,
    var shouldShowDownloadButton: Boolean,
    @StringRes var alertText: Int? = null)