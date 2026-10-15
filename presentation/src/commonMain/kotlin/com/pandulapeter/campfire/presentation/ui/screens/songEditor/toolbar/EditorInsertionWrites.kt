/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor.toolbar

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert
import androidx.compose.ui.text.TextRange
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.edit.ChordProHeader
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.presentation.ui.chords.chordShapeInsertion

/** Writes [insertion] where it belongs: into the song's header when it describes the song, at the caret otherwise. */
internal fun TextFieldState.insert(insertion: EditorInsertion) {
    val metadataName = insertion.metadataName
    if (metadataName == null) insertAtSelection(insertion) else insertIntoHeader(insertion, metadataName)
}

/**
 * Writes a directive that describes the song into its header, wherever the caret happens to be at the time.
 *
 * ChordPro reads a `{title}` as the title of the song from anywhere in the file, so the one thing the caret cannot
 * decide here is where the directive goes: a title written into the middle of a verse is valid, invisible in the
 * rendered song, and nowhere near the rest of what the file says about itself. `:chordpro` picks the line, keeping
 * the header in the order it lists metadata in and leaving one the user has arranged otherwise alone; the caret
 * follows it there, since a directive inserted from the toolbar is one the value is about to be typed into. A song
 * that already says what the button writes has that line's value selected instead, so the button is never out of
 * reach and the next keystroke rewrites it. What is selected is copied into the directive as its value, and left
 * where it is: it is the text that names the song (a pasted heading, a first line), which stays part of the song. A
 * tempo or a time signature the header already names is the one exception, see [ChordProHeader.insertChangeable].
 */
private fun TextFieldState.insertIntoHeader(insertion: EditorInsertion, metadataName: String) = edit {
    val start = minOf(selection.start, selection.end)
    val header = ChordProHeader.write(
        text = originalText.toString(),
        name = metadataName,
        caretOffset = start,
        prefix = insertion.prefix,
        suffix = insertion.suffix,
        value = originalText.substring(start, maxOf(selection.start, selection.end)),
    )
    replace(header.offset, header.offset + header.replacedLength, header.text)
    selection = TextRange(header.caretOffset, header.selectionEnd)
}

/**
 * What the Chord shape button needs besides the text and the instrument picked from its menu: the notation the field is
 * written in, and the player's own choices of shape (`UserPreferences.chordVoicings`), whose entry under the picked
 * instrument's id is the standard tuning's that the line is filled in from (see [chordShapeInsertion]).
 */
internal class EditorChordShapes(
    val notation: ChordNotation,
    val storedShapes: Map<String, Map<String, String>>,
)

internal fun TextFieldState.insertChordShape(chordShapes: EditorChordShapes, instrument: ChordInstrument) = edit {
    val insertion = chordShapeInsertion(
        text = originalText.toString(),
        caret = minOf(selection.start, selection.end),
        notation = chordShapes.notation,
        instrument = instrument,
        storedShapes = chordShapes.storedShapes[instrument.id].orEmpty(),
    )
    if (insertion.text.isNotEmpty()) insert(insertion.offset, insertion.text)
    selection = TextRange(insertion.selectionStart, insertion.selectionEnd)
}

/**
 * Puts the two halves of [insertion] around the selection, or around the caret when there is none.
 *
 * A directive only counts as one when nothing shares its line, so the halves grow a line break of their own where
 * the text on either side would otherwise run into them.
 */
private fun TextFieldState.insertAtSelection(insertion: EditorInsertion) = edit {
    val start = minOf(selection.start, selection.end)
    val end = maxOf(selection.start, selection.end)
    val selected = originalText.substring(start, end)
    val prefix = if (selected.isEmpty()) insertion.emptyPrefix else insertion.prefix
    val suffix = if (selected.isEmpty()) insertion.emptySuffix else insertion.suffix
    val opening = if (insertion.isOwnLine && start > 0 && originalText[start - 1] != '\n') "\n$prefix" else prefix
    val closing = if (insertion.isOwnLine && end < originalText.length && originalText[end] != '\n') "$suffix\n" else suffix
    delete(start, end)
    insert(start, opening + selected + closing)
    // With nothing selected the caret lands between the two halves, which is where the next thing typed belongs.
    selection = if (selected.isEmpty()) {
        TextRange(start + opening.length)
    } else {
        TextRange(start + opening.length, start + opening.length + selected.length)
    }
}
