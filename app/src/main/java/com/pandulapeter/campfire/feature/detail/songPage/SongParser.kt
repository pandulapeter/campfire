package com.pandulapeter.campfire.feature.detail.songPage

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.Chord
import com.pandulapeter.campfire.data.model.Note
import com.pandulapeter.campfire.data.model.Section
import com.pandulapeter.campfire.data.model.SectionType
import com.pandulapeter.campfire.util.color

/**
 * Parses raw song texts.
 */
class SongParser(private val context: Context) {
    private val accentColor = context.color(R.color.accent)

    //TODO: Implement chord parsing.
    //TODO: Remove line breaks if they occur multiple times in a row.
    fun parseSong(text: String, shouldShowChords: Boolean): SpannableString {
        val sectionNames = mutableListOf<Section>()
        val chords = mutableListOf<Chord>()
        var offset = 0
        val newText = text.replace("*", "") //TODO: This should be useless.
        val parsedText = (if (shouldShowChords) newText else newText.replace(Regex("\\[(.*?)\\]"), ""))
            .replace(Regex("\\{(.*?)\\}"), {
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
            parsedText.replace(Regex("\\[(.*?)\\]")) {
                chords.add(Chord(it.value.toNote(), it.value.toSuffix(), it.range.first, it.range.last + 1))
                it.value
            }
        }
        return SpannableString(parsedText).apply {
            sectionNames.forEach {
                //TODO: getName() is called unnecessarily.
                setSpan(ForegroundColorSpan(accentColor), it.startPosition, it.startPosition + it.getName(context).length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            //TODO: Respect German notation preference and transposition.
            chords.forEach {
                setSpan(ChordSpan(it.getName(0, false)), it.startPosition, it.endPosition, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    //TODO: Crashing in the coroutine apparently does not cause the app to crash.
    private fun String.toNote() = when (this[2].toLowerCase()) {
        'b' -> when (this[1].toUpperCase()) {
            'C' -> Note.B
            'D' -> Note.CSharp
            'E' -> Note.DSharp
            'F' -> Note.E
            'G' -> Note.FSharp
            'A' -> Note.GSharp
            'B' -> Note.ASharp
            else -> throw IllegalArgumentException(EXCEPTION_MESSAGE)
        }
        '#' -> when (this[1].toUpperCase()) {
            'C' -> Note.CSharp
            'D' -> Note.DSharp
            'E' -> Note.F
            'F' -> Note.FSharp
            'G' -> Note.GSharp
            'A' -> Note.ASharp
            'B' -> Note.C
            else -> throw IllegalArgumentException(EXCEPTION_MESSAGE)
        }
        else -> when (this[1].toUpperCase()) {
            'C' -> Note.C
            'D' -> Note.D
            'E' -> Note.E
            'F' -> Note.F
            'G' -> Note.G
            'A' -> Note.A
            'B' -> Note.B
            else -> throw IllegalArgumentException(EXCEPTION_MESSAGE)
        }
    }

    private fun String.toSuffix() = substring(if (this[2] == 'b' || this[2] == '#') 3 else 2).removeSuffix("]").toLowerCase()

    companion object {
        const val EXCEPTION_MESSAGE = "Cannot parse song."
    }
}
