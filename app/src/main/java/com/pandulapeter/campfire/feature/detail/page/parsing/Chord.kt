package com.pandulapeter.campfire.feature.detail.page.parsing

data class Chord(
    private val root: Note,
    private val suffix: String,
    val startPosition: Int,
    val endPosition: Int
) {
    private val bassNote: Note? = if (suffix.indexOf('/') >= 0) Note.toNote("[" + suffix.substring(suffix.indexOf('/') + 1) + "]") else null
    val isHint = root === Note.Hint

    fun getName(transposition: Int, shouldUseGermanNotation: Boolean) = root.transpose(transposition).getName(shouldUseGermanNotation) +
            if (bassNote == null) suffix else "${suffix.substring(0, suffix.indexOf('/'))}/${bassNote.transpose(transposition).getName(shouldUseGermanNotation)}"
}