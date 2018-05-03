package com.pandulapeter.campfire.feature.detail.page.parsing

data class Chord(
    private val root: Note,
    private val suffix: String,
    val startPosition: Int,
    val endPosition: Int
) {
    private val bassNote: Note? = if (suffix.indexOf('/') >= 0) Note.toNote("[" + suffix.substring(suffix.indexOf('/') + 1) + "]") else null

    fun getName(transposition: Int, shouldUseGermanNotation: Boolean) =
        root.transpose(transposition).getName(shouldUseGermanNotation) + if (bassNote == null) suffix else "/" + bassNote.transpose(transposition).getName(shouldUseGermanNotation)
}