package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.TranspositionKey

interface SaveTranspositionsUseCase {

    suspend operator fun invoke(transpositions: Map<TranspositionKey, Int>)
}
