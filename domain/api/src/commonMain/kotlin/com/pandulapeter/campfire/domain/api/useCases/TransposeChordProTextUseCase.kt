package com.pandulapeter.campfire.domain.api.useCases

interface TransposeChordProTextUseCase {

    /** Transposes the chords of a ChordPro document in place, leaving the rest of the text exactly as it was. */
    operator fun invoke(text: String, semitones: Int): String
}
