/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.chords

import com.pandulapeter.campfire.presentation.ui.chords.libraryKeys
import com.pandulapeter.campfire.presentation.ui.chrome.TOOL_GROUP_GAP
import com.pandulapeter.campfire.presentation.ui.chrome.TOOL_PAGE_PADDING
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.chords_common
import com.pandulapeter.campfire.presentation.resources.chords_instrument_other
import com.pandulapeter.campfire.presentation.resources.chords_instrument_without_diagrams
import com.pandulapeter.campfire.presentation.resources.chords_search
import com.pandulapeter.campfire.presentation.resources.chords_search_heading
import com.pandulapeter.campfire.presentation.resources.chords_search_no_chord
import com.pandulapeter.campfire.presentation.ui.components.QueryBackHandler
import com.pandulapeter.campfire.presentation.ui.components.pinnedSearchField
import com.pandulapeter.campfire.presentation.ui.components.pinnedSearchTopFade
import com.pandulapeter.campfire.presentation.ui.components.rememberPinnedSearchFocus
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.EDGE_FADE_SIZE
import com.pandulapeter.campfire.presentation.ui.chords.ChordLibraryCell
import com.pandulapeter.campfire.presentation.ui.chords.LibraryKeyStepper
import com.pandulapeter.campfire.presentation.ui.chords.SongChord
import com.pandulapeter.campfire.presentation.ui.chords.chordCellMinWidth
import com.pandulapeter.campfire.presentation.ui.chords.selectShape
import com.pandulapeter.campfire.presentation.ui.chords.shownChordName
import com.pandulapeter.campfire.presentation.mapper.toChordNotation
import com.pandulapeter.campfire.presentation.ui.components.ScrollToTopOnRequest
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.list.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.rememberRetainedLazyGridState
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.instrument.chordTuning
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentLabel
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentTuning
import com.pandulapeter.campfire.presentation.ui.instrument.storedChordShapes
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.components.bounceScrollableContent
import com.pandulapeter.campfire.presentation.ui.settingsPage.PageWidthLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Chords tab: every chord the app can draw, for the instrument in the player's hands and its tuning, which the chip
 * at the top of the page changes for the songs and the tuner too, since a player browsing ukulele chords has a ukulele in
 * hand. An instrument without shapes gets a line naming it instead, which points at the chip. Under them the search
 * with the key beside it, and the common chords of that key under a heading that calls them that, in the order of [CHORD_LIBRARY_QUALITIES], each
 * a cell with the shape the songs draw it with; a search that names a chord the key does not list finds it above them
 * ([chordLibraryResults]). A tap on a cell opens every shape of that chord ([DialogType.ChordReference]), the one
 * settled on becoming the player's for that chord in every song.
 *
 * The instrument scrolls with the chords rather than staying pinned, so that a phone on its side still has room for a
 * row of diagrams under the keyboard; only the search line stays, which is the way to any chord the key leaves out.
 */
