package com.pandulapeter.campfire.data.model.local

import android.support.annotation.StringRes

data class ChangelogItem(
    @StringRes val versionName: Int,
    @StringRes val description: Int
)