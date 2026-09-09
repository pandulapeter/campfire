package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.chordpro.model.ChordProSong

interface ParseChordProUseCase {

    operator fun invoke(text: String): ChordProSong
}
