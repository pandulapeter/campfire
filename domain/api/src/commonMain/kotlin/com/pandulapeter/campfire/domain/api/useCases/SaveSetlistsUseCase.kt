package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.Setlist

interface SaveSetlistsUseCase {

    suspend operator fun invoke(setlists: List<Setlist>)
}