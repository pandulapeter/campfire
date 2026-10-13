/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.chords.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.chordpro.model.ChordVoicing
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_next
import com.pandulapeter.campfire.presentation.resources.ic_previous
import com.pandulapeter.campfire.presentation.resources.song_details_chord_defined
import com.pandulapeter.campfire.presentation.resources.song_details_chord_defined_moved
import com.pandulapeter.campfire.presentation.resources.song_details_chord_shape_next
import com.pandulapeter.campfire.presentation.resources.song_details_chord_shape_position
import com.pandulapeter.campfire.presentation.resources.song_details_chord_shape_position_description
import com.pandulapeter.campfire.presentation.resources.song_details_chord_shape_previous
import com.pandulapeter.campfire.presentation.ui.chords.ChordCell
import com.pandulapeter.campfire.presentation.ui.chords.SelectedShape
import com.pandulapeter.campfire.presentation.ui.chords.SongChord
import com.pandulapeter.campfire.presentation.ui.chords.chordCellDescription
import com.pandulapeter.campfire.presentation.ui.chords.chordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.chords.emptyChordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.components.ChordDiagram
import com.pandulapeter.campfire.presentation.ui.components.Stepper
import com.pandulapeter.campfire.presentation.ui.components.textResource
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

/**
 * The chord's letters where the page counts it, its shapes as pages of one pager, its notes, and where it has more than
 * one shape, a stepper reading which page it is on and turning it, going around like the transposition's. It is never
 * highlighted and has no reset: which shape is the app's first says nothing to a player who only picks the one their
 * hands play. A chord the song defines has one page, and says so in place of the stepper.
 */
@Composable
internal fun ChordShapePager(
    chord: SongChord,
    tuning: ChordTuning,
    notation: ChordNotation,
    selection: SelectedShape,
    onShapeSelected: (ChordVoicing?) -> Unit,
) {
    // The search for every shape of a chord is quick, but not quick enough for the frame the sheet opens on.
    val shapes by produceState<List<ChordVoicing>?>(null, chord.chord, tuning, selection.source == SelectedShape.Source.DEFINED) {
        value = if (selection.source == SelectedShape.Source.DEFINED) emptyList() else withContext(Dispatchers.Default) { ChordVoicings.all(chord.chord, tuning) }
    }
    // The pages are decided once the shapes are there and kept while the sheet is up: a stored shape the app does not list
    // is still the player's, and comes first, but paging away from it must not take its page out from under the pager.
    val options = remember(shapes) {
        shapes?.let { all ->
            val current = selection.shape
            when {
                selection.source == SelectedShape.Source.DEFINED || all.isEmpty() -> listOfNotNull(current)
                current == null || all.any { it.sameShapeAs(current) } -> all
                else -> listOf(current) + all
            }
        }
    }
    chord.letterName?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    // A chord the tables have no shape for has no page to draw, only the empty frame its cell has on the page.
    if (options.isNullOrEmpty()) {
        ChordShapePlaceholder(instrument = tuning.instrument, chordName = chord.name)
        return
    }
    val initialPage = options.indexOfFirst { selection.shape != null && it.sameShapeAs(selection.shape) }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { options.size }
    // The app's own first shape is never stored as the player's: settling on it takes the chord's entry out, so that the
    // app's default is what plays there, rather than a copy of it that nothing would ever update.
    val defaultShape = shapes?.firstOrNull()
    val currentOnShapeSelected by rememberUpdatedState(onShapeSelected)
    LaunchedEffect(pagerState, options) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { page ->
            currentOnShapeSelected(options[page].takeUnless { defaultShape?.sameShapeAs(it) == true })
        }
    }
    HorizontalPager(
        modifier = Modifier.fillMaxWidth(),
        state = pagerState,
        // The next shape is drawn already, so that a swipe shows it coming rather than an empty page.
        beyondViewportPageCount = 1,
    ) { page ->
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            ChordShapeDiagram(
                chord = chord,
                tuning = tuning,
                shape = options[page],
            )
        }
    }
    ChordNotes(
        text = (chord.spelling?.let { ChordProChords.spelledNoteNames(it, notation) } ?: ChordProChords.noteNames(chord.chord, notation)).joinToString(" "),
    )
    ChordShapeStepper(
        chordName = chord.name,
        selection = selection,
        pagerState = pagerState,
    )
}

