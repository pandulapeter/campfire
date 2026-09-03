package com.pandulapeter.campfire.domain.api.useCases

interface NormalizeTextUseCase {

    operator fun invoke(text: String): String
}