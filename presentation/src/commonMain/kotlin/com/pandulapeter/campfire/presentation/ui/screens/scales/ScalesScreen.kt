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
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.scales.Scale
import com.pandulapeter.campfire.chordpro.scales.ScaleFamily
import com.pandulapeter.campfire.chordpro.scales.Scales
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.mapper.toChordNotation
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.scale_family_common
import com.pandulapeter.campfire.presentation.resources.scales_instrument_other
import com.pandulapeter.campfire.presentation.resources.scales_search
import com.pandulapeter.campfire.presentation.resources.scales_search_heading
import com.pandulapeter.campfire.presentation.resources.scales_search_no_scale
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.ui.chords.LibraryKeyStepper
import com.pandulapeter.campfire.presentation.ui.chords.shownChordName
import com.pandulapeter.campfire.presentation.ui.components.EDGE_FADE_SIZE
import com.pandulapeter.campfire.presentation.ui.components.QueryBackHandler
import com.pandulapeter.campfire.presentation.ui.components.pinnedSearchField
import com.pandulapeter.campfire.presentation.ui.components.pinnedSearchTopFade
import com.pandulapeter.campfire.presentation.ui.components.rememberPinnedSearchFocus
import com.pandulapeter.campfire.presentation.ui.scales.scaleName
import com.pandulapeter.campfire.presentation.ui.scales.scaleTitle
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.chords.libraryKeys
import com.pandulapeter.campfire.presentation.ui.chrome.TOOL_GROUP_GAP
import com.pandulapeter.campfire.presentation.ui.chrome.TOOL_PAGE_PADDING
import com.pandulapeter.campfire.presentation.ui.components.ScrollToTopOnRequest
import com.pandulapeter.campfire.presentation.ui.components.bleedHorizontally
import com.pandulapeter.campfire.presentation.ui.components.bounceScrollableContent
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.list.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.rememberRetainedLazyGridState
import com.pandulapeter.campfire.presentation.ui.components.rememberRetainedScrollState
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentTuning
import com.pandulapeter.campfire.presentation.ui.instrument.scaleLibraryCapo
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.scales.label
import com.pandulapeter.campfire.presentation.ui.scales.scaleAliases
import com.pandulapeter.campfire.presentation.ui.scales.scaleDiagramDescription
import com.pandulapeter.campfire.presentation.ui.scales.scaleDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.settingsPage.PageWidthLayout
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * The Scales tab: every scale the app knows, on the instrument in the player's hands and its tuning, which the chips at
 * the top change for the whole app, as on the Chords tab. Unlike chord shapes, a scale needs nothing of an instrument but
 * its open strings, so every stringed instrument draws its scales, and Other draws them on a keyboard with a line saying
 * so. Under the search, with the key beside it, the families, and a cell per scale of the one picked
 * ([scaleLibraryScales]), each opening the scale sheet ([DialogType.Scale]). Beside the instrument, the capo the Scales and
 * the Chords tabs share ([scaleLibraryCapo]): the scales keep the names they sound by, and the frets below it are veiled,
 * on the cells and in the sheet, as under a song's capo.
 *
 * The search under the controls, pinned once the cells pass under it, finds scales by name and other names across
 * every family ([scaleLibraryResults]), and a search that starts with a note finds them on that note, whatever the key;
 * picking a family empties it. Everything is worked out in composition: the scales
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
    val shownCapo = userPreferences?.scaleLibraryCapo()
    val capo = shownCapo ?: 0
    val keyName = keys[state.keyIndex.coerceIn(keys.indices)]
    val query = state.query
    val results = rememberScaleLibraryResults(state.familyIndex, query, keyName, notation)
    val rootName = results.rootName
    val root = ChordProChords.parse(rootName)?.root ?: 0
    val gridState = rememberRetainedLazyGridState(state.scrollPosition)
    val familyScrollState = rememberRetainedScrollState(state.familyScrollPosition)
    val familyLabels = listOf(stringResource(Res.string.scale_family_common)) + ScaleFamily.entries.map { stringResource(it.label) }
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
        contentAlignment = Alignment.TopStart,
    ) {
        LazyVerticalGrid(
            modifier = Modifier
                .widthIn(max = layout.animatedPageMaxWidth(sections = 2))
                .fillMaxSize()
                .fadingTopEdge(
                    scrolled = { pinnedSearchTopFade(gridState, fadeHeight) },
                    backgroundColor = MaterialTheme.colorScheme.background,
                )
                .bounceScrollableContent(gridState),
            state = gridState,
            columns = GridCells.Adaptive(minSize = SCALE_CELL_MIN_WIDTH),
            // Nothing above the first row but its own room, as on the Chords tab, which the tools' button group switches between.
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
            controlsItem(viewModel = viewModel, instrument = instrument, instrumentTuning = instrumentTuning, notation = notation, capo = shownCapo)
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
                    selectedIndex = state.keyIndex,
                    notation = notation,
                    onSelected = { state.keyIndex = it },
                )
            }
            item(key = HEADER_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = HEADER_KEY) {
                ScaleLibraryHeader(
                    modifier = listItemAnimation(gridState),
                    query = query.trim(),
                    results = results,
                    notation = notation,
                    familyLabels = familyLabels,
                    selectedFamilyIndex = state.familyIndex,
                    familyScrollState = familyScrollState,
                    contentPadding = contentPadding,
                    onFamilySelected = state::selectFamily,
                )
            }
            if (instrument == UserPreferences.Instrument.OTHER) {
                item(key = MESSAGE_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = MESSAGE_KEY) {
                    ScaleLibraryMessage(
                        modifier = listItemAnimation(gridState),
                        text = stringResource(Res.string.scales_instrument_other),
                    )
                }
            }
            items(items = results.scales, key = { it.id }, contentType = { CELL_CONTENT_TYPE }) { scale ->
                ScaleLibraryItem(
                    modifier = listItemAnimation(gridState),
                    scale = scale,
                    root = root,
                    rootName = rootName,
                    notation = notation,
                    instrument = instrument,
                    instrumentTuning = instrumentTuning,
                    capo = capo,
                    onClick = { viewModel.dialogHost.showDialog(DialogType.Scale(scaleId = scale.id, rootName = rootName, capo = capo)) },
                )
            }
        }
    }
    val visibleDialog by viewModel.dialogHost.visibleDialog.collectAsStateWithLifecycle()
    QueryBackHandler(query = query, isDialogShown = visibleDialog != null, onCleared = { state.query = "" })
}

