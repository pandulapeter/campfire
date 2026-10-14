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

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.chordpro.model.ChordVoicing
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_chord_defined
import com.pandulapeter.campfire.presentation.resources.song_details_chord_defined_moved
import com.pandulapeter.campfire.presentation.ui.chords.ChordCell
import com.pandulapeter.campfire.presentation.ui.chords.SelectedShape
import com.pandulapeter.campfire.presentation.ui.chords.SongChord
import com.pandulapeter.campfire.presentation.ui.chords.chordCellDescription
import com.pandulapeter.campfire.presentation.ui.chords.chordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.chords.emptyChordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.chords.ChordDiagram
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.platform.bounceScrollableContent
import kotlin.math.abs

/**
 * The scrolling content of a chord's sheet, whose subtitle names the chord's notes ([chordNoteNames]): [tuningLine] where
 * the tuning is not the standard one, the chord's letters where the page counts it, and under them every shape of it as a
 * grid of diagrams, the one the songs draw it with marked. A tap on another marks that one and hands it to [onShapeSelected] at once; the marked shape is never
 * reset, since which shape is the app's first says nothing to a player who only picks the one their hands play. A chord
 * the song defines has its definition alone, and says so under it. [footer] closes the grid. [shapes] are the chord's,
 * searched for by the sheet before it is put up ([rememberChordShapes]); while a chord that took another's place under
 * the open sheet has none yet, one empty frame stands where the first shape will be.
 */
@Composable
internal fun ChordShapeGrid(
    modifier: Modifier = Modifier,
    chord: SongChord,
    shapes: List<ChordVoicing>?,
    tuning: ChordTuning,
    notation: ChordNotation,
    selection: SelectedShape?,
    tuningLine: String?,
    contentPadding: PaddingValues,
    onShapeSelected: (ChordVoicing?) -> Unit,
    footer: (@Composable () -> Unit)? = null,
) {
    val isDefined = selection?.source == SelectedShape.Source.DEFINED
    // The options are decided once the shapes are there and kept while the sheet is up, so that picking another shape
    // than a stored one the app does not list never takes the stored one out from under the finger.
    val options = remember(shapes) { shapes?.let { all -> selection?.let { shapeGridOptions(it, all) } } }
    // Marked at once on a tap, rather than once the choice has come back through the preferences; a choice arriving
    // from elsewhere (a sync run) marks its own shape.
    var selectedIndex by remember(options, selection?.shape) {
        mutableIntStateOf(options?.indexOfFirst { selection?.shape != null && it.sameShapeAs(selection.shape) }?.coerceAtLeast(0) ?: -1)
    }
    // The app's own first shape is never stored as the player's: picking it takes the chord's entry out, so that the app's
    // default is what plays there, rather than a copy of it that nothing would ever update.
    val defaultShape = shapes?.firstOrNull()
    val gridState = rememberLazyGridState()
    LazyVerticalGrid(
        modifier = modifier
            .fillMaxWidth()
            .fadingTopEdge(
                scrolled = { if (gridState.firstVisibleItemIndex > 0) Int.MAX_VALUE else gridState.firstVisibleItemScrollOffset },
                backgroundColor = sheetContainerColor(),
            )
            .bounceScrollableContent(gridState),
        state = gridState,
        columns = GridCells.Adaptive(minSize = tuning.instrument.optionMinWidth),
        contentPadding = contentPadding.only(
            start = true,
            top = true,
            end = true,
            bottom = true,
            extraStart = 16.dp,
            extraTop = 8.dp,
            extraEnd = 16.dp,
            extraBottom = 8.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(OPTION_GAP),
        verticalArrangement = Arrangement.spacedBy(OPTION_GAP),
    ) {
        chordHeader(
            tuningLine = tuningLine,
            letterName = chord.letterName,
        )
        if (options == null) {
            item(key = PLACEHOLDER_KEY, contentType = OPTION_CONTENT_TYPE) {
                ChordShapePlaceholder(instrument = tuning.instrument, chordName = chord.name)
            }
        } else {
            itemsIndexed(items = options, key = { index, _ -> index }, contentType = { _, _ -> OPTION_CONTENT_TYPE }) { index, shape ->
                ChordShapeOption(
                    modifier = listItemAnimation(gridState),
                    chord = chord,
                    tuning = tuning,
                    shape = shape,
                    isSelected = index == selectedIndex,
                    onClick = if (isDefined) {
                        null
                    } else {
                        {
                            if (index != selectedIndex) {
                                selectedIndex = index
                                onShapeSelected(shape.takeUnless { defaultShape?.sameShapeAs(it) == true })
                            }
                        }
                    },
                )
            }
            if (isDefined && selection != null) {
                item(key = DEFINED_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = DEFINED_KEY) {
                    DefinedNote(modifier = listItemAnimation(gridState), movedBy = selection.movedBy)
                }
            }
        }
        if (footer != null) {
            item(key = FOOTER_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = FOOTER_KEY) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    footer()
                }
            }
        }
    }
}

