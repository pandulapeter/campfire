package com.pandulapeter.campfire.domain.useCases

import com.pandulapeter.campfire.data.repository.api.LanguageRepository

class AreLanguagesAvailableUseCase internal constructor(
    private val languageRepository: LanguageRepository
) {
    operator fun invoke() = languageRepository.areLanguagesAvailable()
}