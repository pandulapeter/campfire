package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.Setlist

interface CreateSetlistUseCase {

    /** Writes a new, empty setlist file and returns the setlist it became. */
    suspend operator fun invoke(title: String): Setlist
}
