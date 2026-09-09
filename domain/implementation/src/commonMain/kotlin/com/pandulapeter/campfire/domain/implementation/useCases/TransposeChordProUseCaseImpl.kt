package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.chordpro.ChordProTransposer
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.domain.api.useCases.TransposeChordProUseCase

class TransposeChordProUseCaseImpl internal constructor() : TransposeChordProUseCase {

    override operator fun invoke(song: ChordProSong, semitones: Int) = ChordProTransposer.transpose(song, semitones)
}
