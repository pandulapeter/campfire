/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chords

import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.chordpro.voicings.ChordProDefinitions
import com.pandulapeter.campfire.chordpro.voicings.ChordVoicings
import com.pandulapeter.campfire.chordpro.edit.ChordProHeader
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.instrument.chordInstrument
import com.pandulapeter.campfire.presentation.ui.instrument.isTwelveString

/**
 * One edit of the editor's text: [text] inserted at [offset], and the selection afterwards from [selectionStart] to
 * [selectionEnd], the two equal for a caret.
 */
internal data class TextInsertion(
    val offset: Int,
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int = selectionStart,
)

/**
 * The instruments the editor's Chord shape button offers a definition for, in the order it lists them: every one
 * diagrams are drawn for, the twelve-string left out since its shapes are the guitar's.
 */
internal val chordShapeInstruments get() = UserPreferences.Instrument.entries.filter { it.chordInstrument != null && !it.isTwelveString }

/**
 * What the editor's Chord shape button does to [text], the field's, in [notation], with the caret at [caret]: a
 * `{define}` line for the chord of the brackets the caret is in or touching, written into the header and filled in
 * with the shape a player of [instrument] would be shown for it — their own from [storedShapes], or the app's — with
 * the frets (or the keys) selected, since changing three numbers takes no knowledge of the syntax and writing the line
 * from nothing takes all of it. With the caret in no chord the line is a whole placeholder, the definition of a C
 * major ([PLACEHOLDER_CHORD]) with its name selected, so that the syntax is there to be copied from even by whoever has
 * never read it: the name is typed over first, and the frets after it. A chord the text already defines on [instrument],
 * the placeholder too, gets no second line, which would replace the song's own shape: the caret goes to the frets of the
 * one there is, the way a `{tempo}` the header already names is selected rather than written again.
 *
 * ChordPro gives a definition no tuning, so every program reads one as a shape in standard tuning, and that is what is
 * written whatever tuning the player's own diagrams are drawn in: [storedShapes] are their choices under the
 * instrument's own key, which are the standard tuning's.
 */
internal fun chordShapeInsertion(
    text: String,
    caret: Int,
    notation: ChordNotation,
    instrument: ChordInstrument,
    storedShapes: Map<String, String>,
): TextInsertion {
    val standardTuning = ChordTuning.standard(instrument)
    val chordAtCaret = ChordProDefinitions.chordAt(text, caret)?.takeIf { ChordProChords.parse(it, notation) != null }
    val name = chordAtCaret ?: ChordProNotation.shownName(PLACEHOLDER_CHORD, notation)
    // A chord the text already defines on this instrument, the placeholder's C included, gets no second line: the last one
    // would win, silently replacing the song's own shape.
    ChordProDefinitions.rangeOf(text, name, instrument)?.let { return TextInsertion(offset = 0, text = "", selectionStart = it.first, selectionEnd = it.last + 1) }
    val chord = ChordProChords.parse(name, notation)
    val shape = chord?.let { storedShapes[it.id]?.let { stored -> ChordVoicings.read(stored, standardTuning, it) } ?: ChordVoicings.default(it, standardTuning) }
    val line = if (shape == null) "{$DEFINE_DIRECTIVE: $name}" else ChordProDefinitions.line(name, shape, instrument, notation)
    val insertion = ChordProHeader.insertDefinition(text, line)
    val nameStart = line.indexOf(':') + 2
    val lineStart = insertion.caretOffset - nameStart
    if (chordAtCaret == null) {
        return TextInsertion(insertion.offset, insertion.text, selectionStart = lineStart + nameStart, selectionEnd = lineStart + nameStart + name.length)
    }
    if (shape == null) return TextInsertion(insertion.offset, insertion.text, selectionStart = lineStart + nameStart + name.length)
    val keyword = if (instrument == ChordInstrument.KEYBOARD) " keys " else " frets "
    val valuesStart = line.indexOf(keyword) + keyword.length
    val valuesEnd = line.indexOf(" fingers").takeIf { it >= 0 } ?: line.lastIndexOf('}')
    return TextInsertion(
        offset = insertion.offset,
        text = insertion.text,
        selectionStart = lineStart + valuesStart,
        selectionEnd = lineStart + valuesEnd,
    )
}

/** The chord a definition written with the caret in no chord is a placeholder for: the first one most players learn. */
private const val PLACEHOLDER_CHORD = "C"

private const val DEFINE_DIRECTIVE = "define"
