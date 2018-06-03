package com.pandulapeter.campfire.feature.main.options.changelog

import com.pandulapeter.campfire.data.repository.ChangelogRepository
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import org.koin.android.ext.android.inject

class ChangelogViewModel : CampfireViewModel() {

    private val changelogRepository by inject<ChangelogRepository>()
    val adapter = ChangelogAdapter(changelogRepository.data)
}