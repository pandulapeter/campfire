/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert
import androidx.compose.ui.text.TextRange
import com.pandulapeter.campfire.chordpro.ChordProPrettifier
import com.pandulapeter.campfire.chordpro.chords.ChordProTransposer

/**
 * Replaces everything, for the rewrites that touch the whole document. The undo history records such an edit as
 * the whole text twice and keeps a hundred of them, which for a long document is more memory than a phone hands
 * out, so there the history starts over with the rewrite: it can still be undone, what came before it cannot.
 *
 * @param isSelectionMapped Whether the caret and the selection stay next to the text they were next to
 *   ([editedOffset]), for an edit that changes a few places of the text - a sheet of the menus, a save's respelling,
 *   the file changing underneath. A revert has nothing to map by and keeps the caret's offset.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun TextFieldState.replaceAll(text: String, isSelectionMapped: Boolean) {
    val before = this.text.toString()
    if (before.length > LONG_DOCUMENT_LENGTH) undoState.clearHistory()
    edit {
        val newSelection = if (isSelectionMapped) {
            TextRange(editedOffset(before, text, selection.start), editedOffset(before, text, selection.end))
        } else {
            TextRange(selection.start.coerceAtMost(text.length))
        }
        delete(0, length)
        insert(0, text)
        selection = newSelection
    }
}

/**
 * Replaces the document with its transposition, keeping the caret and the selection next to the text they were next
 * to (see [ChordProTransposer.transposedOffset]) rather than at the same character count. The history is treated the
 * way [replaceAll] treats it.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun TextFieldState.replaceWithTransposition(transposed: String) {
    val before = text.toString()
    if (before == transposed) return
    if (before.length > LONG_DOCUMENT_LENGTH) undoState.clearHistory()
    edit {
        val start = ChordProTransposer.transposedOffset(before, transposed, selection.start)
        val end = ChordProTransposer.transposedOffset(before, transposed, selection.end)
        replace(0, length, transposed)
        selection = TextRange(start, end)
    }
}

/** Keeps the selection next to its lines when formatting moves the header and changes the line breaks. */
@OptIn(ExperimentalFoundationApi::class)
internal fun TextFieldState.replaceWithPrettification(prettified: String) {
    val before = text.toString()
    if (before == prettified) return
    if (before.length > LONG_DOCUMENT_LENGTH) undoState.clearHistory()
    edit {
        val start = ChordProPrettifier.prettifiedOffset(before, prettified, selection.start)
        val end = ChordProPrettifier.prettifiedOffset(before, prettified, selection.end)
        replace(0, length, prettified)
        selection = TextRange(start, end)
    }
}

/**
 * What the editor takes for a long document: 100 KB as a saved state writes it, several times the longest song
 * and a tenth of what the transaction has room for.
 */
internal const val LONG_DOCUMENT_LENGTH = 50_000
