package com.pandulapeter.campfire.feature.main.options.changelog

import com.pandulapeter.campfire.data.repository.ChangelogRepository
import com.pandulapeter.campfire.feature.shared.CampfireViewModel

class ChangelogViewModel(changelogRepository: ChangelogRepository) : CampfireViewModel() {

    val adapter = ChangelogAdapter(changelogRepository.data)
}