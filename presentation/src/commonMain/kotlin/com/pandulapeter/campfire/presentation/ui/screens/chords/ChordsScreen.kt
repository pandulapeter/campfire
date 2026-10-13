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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.chords_instrument_other
import com.pandulapeter.campfire.presentation.resources.chords_instrument_without_diagrams
import com.pandulapeter.campfire.presentation.resources.chords_search_no_chord
import com.pandulapeter.campfire.presentation.resources.chords_search_no_shape
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.chords.selectShape
import com.pandulapeter.campfire.presentation.ui.chords.toChordNotation
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.rememberRetainedLazyGridState
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.instrument.chordTuning
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentLabel
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentTuning
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.platform.bounceScrollableContent
import com.pandulapeter.campfire.presentation.ui.screens.settings.PAGE_TOP_PADDING
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsWidthLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

/**
 * The Chords tab: every chord the app can draw, for the instrument in the player's hands and its tuning, which the chip
 * at the top of the page changes for the songs and the tuner too, since a player browsing ukulele chords has a ukulele in
 * hand. An instrument without shapes gets a line naming it instead, which points at the chip. Under them the chords of one key, in the order of [CHORD_LIBRARY_QUALITIES], each a cell with the shape the
 * songs draw it with, or a search for one chord by name ([chordLibraryResults]). A tap on a cell opens every shape of
 * that chord ([DialogType.ChordReference]), the one settled on becoming the player's for that chord in every song.
 *
 * The controls scroll with the chords rather than staying pinned, so that a phone on its side still has room for a row
 * of diagrams under the keyboard.
 */
@Composable
internal fun ChordsScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    layout: SettingsWidthLayout,
    contentPadding: PaddingValues,
) {
    val state = viewModel.chordLibrary
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val spelling = userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default
    val notation = spelling.notation.toChordNotation()
    val keys = remember(spelling.accidentals) { chordLibraryKeys(spelling.accidentals) }
    val tuning = userPreferences?.chordTuning()
    val instrument = userPreferences?.instrument ?: UserPreferences.Instrument.GUITAR
    val keyIndex = state.keyIndex
    val query = state.query
    // What the tables answer alone is there from the first frame; a chord only the search finds keeps the chords that
    // were on screen up until it has been found, rather than emptying the page in between. They are held with the tuning
    // they were found for, since each carries that tuning's shape, which the new strings would play as another chord.
    var shown by remember {
        mutableStateOf(tuning?.let { ChordLibraryShown(it, chordLibraryResults(keys, keyIndex, query, notation, it, searchesShapes = false)) })
    }
    LaunchedEffect(keys, keyIndex, query, notation, tuning) {
        shown = tuning?.let {
            ChordLibraryShown(
                tuning = it,
                results = chordLibraryResults(keys, keyIndex, query, notation, it, searchesShapes = false)
                    ?: withContext(Dispatchers.Default) { chordLibraryResults(keys, keyIndex, query, notation, it) },
            )
        }
    }
    val gridState = rememberRetainedLazyGridState(state.scrollPosition)
    LaunchedEffect(viewModel, gridState) {
        // Latest, for the reason the settings screen gives.
        viewModel.scrollToTopRequests.collectLatest {
            if (it.destination == CampfireDestination.Chords) {
                if (gridState.canScrollBackward) gridState.animateScrollToItem(0) else it.destinationWhenAtTop?.let(viewModel::selectTopLevelDestination)
            }
        }
    }
    val backgroundColor = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyVerticalGrid(
            modifier = Modifier
                .widthIn(max = layout.pageMaxWidth(sections = 2))
                .fillMaxSize()
                .fadingTopEdge(
                    scrolled = { if (gridState.firstVisibleItemIndex > 0) Int.MAX_VALUE else gridState.firstVisibleItemScrollOffset },
                    backgroundColor = backgroundColor,
                )
                .bounceScrollableContent(gridState),
            state = gridState,
            columns = GridCells.Adaptive(minSize = chordCellMinWidth(shown?.tuning?.instrument ?: tuning?.instrument)),
            contentPadding = contentPadding.only(
                start = true,
                end = true,
                bottom = true,
                extraStart = CHORDS_PAGE_PADDING,
                extraTop = PAGE_TOP_PADDING,
                extraEnd = CHORDS_PAGE_PADDING,
                extraBottom = CHORDS_PAGE_PADDING,
            ),
            horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
            verticalArrangement = Arrangement.spacedBy(CELL_GAP),
        ) {
            item(key = CONTROLS_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = CONTROLS_KEY) {
                ChordLibraryControls(
                    instrument = instrument,
                    instrumentTuning = userPreferences?.instrumentTuning,
                    notation = notation,
                    keys = keys,
                    selectedKeyIndex = keyIndex,
                    query = query,
                    onInstrumentClicked = { viewModel.showDialog(DialogType.Instrument) },
                    onTuningClicked = { viewModel.showDialog(DialogType.Tuning) },
                    onKeySelected = { state.keyIndex = it },
                    onQueryChanged = { state.query = it },
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

                    is ChordLibraryResults.NoShape -> item(key = MESSAGE_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = MESSAGE_KEY) {
                        ChordLibraryMessage(
                            modifier = listItemAnimation(gridState),
                            text = textResource(Res.string.chords_search_no_shape, results.name),
                        )
                    }

                    is ChordLibraryResults.Chords -> {
                        val heldTuning = held.tuning
                        val storedShapes = userPreferences?.chordVoicings?.get(heldTuning.voicingKey).orEmpty()
                        // Cells held from another tuning are on their way out: their chords name that tuning's shapes.
                        val isCurrent = heldTuning == tuning
                        items(items = results.chords, key = { it.name to it.chord.id }, contentType = { CELL_CONTENT_TYPE }) { chord ->
                            ChordLibraryCell(
                                modifier = listItemAnimation(gridState),
                                chord = chord,
                                tuning = heldTuning,
                                selection = selectShape(chord, heldTuning, storedShapes),
                                onClick = { if (isCurrent) viewModel.showDialog(DialogType.ChordReference(chord)) },
                            )
                        }
                    }
                }
            }
        }
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
private const val MESSAGE_KEY = "message"
private val CELL_GAP = 8.dp
