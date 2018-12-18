package com.pandulapeter.campfire.data.repository

import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.ChangelogItem

class ChangelogRepository {

    val data = listOf(
        ChangelogItem(R.string.options_changelog_0_9_0_version_name, R.string.options_changelog_0_9_0_description),
        ChangelogItem(R.string.options_changelog_0_8_0_version_name, R.string.options_changelog_0_8_0_description),
        ChangelogItem(R.string.options_changelog_0_7_0_version_name, R.string.options_changelog_0_7_0_description),
        ChangelogItem(R.string.options_changelog_0_6_0_version_name, R.string.options_changelog_0_6_0_description),
        ChangelogItem(R.string.options_changelog_0_5_0_version_name, R.string.options_changelog_0_5_0_description),
        ChangelogItem(R.string.options_changelog_0_4_0_version_name, R.string.options_changelog_0_4_0_description),
        ChangelogItem(R.string.options_changelog_0_3_0_version_name, R.string.options_changelog_0_3_0_description),
        ChangelogItem(R.string.options_changelog_0_2_0_version_name, R.string.options_changelog_0_2_0_description),
        ChangelogItem(R.string.options_changelog_0_1_0_version_name, R.string.options_changelog_0_1_0_description)
    )
}