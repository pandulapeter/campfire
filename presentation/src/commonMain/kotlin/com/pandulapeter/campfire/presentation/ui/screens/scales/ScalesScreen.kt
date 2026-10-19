/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.scales

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.scales.ScaleFamily
import com.pandulapeter.campfire.chordpro.scales.Scales
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.mapper.toChordNotation
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.scales_common_heading
import com.pandulapeter.campfire.presentation.resources.scales_heading
import com.pandulapeter.campfire.presentation.resources.scales_instrument_other
import com.pandulapeter.campfire.presentation.resources.scales_search
import com.pandulapeter.campfire.presentation.resources.scales_search_heading
import com.pandulapeter.campfire.presentation.resources.scales_search_no_scale
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.ui.chords.shownChordName
import com.pandulapeter.campfire.presentation.ui.components.EDGE_FADE_SIZE
import com.pandulapeter.campfire.presentation.ui.components.QueryBackHandler
import com.pandulapeter.campfire.presentation.ui.components.pinnedSearchField
import com.pandulapeter.campfire.presentation.ui.components.pinnedSearchTopFade
import com.pandulapeter.campfire.presentation.ui.components.rememberPinnedSearchFocus
import com.pandulapeter.campfire.presentation.ui.scales.scaleName
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.chords.libraryKeys
import com.pandulapeter.campfire.presentation.ui.chrome.TOOL_PAGE_PADDING
import com.pandulapeter.campfire.presentation.ui.components.ScrollToTopOnRequest
import com.pandulapeter.campfire.presentation.ui.components.bounceScrollableContent
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.list.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.rememberRetainedLazyGridState
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentTuning
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.scales.label
import com.pandulapeter.campfire.presentation.ui.scales.scaleDiagramDescription
import com.pandulapeter.campfire.presentation.ui.scales.scaleDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.settingsPage.PageWidthLayout

/**
 * The Scales tab: every scale the app knows, on the instrument in the player's hands and its tuning, which the chips at
 * the top change for the whole app, as on the Chords tab. Unlike chord shapes, a scale needs nothing of an instrument but
 * its open strings, so every stringed instrument draws its scales, and Other draws them on a keyboard with a line saying
 * so. Under the key and the family, a heading and a cell per scale of that family ([scaleLibraryScales]), each opening the
 * scale sheet ([DialogType.Scale]).
 *
 * The search under the controls, pinned once the cells pass under it, finds scales by name and other names across
 * every family ([scaleLibraryResults]); picking a family empties it. Everything is worked out in composition: the scales
 * are a fixed list and each diagram is arithmetic, so the first frame is already the page.
 */
