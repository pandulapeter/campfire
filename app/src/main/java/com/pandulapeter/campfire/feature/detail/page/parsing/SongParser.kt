package com.pandulapeter.campfire.feature.detail.page.parsing

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.TextAppearanceSpan
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.shared.span.ChordSpan

class SongParser(private val context: Context) {

    fun parseSong(text: String, shouldShowChords: Boolean, shouldUseGermanNotation: Boolean, transposition: Int): SpannableString {
        val sectionNames = mutableListOf<Section>()
        val chords = mutableListOf<Chord>()
        var offset = 0
        val parsedText = (if (shouldShowChords) text else text
            .replace(Regex("\\[(.*?)[]]"), "") // Remove chords
            .replace(Regex("[ ][ ]+"), "") // Remove groups of multiple whitespaces within a single line
            .replace(Regex("(?:\\h*\\n){3,}"), "") // Remove lines consisting only of empty space
            .replace(Regex("[}][{]+"), "}\n{") // Ensure that consecutive section headers are separated by an empty line
            .let {
                var returnValue = it
                Regex("[{]").findAll(returnValue, 1).forEach { result ->
                    if (it[result.range.first - 1] != '\n') {
                        returnValue = returnValue.substring(0, result.range.first) + "\n\n" + returnValue.substring(result.range.first, returnValue.length - 1)
                    }
                }
                returnValue = returnValue
                    .replace("\n\n", "\n")
                    .replace("\n\n", "\n")
                    .replace("\n\n", "\n")
                returnValue
                    .replace(Regex("\\{(.*?)[}]"), { if (it.range.first == 0 || returnValue[it.range.first - 2] == '}') it.value else "\n${it.value}" })
            }
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
                setSpan(
                    TextAppearanceSpan(context, R.style.Section),
                    it.startPosition,
                    it.startPosition + it.getName(context).length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            if (shouldShowChords) {
                Regex("]([ \\t]+)\\[").findAll(parsedText).forEach {
                    // Set the font of instrumental-only parts
                    setSpan(TextAppearanceSpan(context, R.style.Hint), it.range.first, it.range.last, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                chords.forEach {
                    setSpan(ChordSpan(it.getName(transposition, shouldUseGermanNotation)), it.startPosition, it.endPosition, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(TextAppearanceSpan(context, if (it.isHint) R.style.Hint else R.style.Chord), it.startPosition, it.endPosition, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
    }

    private fun String.toSuffix(isHint: Boolean) = substring(if (isHint) 1 else if (this[2] == 'b' || this[2] == '#') 3 else 2).removeSuffix("]").toLowerCase()
}
