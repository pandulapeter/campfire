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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.language.WritingDirection
import com.pandulapeter.campfire.chordpro.language.textDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

/**
 * The direction the song in the editor's field is read in (`ChordProSong.textDirection`), which the field's lines take
 * the way the reader's do. It needs the parsed song, so the first one is worked out right here, so the field never
 * opens in the wrong direction, and every later one away from the main thread once the typing pauses: a song turns
 * around only when enough of its lyrics have changed script, never on the keystroke itself.
 *
 * Called once per opened song, by the editor next to its [text], not by the field: the panes compose the field afresh on
 * every switch between Edit, Preview, and Split, and a long song parsed on each of them would stutter.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@Composable
internal fun rememberEditorTextDirection(text: State<String>): State<WritingDirection> {
    val parsed = remember(text) { mutableStateOf(text.value to directionOf(text.value)) }
    LaunchedEffect(text) {
        snapshotFlow { text.value }
            .distinctUntilChanged()
            .debounce(DIRECTION_DELAY_MILLIS)
            .filter { it != parsed.value.first }
            .mapLatest { it to withContext(Dispatchers.Default) { directionOf(it) } }
            .collect { parsed.value = it }
    }
    // Derived, so that whoever reads it is composed again when the direction turns, not on every parse.
    return remember(parsed) { derivedStateOf { parsed.value.second } }
}

private fun directionOf(text: String) = ChordProParser.parse(text).textDirection

private const val DIRECTION_DELAY_MILLIS = 150L
