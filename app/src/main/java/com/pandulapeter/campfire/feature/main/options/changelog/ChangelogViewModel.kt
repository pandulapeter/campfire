package com.pandulapeter.campfire.feature.main.options.changelog

import com.pandulapeter.campfire.data.repository.ChangelogRepository
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.feature.shared.InteractionBlocker

class ChangelogViewModel(
    changelogRepository: ChangelogRepository,
    interactionBlocker: InteractionBlocker
) : CampfireViewModel(interactionBlocker) {

    val data = changelogRepository.data
}