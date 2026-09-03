package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.domain.api.useCases.TransposeRawSongDetailsUseCase

class TransposeRawSongDetailsUseCaseImpl internal constructor() : TransposeRawSongDetailsUseCase {

    override fun invoke(rawData: String, transposition: Int) = if (transposition % NOTE_COUNT == 0) {
        rawData
    } else {
        chordRegex.replace(rawData) { match -> "[${match.groupValues[1].transposeChord(transposition)}]" }
    }

    // Transposes the root note and the optional bass note (after a slash) while keeping the rest of the chord name intact.
    private fun String.transposeChord(transposition: Int) = split(BASS_NOTE_SEPARATOR, limit = 2)
        .joinToString(BASS_NOTE_SEPARATOR) { it.transposeLeadingNote(transposition) }

    private fun String.transposeLeadingNote(transposition: Int): String {
        val noteIndex = noteIndices[getOrNull(0)] ?: return this
        val accidental = accidentals[getOrNull(1)]
        val suffixStartIndex = if (accidental == null) 1 else 2
        val transposedNoteIndex = (noteIndex + (accidental ?: 0) + transposition).mod(NOTE_COUNT)
        return noteNames[transposedNoteIndex] + substring(suffixStartIndex)
    }

    companion object {
        private const val NOTE_COUNT = 12
        private const val BASS_NOTE_SEPARATOR = "/"
        private val chordRegex = Regex("\\[(.*?)[]]")
        private val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        private val noteIndices = mapOf(
            'C' to 0,
            'D' to 2,
            'E' to 4,
            'F' to 5,
            'G' to 7,
            'A' to 9,
            'B' to 11,
            'H' to 11 // German notation
        )
        private val accidentals = mapOf(
            '#' to 1,
            'b' to -1
        )
    }
}
