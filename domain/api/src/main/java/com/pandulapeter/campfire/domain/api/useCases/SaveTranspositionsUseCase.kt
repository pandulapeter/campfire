package com.pandulapeter.campfire.domain.api.useCases

interface SaveTranspositionsUseCase {

    suspend operator fun invoke(transpositions: Map<String, Int>)
}
