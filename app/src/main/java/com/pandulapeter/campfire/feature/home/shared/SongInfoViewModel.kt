package com.pandulapeter.campfire.feature.home.shared

import android.support.annotation.DrawableRes
import android.support.annotation.StringRes
import com.pandulapeter.campfire.data.model.SongInfo

/**
 * Wraps all information that needs to appear on a list item.
 */
data class SongInfoViewModel(
    val songInfo: SongInfo,
    @StringRes val actionDescription: Int,
    @DrawableRes val actionIcon: Int,
    val isActionTinted: Boolean)