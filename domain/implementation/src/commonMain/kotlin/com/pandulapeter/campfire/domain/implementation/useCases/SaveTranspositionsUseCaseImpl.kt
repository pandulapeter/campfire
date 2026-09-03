package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.domain.TranspositionKey
import com.pandulapeter.campfire.data.repository.api.TranspositionRepository
import com.pandulapeter.campfire.domain.api.useCases.SaveTranspositionsUseCase

class SaveTranspositionsUseCaseImpl internal constructor(
    private val transpositionRepository: TranspositionRepository
) : SaveTranspositionsUseCase {

    override suspend operator fun invoke(transpositions: Map<TranspositionKey, Int>) = transpositionRepository.saveTranspositions(transpositions)
}
