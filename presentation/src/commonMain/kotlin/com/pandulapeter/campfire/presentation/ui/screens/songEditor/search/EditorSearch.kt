/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor.search

import com.pandulapeter.campfire.presentation.ui.screens.songEditor.replaceAll
import com.pandulapeter.campfire.presentation.ui.screens.songEditor.replaceMatches
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import com.pandulapeter.campfire.presentation.ui.components.MAX_SEARCH_QUERY_LENGTH
import kotlinx.coroutines.flow.filterNotNull

/** Which of the editor's two search sheets is open: Find, or Find and replace, which is Find with a second field. */
internal enum class EditorSearchMode {
    FIND,
    REPLACE,
}

/**
 * Every occurrence of what is searched for, for the field to draw behind the text, and the one the search is at, for it
 * to draw differently and scroll into view.
 */
@Immutable
internal class SearchHighlights(
    val matchStarts: List<Int>,
    val length: Int,
    val currentIndex: Int,
)

/**
 * The editor's Find and Find and replace: what is searched for and what it is replaced with, kept from one opening to
 * the next as an editor keeps them, and where the search is.
 *
 * Where the search is, is the field's own selection rather than a position of its own: the current match is the first
 * one at or after where the selection starts ([currentMatchIndex]), and moving to another match selects it. So a search
 * picks up from where the caret was, typing a longer query stays at the match it has reached rather than starting over
 * from the top, a replacement moves on to the match after what it wrote, and closing the sheet leaves the match it was at
 * selected in the field. Every replacement is an edit of the field's own, one step of its undo history, which only Save
 * writes to the file like any other.
 *
 * @param text The text of [textFieldState] as one string, the copy the screen already shares.
 */
@Stable
internal class EditorSearch(
    private val textFieldState: TextFieldState,
    private val text: State<String>,
    val query: TextFieldState,
    val replacement: TextFieldState,
    mode: EditorSearchMode?,
    isMatchCase: Boolean,
) {
    var mode by mutableStateOf(mode)
        private set
    var isMatchCase by mutableStateOf(isMatchCase)

    /**
     * Where the open sheet's top edge is on the screen, for the field to keep the current match above it, or null while
     * no sheet is open or it has not been laid out yet. Not saved: a restored sheet measures it again on its first frame.
     */
    var sheetTop: Float? by mutableStateOf(null)

    // Nothing is searched for while neither sheet is open, so that typing in the field never scans the song for a
    // query nobody is looking at.
    private val matchStarts by derivedStateOf {
        if (this.mode == null) emptyList() else searchMatchStarts(text.value, query.text.toString(), this.isMatchCase)
    }
    private val currentIndex by derivedStateOf {
        currentMatchIndex(matchStarts, minOf(textFieldState.selection.start, textFieldState.selection.end))
    }

    val matchCount get() = matchStarts.size

    /** The current match counted from one, as the sheet names it, or 0 where there is none. */
    val currentNumber get() = currentIndex + 1

    val highlights by derivedStateOf {
        matchStarts.takeIf { it.isNotEmpty() }?.let { SearchHighlights(matchStarts = it, length = query.text.length, currentIndex = currentIndex) }
    }

    /**
     * Opens the sheet of [mode], with what is selected in the field as the query where it is something to search for: a
     * word or a chord picked out to be found again, rather than a whole verse. The query is selected whole, so that the
     * first key typed replaces it, and so is the replacement left from an earlier use whenever the sheet turns to
     * replacing, since the caret goes to it then. With a sheet open already it only switches to [mode]: the field's
     * selection is the current match by then, which may differ from the query in its case.
     */
    fun open(mode: EditorSearchMode) {
        if (this.mode == null) {
            val fieldSelection = textFieldState.selection
            if (!fieldSelection.collapsed) {
                val selected = text.value.substring(fieldSelection.min, fieldSelection.max)
                if ('\n' !in selected && selected.length <= MAX_SEARCH_QUERY_LENGTH) query.setTextAndPlaceCursorAtEnd(selected)
            }
            query.edit { selection = TextRange(0, length) }
        }
        if (mode == EditorSearchMode.REPLACE && this.mode != EditorSearchMode.REPLACE) replacement.edit { selection = TextRange(0, length) }
        this.mode = mode
    }

    fun close() {
        mode = null
        sheetTop = null
    }

    fun selectNext() {
        if (matchStarts.isNotEmpty()) select(matchStarts[(currentIndex + 1).mod(matchStarts.size)])
    }

    fun selectPrevious() {
        if (matchStarts.isNotEmpty()) select(matchStarts[(currentIndex - 1).mod(matchStarts.size)])
    }

    /** Writes the replacement over the current match and leaves the caret after it, which makes the next match current. */
    fun replaceCurrent() {
        val start = matchStarts.getOrNull(currentIndex) ?: return
        val replacementText = replacement.text.toString()
        val end = start + query.text.length
        textFieldState.edit {
            replace(start, end, replacementText)
            selection = TextRange(start + replacementText.length)
        }
    }

    /** Writes the replacement over every match as one edit, so that a single undo takes all of them back. */
    fun replaceAll() = textFieldState.replaceMatches(matchStarts, query.text.length, replacement.text.toString())

    /** The current match as the range it covers, for [select] to follow whenever it changes. */
    val currentMatch get() = matchStarts.getOrNull(currentIndex)?.let { TextRange(it, it + query.text.length) }

    private fun select(start: Int) = select(TextRange(start, start + query.text.length))

    fun select(range: TextRange) {
        if (textFieldState.selection != range) textFieldState.edit { selection = range }
    }

    companion object {

        fun saver(textFieldState: TextFieldState, text: State<String>) = listSaver<EditorSearch, Any>(
            save = { listOf(it.query.text.toString(), it.replacement.text.toString(), it.mode?.name.orEmpty(), it.isMatchCase) },
            restore = { saved ->
                EditorSearch(
                    textFieldState = textFieldState,
                    text = text,
                    query = TextFieldState(saved[0] as String),
                    replacement = TextFieldState(saved[1] as String),
                    mode = (saved[2] as String).takeIf { it.isNotEmpty() }?.let(EditorSearchMode::valueOf),
                    isMatchCase = saved[3] as Boolean,
                )
            },
        )
    }
}

/**
 * The editor's [EditorSearch], saved with the field it searches so that a rotation or a restored process reopens the
 * sheet that was open, on the same query.
 */
@Composable
internal fun rememberEditorSearch(textFieldState: TextFieldState, text: State<String>): EditorSearch {
    val search = rememberSaveable(textFieldState, saver = EditorSearch.saver(textFieldState, text)) {
        EditorSearch(
            textFieldState = textFieldState,
            text = text,
            query = TextFieldState(),
            replacement = TextFieldState(),
            mode = null,
            isMatchCase = false,
        )
    }
    // The match a query reaches as it is typed is selected as well, so that the next key typed searches on from it, and
    // so that the sheet closed over it leaves it selected.
    LaunchedEffect(search) {
        snapshotFlow { search.currentMatch.takeIf { search.mode != null } }.filterNotNull().collect(search::select)
    }
    return search
}
