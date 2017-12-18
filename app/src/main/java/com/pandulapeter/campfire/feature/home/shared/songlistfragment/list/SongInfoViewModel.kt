package com.pandulapeter.campfire.feature.home.shared.songlistfragment.list

import android.support.annotation.DrawableRes
import android.support.annotation.StringRes
import com.pandulapeter.campfire.data.model.SongInfo

/**
 * Wraps all information that needs to appear on a list item.
 */
data class SongInfoViewModel(
    val songInfo: SongInfo,
    val isDownloaded: Boolean,
    @DrawableRes val primaryActionDrawable: Int? = null,
    @StringRes val primaryActionContentDescription: Int? = null,
    @DrawableRes val secondaryActionDrawable: Int? = null,
    @StringRes val secondaryActionContentDescription: Int? = null,
    @StringRes val alertText: Int? = null)