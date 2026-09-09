package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.Setlist

interface SaveSetlistUseCase {

    suspend operator fun invoke(setlist: Setlist)
}
