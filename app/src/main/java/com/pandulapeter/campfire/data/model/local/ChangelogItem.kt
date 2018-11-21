package com.pandulapeter.campfire.data.model.local

import androidx.annotation.StringRes

data class ChangelogItem(
    @StringRes val versionName: Int,
    @StringRes val description: Int
)