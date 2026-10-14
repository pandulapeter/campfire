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
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.edit.ChordProHeader
import com.pandulapeter.campfire.chordpro.model.ChordTuning
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
 * follows it there, since a directive inserted from the toolbar is one the value is about to be typed into. A tempo or
 * a time signature the header already names is the one exception, see [ChordProHeader.insertChangeable].
 */
private fun TextFieldState.insertIntoHeader(insertion: EditorInsertion, metadataName: String) = edit {
    val header = if (metadataName in ChordProHeader.changeableMetadata) {
        // The header holds the song's own value and the body its changes, so here the caret does say where it goes.
        ChordProHeader.insertChangeable(
            text = originalText.toString(),
            name = metadataName,
            caretOffset = minOf(selection.start, selection.end),
            prefix = insertion.prefix,
            suffix = insertion.suffix,
        )
    } else {
        ChordProHeader.insert(
            text = originalText.toString(),
            name = metadataName,
            prefix = insertion.prefix,
            suffix = insertion.suffix,
        )
    }
    replace(header.offset, header.offset + header.replacedLength, header.text)
    selection = TextRange(header.caretOffset, header.selectionEnd)
}

/**
 * What the Chord shape button needs besides the text: the notation the field is written in, the tuning the diagrams are
 * drawn in and the player's own standard tuning shapes the line is filled in from, and what says that a line was
 * written for standard tuning where the diagrams are drawn in another one (see [chordShapeInsertion]).
 */
internal class EditorChordShapes(
    val notation: ChordNotation,
    val tuning: ChordTuning,
    val storedShapes: Map<String, String>,
    val onWrittenForStandardTuning: () -> Unit,
)

/** Writes the definition [chordShapeInsertion] decides on as one step of the undo history, and selects what it says. */
internal fun TextFieldState.insertChordShape(chordShapes: EditorChordShapes) {
    var isForStandardTuning = false
    edit {
        val insertion = chordShapeInsertion(
            text = originalText.toString(),
            caret = minOf(selection.start, selection.end),
            notation = chordShapes.notation,
            tuning = chordShapes.tuning,
            storedShapes = chordShapes.storedShapes,
        )
        if (insertion.text.isNotEmpty()) insert(insertion.offset, insertion.text)
        selection = TextRange(insertion.selectionStart, insertion.selectionEnd)
        isForStandardTuning = insertion.text.isNotEmpty() && insertion.isForStandardTuning
    }
    if (isForStandardTuning) chordShapes.onWrittenForStandardTuning()
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
