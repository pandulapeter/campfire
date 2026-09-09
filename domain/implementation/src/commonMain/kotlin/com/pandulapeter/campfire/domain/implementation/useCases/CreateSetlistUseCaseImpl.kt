package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.domain.api.useCases.CreateSetlistUseCase

class CreateSetlistUseCaseImpl internal constructor(
    private val setlistRepository: SetlistRepository
) : CreateSetlistUseCase {

    /** The newest setlist goes on top, so it gets a priority above every existing one. */
    override suspend operator fun invoke(title: String) = setlistRepository.createSetlist(
        title = title.trim(),
        priority = (setlistRepository.loadSetlistsIfNeeded().orEmpty().maxOfOrNull { it.priority } ?: -1) + 1
    )
}
