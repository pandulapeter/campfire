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

import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.chords.ChordProDefinitions
import com.pandulapeter.campfire.chordpro.chords.ChordVoicings
import com.pandulapeter.campfire.chordpro.edit.ChordProHeader
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordTuning

/**
 * One edit of the editor's text: [text] inserted at [offset], and the selection afterwards from [selectionStart] to
 * [selectionEnd], the two equal for a caret. [isForStandardTuning] marks a definition written in standard tuning for a
 * player whose diagrams are drawn in another one, which the editor says.
 */
internal data class TextInsertion(
    val offset: Int,
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int = selectionStart,
    val isForStandardTuning: Boolean = false,
)

/**
 * What the editor's Chord shape button does to [text], the field's, in [notation], with the caret at [caret]: a
 * `{define}` line for the chord of the brackets the caret is in or touching, written into the header and filled in
 * with the shape the player would be shown for it on [instrument] — their own from [storedShapes], or the app's — with
 * the frets (or the keys) selected, since changing three numbers takes no knowledge of the syntax and writing the line
 * from nothing takes all of it. With the caret in no chord the line is written with the name left to be typed, and
 * the caret where it goes. A chord the text already defines on [tuning]'s instrument gets no second line: the caret goes
 * to the frets of the one there is, the way a `{tempo}` the header already names is selected rather than written again.
 *
 * A definition is read by every program, and by this one on any other device, as a shape in standard tuning, so that is
 * what is written whatever [tuning] the diagrams are drawn in: [storedShapes] are the player's choices under the
 * instrument's own key, which are the standard tuning's, and where the tuning's shapes are not the standard ones
 * ([ChordTuning.drawsDefinitions]) the line written is marked [TextInsertion.isForStandardTuning].
 */
internal fun chordShapeInsertion(
    text: String,
    caret: Int,
    notation: ChordNotation,
    tuning: ChordTuning,
    storedShapes: Map<String, String>,
): TextInsertion {
    val instrument = tuning.instrument
    val standardTuning = ChordTuning.standard(instrument)
    val name = ChordProDefinitions.chordAt(text, caret)?.takeIf { ChordProChords.parse(it, notation) != null }
    if (name != null) {
        ChordProDefinitions.rangeOf(text, name, instrument)?.let { return TextInsertion(offset = 0, text = "", selectionStart = it.first, selectionEnd = it.last + 1) }
    }
    val chord = name?.let { ChordProChords.parse(it, notation) }
    val shape = chord?.let { storedShapes[it.id]?.let { stored -> ChordVoicings.read(stored, standardTuning, it) } ?: ChordVoicings.default(it, standardTuning) }
    val line = if (name == null || shape == null) "{$DEFINE_DIRECTIVE: ${name.orEmpty()}}" else ChordProDefinitions.line(name, shape, instrument, notation)
    val insertion = ChordProHeader.insertDefinition(text, line)
    val nameStart = line.indexOf(':') + 2
    val lineStart = insertion.caretOffset - nameStart
    val isForStandardTuning = !tuning.drawsDefinitions
    if (name == null || shape == null) {
        return TextInsertion(insertion.offset, insertion.text, selectionStart = lineStart + nameStart + name.orEmpty().length, isForStandardTuning = isForStandardTuning)
    }
    val keyword = if (instrument == ChordInstrument.KEYBOARD) " keys " else " frets "
    val valuesStart = line.indexOf(keyword) + keyword.length
    val valuesEnd = line.indexOf(" fingers").takeIf { it >= 0 } ?: line.lastIndexOf('}')
    return TextInsertion(
        offset = insertion.offset,
        text = insertion.text,
        selectionStart = lineStart + valuesStart,
        selectionEnd = lineStart + valuesEnd,
        isForStandardTuning = isForStandardTuning,
    )
}

private const val DEFINE_DIRECTIVE = "define"