/** The controls' item, the instrument and the capo, each opening its sheet or written at once as the app-wide preference. */
private fun LazyGridScope.controlsItem(
    viewModel: CampfireViewModel,
    instrument: UserPreferences.Instrument,
    instrumentTuning: InstrumentTuning?,
    notation: ChordNotation,
    capo: Int?,
) = item(key = CONTROLS_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = CONTROLS_KEY) {
    ScaleLibraryControls(
        instrument = instrument,
        instrumentTuning = instrumentTuning,
        notation = notation,
        onInstrumentClicked = { viewModel.dialogHost.showDialog(DialogType.Instrument) },
        onTuningClicked = { viewModel.dialogHost.showDialog(DialogType.Tuning) },
        capo = capo,
        onCapoStep = viewModel.preferencesController::stepToolCapo,
        onCapoReset = viewModel.preferencesController::resetToolCapo,
    )
}

/** [scaleLibraryResults] for the names and other names of the scales in the app's language. */
@Composable
private fun rememberScaleLibraryResults(familyIndex: Int, query: String, keyName: String, notation: ChordNotation): ScaleLibraryResults {
    val names = Scales.all.associateWith { scaleName(it) }
    val aliases = Scales.all.associateWith { scaleAliases(it) }
    return remember(familyIndex, query, keyName, notation, names, aliases) {
        scaleLibraryResults(familyIndex, query, keyName, notation, names::getValue, aliases::get)
    }
}