/** The lines over the shapes, each spanning the grid, each there only where it says something. */
private fun LazyGridScope.chordHeader(
    tuningLine: String?,
    letterName: String?,
) {
    tuningLine?.let {
        item(key = TUNING_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = TUNING_KEY) {
            HeaderLine(text = it, style = MaterialTheme.typography.titleSmall)
        }
    }
    letterName?.let {
        item(key = LETTERS_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = LETTERS_KEY) {
            HeaderLine(text = it, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun HeaderLine(
    text: String,
    style: TextStyle,
) = Text(
    modifier = Modifier.fillMaxWidth(),
    text = text,
    style = style,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
)

/**
 * One shape of the grid: its diagram with the fingers that hold it, on a card that picks it, and marked while it is the
 * one the songs draw the chord with. A song's definition has no other shape to pick, so its card is not clickable.
 */
@Composable
private fun ChordShapeOption(
    modifier: Modifier = Modifier,
    chord: SongChord,
    tuning: ChordTuning,
    shape: ChordVoicing,
    isSelected: Boolean,
    onClick: (() -> Unit)?,
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "chordShapeOptionContainer",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "chordShapeOptionBorder",
    )
    val content = @Composable {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            ChordShapeDiagram(chord = chord, tuning = tuning, shape = shape)
        }
    }
    if (onClick == null) {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.medium,
            color = containerColor,
            border = BorderStroke(OPTION_BORDER_WIDTH, borderColor),
            content = content,
        )
    } else {
        Surface(
            modifier = modifier.semantics { role = Role.RadioButton },
            selected = isSelected,
            onClick = onClick,
            shape = MaterialTheme.shapes.medium,
            color = containerColor,
            border = BorderStroke(OPTION_BORDER_WIDTH, borderColor),
            content = content,
        )
    }
}

/** [shape] of [chord], with the fingers that hold it. */
@Composable
private fun ChordShapeDiagram(
    chord: SongChord,
    tuning: ChordTuning,
    shape: ChordVoicing,
) = ChordDiagram(
    modifier = Modifier.diagramSize(tuning.instrument),
    geometry = remember(shape, tuning, chord.chord.root) { chordDiagramGeometryOf(shape, tuning, chord.chord.root) },
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

/** That the song defines the chord, and by how many frets the song's transposition moved the definition. */
@Composable
private fun DefinedNote(
    modifier: Modifier = Modifier,
    movedBy: Int,
) = Text(
    modifier = modifier.fillMaxWidth().padding(top = 4.dp),
    text = if (movedBy == 0) {
        stringResource(Res.string.song_details_chord_defined)
    } else {
        pluralStringResource(Res.plurals.song_details_chord_defined_moved, abs(movedBy), abs(movedBy))
    },
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.primary,
    textAlign = TextAlign.Center,
)

/** The empty frame of a diagram on the card of a shape, where the first shape will be once there is one to draw. */
@Composable
private fun ChordShapePlaceholder(
    instrument: ChordInstrument,
    chordName: String,
) = Surface(
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        ChordDiagram(
            modifier = Modifier.diagramSize(instrument),
            geometry = remember(instrument) { emptyChordDiagramGeometryOf(instrument) },
            description = chordName,
            showsFingers = true,
        )
    }
}

private fun Modifier.diagramSize(instrument: ChordInstrument) = size(
    width = if (instrument.isFretted) FRETTED_WIDTH else KEYBOARD_WIDTH,
    height = if (instrument.isFretted) FRETTED_HEIGHT else KEYBOARD_HEIGHT,
)

/** How narrow a column of the grid gets: a card as wide as its diagram and the room around it. */
private val ChordInstrument.optionMinWidth get() = (if (isFretted) FRETTED_WIDTH else KEYBOARD_WIDTH) + 16.dp

/**
 * The shapes of the grid for [selection], [all] being every shape the app has for the chord in the sheet's tuning: a
 * song's definition alone, and otherwise [all], after a stored shape of the player's that the app does not list, which is
 * still theirs. A default the list lacks was given for another tuning (cells held while the next tuning's are searched
 * for), and is dropped, since picking it would store a shape that sounds another chord on these strings.
 */
internal fun shapeGridOptions(selection: SelectedShape, all: List<ChordVoicing>): List<ChordVoicing> {
    val current = selection.shape
    return when {
        selection.source == SelectedShape.Source.DEFINED -> listOfNotNull(current)
        selection.source == SelectedShape.Source.PLAYER && current != null && all.none { it.sameShapeAs(current) } -> listOf(current) + all
        else -> all
    }
}

/** The notes [chord] is made of, spelled the way the song spells the chord where it does: the subtitle of its sheet. */
internal fun chordNoteNames(chord: SongChord, notation: ChordNotation) =
    (chord.spelling?.let { ChordProChords.spelledNoteNames(it, notation) } ?: ChordProChords.noteNames(chord.chord, notation)).joinToString(" ")

/** Whether two shapes are fingered on the same frets or keys, whatever either says about the fingers. */
private fun ChordVoicing.sameShapeAs(other: ChordVoicing) = when (this) {
    is ChordVoicing.Fretted -> other is ChordVoicing.Fretted && frets == other.frets
    is ChordVoicing.Keys -> this == other
}

private const val OPTION_CONTENT_TYPE = "shape"
private const val PLACEHOLDER_KEY = "placeholder"
private const val DEFINED_KEY = "defined"
private const val FOOTER_KEY = "footer"
private const val TUNING_KEY = "tuning"
private const val LETTERS_KEY = "letters"
private val OPTION_GAP = 8.dp
private val OPTION_BORDER_WIDTH = 2.dp
private val FRETTED_WIDTH = 112.dp
private val FRETTED_HEIGHT = 140.dp
private val KEYBOARD_WIDTH = 224.dp
private val KEYBOARD_HEIGHT = 96.dp