/** The diagram of one page: [shape] of [chord], with the fingers that hold it. */
@Composable
private fun ChordShapeDiagram(
    chord: SongChord,
    tuning: ChordTuning,
    shape: ChordVoicing?,
) = ChordDiagram(
    modifier = Modifier.diagramSize(tuning.instrument),
    geometry = remember(shape, tuning, chord.chord.root) {
        shape?.let { chordDiagramGeometryOf(it, tuning, chord.chord.root) } ?: emptyChordDiagramGeometryOf(tuning.instrument)
    },
    description = chordCellDescription(
        ChordCell(
            name = chord.name,
            letterName = chord.letterName,
            tuning = tuning,
            root = chord.chord.root,
            selection = SelectedShape(shape, SelectedShape.Source.PLAYER),
        ),
    ),
    showsFingers = true,
)

/** The tuning the shapes are drawn for, where it is not the standard one. */
@Composable
internal fun ChordTuningLine(text: String) = Text(
    modifier = Modifier.padding(bottom = 4.dp),
    text = text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
)

@Composable
private fun ChordNotes(text: String) = Text(
    modifier = Modifier.padding(top = 8.dp),
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
)

/**
 * Which page the pager is on, read as the finger moves it, and the two buttons that turn it, for a mouse, a keyboard and
 * a screen reader; or where the song defines the chord, that it does.
 */
@Composable
private fun ChordShapeStepper(
    chordName: String,
    selection: SelectedShape,
    pagerState: PagerState,
) {
    val coroutineScope = rememberCoroutineScope()
    val pageCount = pagerState.pageCount
    when {
        selection.source == SelectedShape.Source.DEFINED -> Text(
            modifier = Modifier.padding(top = 12.dp).heightIn(min = STEPPER_PLACEHOLDER_HEIGHT),
            text = if (selection.movedBy == 0) {
                stringResource(Res.string.song_details_chord_defined)
            } else {
                pluralStringResource(Res.plurals.song_details_chord_defined_moved, abs(selection.movedBy), abs(selection.movedBy))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        pageCount > 1 -> {
            val page = pagerState.currentPage
            Stepper(
                modifier = Modifier.padding(top = 12.dp),
                value = stringResource(Res.string.song_details_chord_shape_position, page + 1, pageCount),
                valueDescription = stringResource(Res.string.song_details_chord_shape_position_description, page + 1, pageCount),
                isDefault = true,
                decreaseIcon = painterResource(Res.drawable.ic_previous),
                decreaseLabel = textResource(Res.string.song_details_chord_shape_previous, chordName),
                canDecrease = true,
                onDecrease = { coroutineScope.launch { pagerState.animateScrollToPage((pagerState.targetPage - 1).mod(pageCount)) } },
                increaseIcon = painterResource(Res.drawable.ic_next),
                increaseLabel = textResource(Res.string.song_details_chord_shape_next, chordName),
                canIncrease = true,
                onIncrease = { coroutineScope.launch { pagerState.animateScrollToPage((pagerState.targetPage + 1).mod(pageCount)) } },
                resetLabel = null,
                onReset = null,
            )
        }
        else -> Spacer(modifier = Modifier.padding(top = 12.dp).height(STEPPER_PLACEHOLDER_HEIGHT))
    }
}

/** The empty frame of a diagram and the room of the notes and the stepper, while there is nothing to page through. */
@Composable
internal fun ChordShapePlaceholder(
    instrument: ChordInstrument,
    chordName: String,
) {
    ChordDiagram(
        modifier = Modifier.diagramSize(instrument),
        geometry = remember(instrument) { emptyChordDiagramGeometryOf(instrument) },
        description = chordName,
        showsFingers = true,
    )
    ChordNotes(text = "")
    Spacer(modifier = Modifier.padding(top = 12.dp).height(STEPPER_PLACEHOLDER_HEIGHT))
}

private fun Modifier.diagramSize(instrument: ChordInstrument) = size(
    width = if (instrument.isFretted) FRETTED_WIDTH else KEYBOARD_WIDTH,
    height = if (instrument.isFretted) FRETTED_HEIGHT else KEYBOARD_HEIGHT,
)

/** Whether two shapes are fingered on the same frets or keys, whatever either says about the fingers. */
private fun ChordVoicing.sameShapeAs(other: ChordVoicing) = when (this) {
    is ChordVoicing.Fretted -> other is ChordVoicing.Fretted && frets == other.frets
    is ChordVoicing.Keys -> this == other
}

private val FRETTED_WIDTH = 132.dp
private val FRETTED_HEIGHT = 164.dp
private val KEYBOARD_WIDTH = 288.dp
private val KEYBOARD_HEIGHT = 124.dp
private val STEPPER_PLACEHOLDER_HEIGHT = 40.dp
