package com.pandulapeter.campfire.feature.main.options.changelog

import com.pandulapeter.campfire.data.repository.ChangelogRepository
import com.pandulapeter.campfire.feature.shared.OldCampfireViewModel
import org.koin.android.ext.android.inject

class ChangelogViewModel : OldCampfireViewModel() {

    private val changelogRepository by inject<ChangelogRepository>()
    val adapter = ChangelogAdapter(changelogRepository.data)
}