/** A scale's cell in the key, on the instrument in the player's hands, with the grid's placement animation as [modifier]. */
@Composable
private fun ScaleLibraryItem(
    modifier: Modifier = Modifier,
    scale: Scale,
    root: Int,
    rootName: String,
    notation: ChordNotation,
    instrument: UserPreferences.Instrument,
    instrumentTuning: InstrumentTuning?,
    capo: Int,
    onClick: () -> Unit,
) {
    // Remembered for the reason ChordsScreen remembers its shapes: the item is composed again as every scroll starts and
    // ends, and the placement animation goes on a box of its own for the same reason.
    val geometry = remember(scale, root, instrumentTuning, instrument, capo) {
        scaleDiagramGeometryOf(scale, root, instrumentTuning?.strings, instrument, capo)
    }
    Box(modifier = modifier) {
        ScaleLibraryCell(
            title = scaleTitle(scale, rootName, notation),
            geometry = geometry,
            description = scaleDiagramDescription(scale, rootName, notation),
            onClick = onClick,
        )
    }
}

/**
 * What stands over the scales: the families to pick from, in one line under the search, or while a search is typed, what
 * it found them by, or that it found nothing, since a search looks across every family. The three fade into one another
 * and the item's height follows, so the cells under it move rather than jump; the words of a search change in place as
 * they are typed. The search's padding parts it from the field, and its own from the cells (TOOL_GROUP_GAP).
 */
@Composable
private fun ScaleLibraryHeader(
    modifier: Modifier = Modifier,
    query: String,
    results: ScaleLibraryResults,
    notation: ChordNotation,
    familyLabels: List<String>,
    selectedFamilyIndex: Int,
    familyScrollState: ScrollState,
    contentPadding: PaddingValues,
    onFamilySelected: (Int) -> Unit,
) = AnimatedContent(
    modifier = modifier.fillMaxWidth(),
    targetState = when {
        query.isEmpty() -> ScaleLibraryHeaderContent.FAMILIES
        results.scales.isNotEmpty() -> ScaleLibraryHeaderContent.FOUND
        else -> ScaleLibraryHeaderContent.NOTHING_FOUND
    },
    transitionSpec = { fadeIn() togetherWith fadeOut() },
    label = "scaleLibraryHeader",
) { content ->
    when (content) {
        ScaleLibraryHeaderContent.FAMILIES -> {
            val layoutDirection = LocalLayoutDirection.current
            val startPadding = contentPadding.calculateStartPadding(layoutDirection) + TOOL_PAGE_PADDING
            val endPadding = contentPadding.calculateEndPadding(layoutDirection) + TOOL_PAGE_PADDING
            ScaleFamilyRow(
                modifier = Modifier
                    .bleedHorizontally(start = startPadding, end = endPadding)
                    .padding(bottom = TOOL_GROUP_GAP - CELL_GAP),
                labels = familyLabels,
                selectedIndex = selectedFamilyIndex,
                scrollState = familyScrollState,
                startPadding = startPadding,
                endPadding = endPadding,
                onSelected = onFamilySelected,
            )
        }
        ScaleLibraryHeaderContent.FOUND -> Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = TOOL_GROUP_GAP - CELL_GAP),
            text = textResource(Res.string.scales_search_heading, results.query, shownChordName(results.rootName, notation)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // Set as the Chords tab sets its own search's empty state, which the tools' button group switches between.
        ScaleLibraryHeaderContent.NOTHING_FOUND -> Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            text = textResource(Res.string.scales_search_no_scale, query),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private enum class ScaleLibraryHeaderContent { FAMILIES, FOUND, NOTHING_FOUND }

/** Why the scales are drawn on a keyboard: no instrument has been picked whose strings they could be drawn on. */
@Composable
private fun ScaleLibraryMessage(
    modifier: Modifier = Modifier,
    text: String,
) = Text(
    modifier = modifier
        .fillMaxWidth()
        .padding(start = 32.dp, end = 32.dp, bottom = TOOL_GROUP_GAP - CELL_GAP),
    text = text,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
)

private const val CELL_CONTENT_TYPE = "scale"
private const val CONTROLS_KEY = "controls"
private const val HEADER_KEY = "header"
private const val MESSAGE_KEY = "message"
private val CELL_GAP = 8.dp
