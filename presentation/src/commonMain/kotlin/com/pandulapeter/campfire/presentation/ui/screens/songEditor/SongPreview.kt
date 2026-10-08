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

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.ChordDiagrams
import com.pandulapeter.campfire.presentation.ui.chords.toChordNotation
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.contentEdges
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SectionMotion
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SongInfoEditing
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SongLyrics
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SongLyricsInputs
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.prepareSongLyrics
import com.pandulapeter.campfire.presentation.ui.songLayout.rememberDefaultSectionLabels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

/**
 * The rendered song, kept a beat behind the text so that typing does not re-parse on every keystroke, and parsed away
 * from the main thread.
 *
 * @param scrollState Where the preview is scrolled to, hoisted so that it survives the pane being composed again.
 * @param isSingleColumn Whether the song is stacked in one column (see [SongLyrics]): next to the field, where the
 * preview follows the text being typed. On its own it lays the song out the way the song details screen does.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@Composable
internal fun SongPreview(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    text: State<String>,
    scrollState: ScrollState,
    fontScale: Float,
    chordSpelling: UserPreferences.ChordSpelling,
    contentPadding: PaddingValues,
    isSingleColumn: Boolean,
    songInfoEditing: SongInfoEditing,
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val labels = rememberDefaultSectionLabels(shouldNumberSections = userPreferences?.shouldNumberSections == true)
    val latestChordSpelling by rememberUpdatedState(chordSpelling)
    // Lyrics only mode is about how a song is read, and this preview is here to show what is being written: chords
    // typed into the field opposite it have to appear, or the editor would answer an edit with nothing. The reader's
    // transposition is a way of reading it too, so the preview is in the key the field and the stepper name - the
    // file's own {transpose} still applies, being part of the text.
    fun inputsOf(text: String, spelling: UserPreferences.ChordSpelling) = SongLyricsInputs(
        text = text,
        transposition = 0,
        spelling = spelling,
        shouldShowChords = true,
        labels = labels,
    )
    fun prepare(inputs: SongLyricsInputs) = prepareSongLyrics(
        song = viewModel.renderSong(inputs.text, inputs.transposition, inputs.spelling, writtenIn = viewModel.editorNotation),
        shouldShowChords = inputs.shouldShowChords,
        labels = inputs.labels,
        notation = inputs.spelling.notation.toChordNotation(),
    )
    // The first rendering is built right here, so the preview never opens on an empty frame. Every later one is built
    // away from the main thread once the typing pauses, which is exactly when the next key is likely to come, and
    // the one before it stays on screen until it is ready.
    var preview by remember(text) { mutableStateOf(inputsOf(text.value, chordSpelling).let { it to prepare(it) }) }
    LaunchedEffect(text, labels) {
        snapshotFlow { inputsOf(text.value, latestChordSpelling) }
            .distinctUntilChanged()
            .debounce(PREVIEW_DELAY_MILLIS)
            .filter { it != preview.first }
            .mapLatest { inputs -> inputs to withContext(Dispatchers.Default) { prepare(inputs) } }
            .collect { preview = it }
    }
    val topPadding = 8.dp
    // The keyboard only ever covers the lower part of the preview, which the scroll room below the last line lets the
    // user scroll past, so it decides that room and not the columns: the height the sections are laid out against is
    // the pane's less the resting inset, and the preview keeps its columns while the keyboard comes and goes.
    val restingBottomPadding = WindowInsets.contentEdges.asPaddingValues().calculateBottomPadding() + 32.dp
    BoxWithConstraints(modifier = modifier) {
        SongLyrics(
            modifier = Modifier
                .fillMaxSize()
                .fadingTopEdge(scrollState, MaterialTheme.colorScheme.background)
                .bounceVerticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = topPadding)
                .padding(contentPadding.only(start = true, end = true, bottom = true, extraBottom = 32.dp)),
            model = preview.second,
            availableHeight = maxHeight - topPadding - restingBottomPadding,
            fontScale = fontScale,
            sectionMotion = SectionMotion.NONE,
            isSingleColumn = isSingleColumn,
            isSongInfoShown = true,
            songInfoEditing = songInfoEditing,
            // Whatever the two switches and the instrument in Settings say: what is being written is what is shown.
            chordDiagrams = remember { ChordDiagrams(instrument = ChordInstrument.GUITAR, showsDefinitionsOnly = true, notation = viewModel.editorNotation.toChordNotation()) },
        )
    }
}

private const val PREVIEW_DELAY_MILLIS = 150L
