package com.pandulapeter.campfire.domain.useCases

import com.pandulapeter.campfire.data.repository.api.LanguageRepository
import com.pandulapeter.campfire.domain.resultOf

class GetLanguagesUseCase internal constructor(
    private val languageRepository: LanguageRepository
) {
    suspend operator fun invoke(
        isForceRefresh: Boolean
    ) = resultOf {
        languageRepository.getLanguages(isForceRefresh)
    }
}