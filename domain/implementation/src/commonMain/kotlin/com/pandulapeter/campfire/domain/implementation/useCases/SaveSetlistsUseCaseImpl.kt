package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.domain.api.useCases.SaveSetlistsUseCase

class SaveSetlistsUseCaseImpl internal constructor(
    private val setlistRepository: SetlistRepository
) : SaveSetlistsUseCase {

    override suspend operator fun invoke(setlists: List<Setlist>) = setlistRepository.saveSetlists(setlists)
}