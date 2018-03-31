package com.pandulapeter.campfire.old.data.model

/**
 * Represents a single chord.
 */
data class Chord(private val root: Note, private val suffix: String, val startPosition: Int, val endPosition: Int) {

    fun getName(transposition: Int, shouldUseGermanNotation: Boolean) = root.transpose(transposition).getName(shouldUseGermanNotation) + suffix
}