@Composable
internal fun ChordsScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    layout: PageWidthLayout,
    contentPadding: PaddingValues,
) {
    val state = viewModel.chordLibrary
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val spelling = userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default
    val notation = spelling.notation.toChordNotation()
    val keys = remember(spelling.accidentals) { libraryKeys(spelling.accidentals) }
    val tuning = userPreferences?.chordTuning()
    val instrument = userPreferences?.instrument ?: UserPreferences.Instrument.GUITAR
    val keyIndex = state.keyIndex
    val query = state.query
    // What the tables answer alone is there from the first frame; a chord only the search finds keeps the chords that
    // were on screen up until it has been found, rather than emptying the page in between. They are held with the tuning
    // they were found for, since each carries that tuning's shape, which the new strings would play as another chord.
    // The session keeps the last results too, so that a return opens on them, and with them in the first layout the
    // retained scroll position has the rows to land on, rather than being clamped to the top of an empty page.
    var shown by remember {
        mutableStateOf(
            tuning?.let {
                initialChordLibraryShown(state.shown, ChordLibraryInputs(keys, keyIndex, query, notation, it)) {
                    chordLibraryResults(keys, keyIndex, query, notation, it, searchesShapes = false)
                }
            },
        )
    }
    SideEffect { state.shown = shown }
    LaunchedEffect(keys, keyIndex, query, notation, tuning) {
        shown = tuning?.let {
            ChordLibraryShown(
                inputs = ChordLibraryInputs(keys, keyIndex, query, notation, it),
                results = chordLibraryResults(keys, keyIndex, query, notation, it, searchesShapes = false)
                    ?: withContext(Dispatchers.Default) { chordLibraryResults(keys, keyIndex, query, notation, it) },
            )
        }
    }
    val gridState = rememberRetainedLazyGridState(state.scrollPosition)
    ScrollToTopOnRequest(
        requests = viewModel.navigator.scrollToTopRequests,
        destination = CampfireDestination.Chords,
        gridState = gridState,
        onSelectDestination = viewModel::selectTopLevelDestination,
    )
    val searchFocus = rememberPinnedSearchFocus(gridState)
    val searchPlaceholder = stringResource(Res.string.chords_search)
    val density = LocalDensity.current
    val backgroundColor = MaterialTheme.colorScheme.background
    val fadeHeight = with(density) { EDGE_FADE_SIZE.roundToPx() }
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyVerticalGrid(
            modifier = Modifier
                .widthIn(max = layout.animatedPageMaxWidth(sections = 2))
                .fillMaxSize()
                .fadingTopEdge(
                    scrolled = { pinnedSearchTopFade(gridState, fadeHeight) },
                    backgroundColor = backgroundColor,
                )
                .bounceScrollableContent(gridState),
            state = gridState,
            columns = GridCells.Adaptive(minSize = chordCellMinWidth(shown?.tuning?.instrument ?: tuning?.instrument)),
            // Nothing above the first row but its own room, so that its title starts where the Metronome's beats and the
            // Tuner's note do, which the tools' tabs switch between in place.
            contentPadding = contentPadding.only(
                start = true,
                end = true,
                bottom = true,
                extraStart = TOOL_PAGE_PADDING,
                extraEnd = TOOL_PAGE_PADDING,
                extraBottom = TOOL_PAGE_PADDING,
            ),
            horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
            verticalArrangement = Arrangement.spacedBy(CELL_GAP),
        ) {
            item(key = CONTROLS_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = CONTROLS_KEY) {
                ChordLibraryControls(
                    instrument = instrument,
                    instrumentTuning = userPreferences?.instrumentTuning,
                    notation = notation,
                    onInstrumentClicked = { viewModel.dialogHost.showDialog(DialogType.Instrument) },
                    onTuningClicked = { viewModel.dialogHost.showDialog(DialogType.Tuning) },
                )
            }
            pinnedSearchField(
                modifier = searchFocus,
                gridState = gridState,
                query = query,
                placeholder = searchPlaceholder,
                focusRequests = state.searchFocusRequests,
                onQueryChanged = { state.query = it },
            ) {
                LibraryKeyStepper(
                    keys = keys,
                    selectedIndex = keyIndex,
                    notation = notation,
                    onSelected = { state.keyIndex = it },
                )
            }
            val held = shown
            // An instrument without shapes says so at once, whatever the last tuning's results still hold.
            if (tuning == null) {
                item(key = MESSAGE_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = MESSAGE_KEY) {
                    ChordLibraryMessage(
                        modifier = listItemAnimation(gridState),
                        text = if (instrument == UserPreferences.Instrument.OTHER) {
                            stringResource(Res.string.chords_instrument_other)
                        } else {
                            textResource(Res.string.chords_instrument_without_diagrams, instrumentLabel(instrument))
                        },
                    )
                }
            } else if (held != null) {
                when (val results = held.results) {
                    null -> Unit

                    is ChordLibraryResults.NoChord -> item(key = MESSAGE_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = MESSAGE_KEY) {
                        ChordLibraryMessage(
                            modifier = listItemAnimation(gridState),
                            text = textResource(Res.string.chords_search_no_chord, results.query),
                        )
                    }

                    is ChordLibraryResults.Chords -> {
                        val heldTuning = held.tuning
                        val storedShapes = userPreferences?.storedChordShapes(heldTuning).orEmpty()
                        // Cells held from another tuning are on their way out: their chords name that tuning's shapes.
                        val isCurrent = heldTuning == tuning
                        val cell: @Composable LazyGridItemScope.(SongChord) -> Unit = { chord ->
                            // The item is composed again as every scroll starts and ends (listItemAnimation), so the shape is
                            // remembered and the placement animation goes on a box of its own: on the cell, it would be a new
                            // modifier each time, and every visible diagram would be composed again with it.
                            val selection = remember(chord, heldTuning, storedShapes) { selectShape(chord, heldTuning, storedShapes) }
                            Box(modifier = listItemAnimation(gridState)) {
                                ChordLibraryCell(
                                    chord = chord,
                                    tuning = heldTuning,
                                    selection = selection,
                                    onClick = { if (isCurrent) viewModel.dialogHost.showDialog(DialogType.ChordReference(chord)) },
                                )
                            }
                        }
                        results.searched?.let { chord ->
                            item(key = chord.name to chord.chord.id, contentType = CELL_CONTENT_TYPE) { cell(chord) }
                        }
                        if (results.chords.isNotEmpty()) {
                            item(key = HEADING_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = HEADING_KEY) {
                                ChordLibraryHeading(
                                    modifier = listItemAnimation(gridState),
                                    isAfterCell = results.searched != null,
                                    text = chordLibraryHeading(held.inputs),
                                )
                            }
                        }
                        items(items = results.chords, key = { it.name to it.chord.id }, contentType = { CELL_CONTENT_TYPE }) { cell(it) }
                    }
                }
            }
        }
    }
    val visibleDialog by viewModel.dialogHost.visibleDialog.collectAsStateWithLifecycle()
    QueryBackHandler(query = query, isDialogShown = visibleDialog != null, onCleared = { state.query = "" })
}

