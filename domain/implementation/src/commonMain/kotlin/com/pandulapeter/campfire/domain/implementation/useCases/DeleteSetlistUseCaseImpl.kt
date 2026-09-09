package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.repository.api.SetlistRepository
import com.pandulapeter.campfire.domain.api.useCases.DeleteSetlistUseCase

class DeleteSetlistUseCaseImpl internal constructor(
    private val setlistRepository: SetlistRepository
) : DeleteSetlistUseCase {

    override suspend operator fun invoke(fileName: String) = setlistRepository.deleteSetlist(fileName)
}
