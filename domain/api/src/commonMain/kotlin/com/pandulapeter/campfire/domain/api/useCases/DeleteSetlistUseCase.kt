package com.pandulapeter.campfire.domain.api.useCases

interface DeleteSetlistUseCase {

    suspend operator fun invoke(fileName: String)
}
