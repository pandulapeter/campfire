package com.pandulapeter.campfire.feature.detail.page.parsing

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.TextAppearanceSpan
import com.pandulapeter.campfire.R

class SongParser(private val context: Context) {

    fun parseSong(text: String, shouldShowChords: Boolean, shouldUseGermanNotation: Boolean, transposition: Int): SpannableString {
        val sectionNames = mutableListOf<Section>()
        val chords = mutableListOf<Chord>()
        var offset = 0
        val newText = text.replace("*", "") //Remove asterisk characters //TODO: This should be useless.
        val parsedText = (if (shouldShowChords) newText else newText
            .replace(Regex("\\[(.*?)[]]"), "") // Remove chords
            .replace(Regex("(?:\\h*\\n){3,}"), "") // Remove lines consisting only of empty space
            .replace(Regex("[ ][ ]+"), "") // Remove groups of multiple whitespaces within a single line
            .replace(Regex("[}][{]+"), "}\n{") // Ensure that consecutive section headers are separated by an empty line
                ).replace(Regex("\\{(.*?)[}]"), {
            // Find the section headers
            val sectionType = SectionType.fromAbbreviation(it.value[1])
            if (sectionType != null) {
                val index = it.value.indexOf("_").let { index -> if (index >= 0) it.value.substring(index + 1).removeSuffix("}").toInt() else 0 }
                val section = Section(sectionType, index, offset + it.range.first)
                val result = section.getName(context)
                sectionNames.add(section)
                offset += result.length - it.value.length
                result
            } else {
                it.value
            }
        })
        if (shouldShowChords) {
            parsedText.replace(Regex("\\[(.*?)]")) {
                // Find the chords
                Note.toNote(it.value)?.let { note ->
                    chords.add(Chord(note, it.value.toSuffix(note === Note.Hint), it.range.first, it.range.last + 1))
                }
                it.value
            }
        }
        return SpannableString(parsedText).apply {
            sectionNames.forEach {
                //TODO: getName() is called unnecessarily.
                setSpan(
                    TextAppearanceSpan(context, R.style.Section),
                    it.startPosition,
                    it.startPosition + it.getName(context).length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            if (shouldShowChords) {
                chords.forEach {
                    setSpan(ChordSpan(it.getName(transposition, shouldUseGermanNotation)), it.startPosition, it.endPosition, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(TextAppearanceSpan(context, R.style.Chord), it.startPosition, it.endPosition, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                Regex("]([ \\t]+)\\[").findAll(parsedText).forEach {
                    // Set the font of instrumental-only parts
                    setSpan(TextAppearanceSpan(context, R.style.Chord), it.range.first, it.range.last, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
    }

    private fun String.toSuffix(isHint: Boolean) = substring(if (isHint) 1 else if (this[2] == 'b' || this[2] == '#') 3 else 2).removeSuffix("]").toLowerCase()
}