/**
 * What stands over the key's chords: that they are its common ones, so that nobody takes them for every chord there is,
 * or while a search is typed, that they are the ones of the key it found, as on the Scales tab, set the way the controls'
 * titles are.
 */
@Composable
private fun ChordLibraryHeading(
    modifier: Modifier = Modifier,
    isAfterCell: Boolean,
    text: String,
) = Text(
    // A group of its own (TOOL_GROUP_GAP), which the search's padding parts from the field, and its own from the cells.
    modifier = modifier
        .fillMaxWidth()
        .padding(top = if (isAfterCell) TOOL_GROUP_GAP - CELL_GAP else 0.dp, bottom = TOOL_GROUP_GAP - CELL_GAP),
    text = text,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurface,
)

/** The heading's words: the key's common chords, or while a search is typed, the ones of the key it found. */
@Composable
private fun chordLibraryHeading(inputs: ChordLibraryInputs): String {
    val keyName = shownChordName(inputs.key, inputs.notation)
    val typed = inputs.query.trim()
    return if (typed.isEmpty()) {
        stringResource(Res.string.chords_common, keyName)
    } else {
        textResource(Res.string.chords_search_heading, typed, keyName)
    }
}

/** What stands in place of the chords: why there are none to show. */
@Composable
private fun ChordLibraryMessage(
    modifier: Modifier = Modifier,
    text: String,
) = Text(
    modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 32.dp, vertical = 24.dp),
    text = text,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
)

private const val CELL_CONTENT_TYPE = "chord"
private const val CONTROLS_KEY = "controls"
private const val HEADING_KEY = "heading"
private const val MESSAGE_KEY = "message"
private val CELL_GAP = 8.dp
