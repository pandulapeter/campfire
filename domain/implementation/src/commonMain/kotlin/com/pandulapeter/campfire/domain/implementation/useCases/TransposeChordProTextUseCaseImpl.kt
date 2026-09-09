package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.chordpro.ChordProTransposer
import com.pandulapeter.campfire.domain.api.useCases.TransposeChordProTextUseCase

class TransposeChordProTextUseCaseImpl internal constructor() : TransposeChordProTextUseCase {

    override operator fun invoke(text: String, semitones: Int) = ChordProTransposer.transposeText(text, semitones)
}
