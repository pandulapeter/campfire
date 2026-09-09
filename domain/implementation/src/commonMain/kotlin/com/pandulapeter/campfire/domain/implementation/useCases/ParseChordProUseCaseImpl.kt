package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.domain.api.useCases.ParseChordProUseCase

class ParseChordProUseCaseImpl internal constructor() : ParseChordProUseCase {

    override operator fun invoke(text: String) = ChordProParser.parse(text)
}
