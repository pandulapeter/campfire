package com.pandulapeter.campfire.domain.api.useCases

interface LoadScreenDataUseCase {

    /** @param isRescan Whether to read the library directory again instead of using what is already in memory. */
    suspend operator fun invoke(isRescan: Boolean)
}
