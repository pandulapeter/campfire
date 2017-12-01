package com.pandulapeter.campfire.feature.home.shared.homefragment.list

import android.support.annotation.DrawableRes
import android.support.annotation.StringRes
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.SongInfo

/**
 * Wraps all information that needs to appear on a list item.
 */
data class SongInfoViewModel(
    val songInfo: SongInfo,
    @StringRes val actionDescription: Int = R.string.something_went_wrong,
    @DrawableRes val actionIcon: Int, //TODO: This will need a loading state.
    val isActionTinted: Boolean)