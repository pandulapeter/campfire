package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.chordpro.model.ChordProSong

interface TransposeChordProUseCase {

    operator fun invoke(song: ChordProSong, semitones: Int): ChordProSong
}
