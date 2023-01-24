package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.domain.api.useCases.NormalizeTextUseCase

class NormalizeTextUseCaseImpl internal constructor() : NormalizeTextUseCase {

    override fun invoke(text: String) = text.trim().lowercase()
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ö", "o")
        .replace("ő", "o")
        .replace("ú", "u")
        .replace("ü", "u")
        .replace("ű", "u")
        .replace("ă", "a")
        .replace("â", "a")
        .replace("î", "i")
        .replace("ț", "t")
        .replace("ș", "s")
        .replace("ä", "a")
}