@Composable
internal fun ScalesScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    layout: PageWidthLayout,
    contentPadding: PaddingValues,
) {
    val state = viewModel.scaleLibrary
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val spelling = userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default
    val notation = spelling.notation.toChordNotation()
    val keys = remember(spelling.accidentals) { libraryKeys(spelling.accidentals) }
    val instrument = userPreferences?.instrument ?: UserPreferences.Instrument.GUITAR
    val instrumentTuning = userPreferences?.instrumentTuning
    val rootName = keys[state.keyIndex.coerceIn(keys.indices)]
    val root = ChordProChords.parse(rootName)?.root ?: 0
    val query = state.query
    val names = Scales.all.associateWith { scaleName(it) }
    val scales = remember(state.familyIndex, query, names) { scaleLibraryResults(state.familyIndex, query) { names.getValue(it) } }
    val gridState = rememberRetainedLazyGridState(state.scrollPosition)
    ScrollToTopOnRequest(
        requests = viewModel.navigator.scrollToTopRequests,
        destination = CampfireDestination.Scales,
        gridState = gridState,
        onSelectDestination = viewModel::selectTopLevelDestination,
    )
    val searchFocus = rememberPinnedSearchFocus(gridState)
    val searchPlaceholder = stringResource(Res.string.scales_search)
    val fadeHeight = with(LocalDensity.current) { EDGE_FADE_SIZE.roundToPx() }
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyVerticalGrid(
            modifier = Modifier
                .widthIn(max = layout.pageMaxWidth(sections = 2))
                .fillMaxSize()
                .fadingTopEdge(
                    scrolled = { pinnedSearchTopFade(gridState, fadeHeight) },
                    backgroundColor = MaterialTheme.colorScheme.background,
                )
                .bounceScrollableContent(gridState),
            state = gridState,
            columns = GridCells.Adaptive(minSize = SCALE_CELL_MIN_WIDTH),
            // Nothing above the first row but its own room, as on the Chords tab, which the tools' tabs switch between.
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
                ScaleLibraryControls(
                    instrument = instrument,
                    instrumentTuning = instrumentTuning,
                    notation = notation,
                    keys = keys,
                    selectedKeyIndex = state.keyIndex,
                    selectedFamilyIndex = state.familyIndex,
                    onInstrumentClicked = { viewModel.dialogHost.showDialog(DialogType.Instrument) },
                    onTuningClicked = { viewModel.dialogHost.showDialog(DialogType.Tuning) },
                    onKeySelected = { state.keyIndex = it },
                    onFamilySelected = {
                        state.familyIndex = it
                        state.query = ""
                    },
                )
            }
            pinnedSearchField(
                modifier = searchFocus,
                gridState = gridState,
                query = query,
                placeholder = searchPlaceholder,
                focusRequests = state.searchFocusRequests,
                onQueryChanged = { state.query = it },
            )
            if (instrument == UserPreferences.Instrument.OTHER) {
                item(key = MESSAGE_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = MESSAGE_KEY) {
                    ScaleLibraryMessage(
                        modifier = listItemAnimation(gridState),
                        text = stringResource(Res.string.scales_instrument_other),
                    )
                }
            }
            item(key = HEADING_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = HEADING_KEY) {
                ScaleLibraryHeading(
                    modifier = listItemAnimation(gridState),
                    familyIndex = state.familyIndex,
                    query = query.trim(),
                    hasResults = scales.isNotEmpty(),
                    rootName = rootName,
                    notation = notation,
                )
            }
            items(items = scales, key = { it.id }, contentType = { CELL_CONTENT_TYPE }) { scale ->
                // Remembered for the reason ChordsScreen remembers its shapes: the item is composed again as every
                // scroll starts and ends.
                val geometry = remember(scale, root, instrumentTuning, instrument) {
                    scaleDiagramGeometryOf(scale, root, instrumentTuning?.strings, instrument)
                }
                Box(modifier = listItemAnimation(gridState)) {
                    ScaleLibraryCell(
                        scale = scale,
                        geometry = geometry,
                        description = scaleDiagramDescription(scale, rootName, notation),
                        onClick = { viewModel.dialogHost.showDialog(DialogType.Scale(scaleId = scale.id, rootName = rootName)) },
                    )
                }
            }
        }
    }
    val visibleDialog by viewModel.dialogHost.visibleDialog.collectAsStateWithLifecycle()
    QueryBackHandler(query = query, isDialogShown = visibleDialog != null, onCleared = { state.query = "" })
}

/**
 * What stands over the scales: the family and the key they are in, set the way the controls' titles are, or while a
 * search is typed, what it found them by, or that it found nothing.
 */
@Composable
private fun ScaleLibraryHeading(
    modifier: Modifier = Modifier,
    familyIndex: Int,
    query: String,
    hasResults: Boolean,
    rootName: String,
    notation: ChordNotation,
) = Text(
    modifier = modifier
        .fillMaxWidth()
        .padding(top = 8.dp),
    text = when {
        query.isEmpty() -> stringResource(
            Res.string.scales_heading,
            stringResource(ScaleFamily.entries.getOrNull(familyIndex - 1)?.label ?: Res.string.scales_common_heading),
            shownChordName(rootName, notation),
        )
        hasResults -> textResource(Res.string.scales_search_heading, query, shownChordName(rootName, notation))
        else -> textResource(Res.string.scales_search_no_scale, query)
    },
    style = MaterialTheme.typography.bodyLarge,
    color = if (query.isNotEmpty() && !hasResults) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
)

/** Why the scales are drawn on a keyboard: no instrument has been picked whose strings they could be drawn on. */
@Composable
private fun ScaleLibraryMessage(
    modifier: Modifier = Modifier,
    text: String,
) = Text(
    modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 32.dp, vertical = 8.dp),
    text = text,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
)

private const val CELL_CONTENT_TYPE = "scale"
private const val CONTROLS_KEY = "controls"
private const val HEADING_KEY = "heading"
private const val MESSAGE_KEY = "message"
private val CELL_GAP = 8.dp
