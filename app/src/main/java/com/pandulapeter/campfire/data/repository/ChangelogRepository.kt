package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.ChangelogItem

class ChangelogRepository {

    val data = listOf(
        ChangelogItem(R.string.options_changelog_0_0_3_version_name, R.string.options_changelog_0_0_3_description),
        ChangelogItem(R.string.options_changelog_0_0_2_version_name, R.string.options_changelog_0_0_2_description),
        ChangelogItem(R.string.options_changelog_0_0_1_version_name, R.string.options_changelog_0_0_1_description)
    )
}