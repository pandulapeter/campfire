/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.pluralTextResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_dock_no_chords
import com.pandulapeter.campfire.presentation.resources.song_details_dock_too_many_chords
import com.pandulapeter.campfire.presentation.resources.song_details_key_change
import com.pandulapeter.campfire.presentation.ui.chords.ChordCell
import com.pandulapeter.campfire.presentation.ui.chords.ChordDiagram
import com.pandulapeter.campfire.presentation.ui.chords.MAX_SONG_CHORDS
import com.pandulapeter.campfire.presentation.ui.chords.chordCellDescription
import com.pandulapeter.campfire.presentation.ui.chords.chordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.chords.emptyChordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor

/**
 * The song's chords as diagrams in the dock's row of their own, from the Chords section's data: every chord once, in
 * the order it is first played, each key's chords after a label naming it ("Key: A♭") where the song's chords are in
 * more than one, each under its name and, where degrees or letters gloss the names, a smaller line over them. The
 * diagrams take what [rowHeight] leaves the lines of text, at most the Chords section's size ([isSmall] sets the
 * smaller names), the keyboard's wider and lower; a chord drawn with another shape crossfades to it. A diagram opens the
 * chord's sheet ([onChordClicked], null where shapes are not picked here). A song without chords says so, and a
 * songbook ([hasTooManyChords]) that it has too many, so that the row, which the screen's switches give the dock rather
 * than the song, is not left looking broken.
 *
 * The chords played on the screen the reader is headed for ([markedChords], `rememberChordsOnScreen`) are tinted, the
 * rest drawn as they are, so that the page's chords are found at a glance without the others looking unplayable.
 *
 * The strip ([scrollState], its row's) scrolls itself to [headedChord], the first chord of the page the reader is
 * headed for, whenever that changes, and only then: a strip swiped by hand stays where it was left until the next turn.
 * In a grid ([isGrid], the side panel's, which wraps the cells) [scrollState] is the panel's, scrolled on a page turn
 * only as far as brings the headed chord into view.
 */
@Composable
internal fun DockDiagramStrip(
    cells: List<ChordCell>,
    isSmall: Boolean,
    rowHeight: Dp,
    hasTooManyChords: Boolean,
    scrollState: ScrollState,
    headedChord: String?,
    markedChords: Set<String>,
    onChordClicked: ((chordName: String) -> Unit)?,
    isGrid: Boolean = false,
) {
    if (cells.isEmpty()) {
        Text(
            modifier = Modifier.padding(vertical = 8.dp),
            text = if (hasTooManyChords) {
                pluralTextResource(Res.plurals.song_details_dock_too_many_chords, MAX_SONG_CHORDS, MAX_SONG_CHORDS.toString())
            } else {
                stringResource(Res.string.song_details_dock_no_chords)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val typography = MaterialTheme.typography
    val nameStyle = if (isSmall) typography.labelMedium else typography.labelLarge
    val glossStyle = typography.labelSmall
    val hasGlosses = cells.any { (it.letterName ?: it.degree) != null }
    val density = LocalDensity.current
    val glossHeight = if (hasGlosses) with(density) { glossStyle.lineHeight.toDp() } + CELL_LINE_GAP else 0.dp
    val diagramHeight = rowHeight - with(density) { nameStyle.lineHeight.toDp() } - CELL_LINE_GAP - glossHeight
    val positions = remember(cells) { DockRowPositions() }
    // A key's first chord is shown with the label naming its key.
    val shown = headedChord?.let { chord ->
        cells.firstOrNull { it.name == chord }?.keyName?.takeIf { key -> cells.first { it.keyName == key }.name == chord }?.let(::keyLabelId) ?: chord
    }
    val requester = remember { BringIntoViewRequester() }
    fun Modifier.headed(id: String) = if (isGrid && id == shown) bringIntoViewRequester(requester) else this
    cells.forEachIndexed { index, cell ->
        val keyName = cell.keyName
        if (keyName != null && (index == 0 || cells[index - 1].keyName != keyName)) {
            Text(
                modifier = Modifier.widthIn(max = KEY_LABEL_WIDTH).onPlaced { positions.record(keyLabelId(keyName), it.positionInParent().x, it.size.width) }.headed(keyLabelId(keyName)),
                text = textResource(Res.string.song_details_key_change, keyName),
                style = typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        key(cell.name) {
            DiagramCell(
                modifier = Modifier.onPlaced { positions.record(cell.name, it.positionInParent().x, it.size.width) }.headed(cell.name),
                cell = cell,
                isMarked = cell.name in markedChords,
                nameStyle = nameStyle,
                glossHeight = glossHeight,
                diagramSize = dockDiagramSize(isFretted = cell.tuning.instrument.isFretted, maxHeight = diagramHeight),
                onClick = onChordClicked?.let { { it(cell.name) } },
            )
        }
    }
    if (isGrid) {
        BringIntoViewOnPageTurn(id = shown, scrollState = scrollState, requester = requester)
    } else {
        ScrollToOnPageTurn(id = shown, scrollState = scrollState, positions = positions)
    }
}

/** One chord of [DockDiagramStrip], tinted where it [isMarked] as played on the screen: its gloss (or the room for one), its name, and its diagram, crossfading as its shape changes. */
@Composable
private fun DiagramCell(
    modifier: Modifier,
    cell: ChordCell,
    isMarked: Boolean,
    nameStyle: TextStyle,
    glossHeight: Dp,
    diagramSize: DpSize,
    onClick: (() -> Unit)?,
) = Column(
    modifier = modifier
        .clip(MaterialTheme.shapes.small)
        // A tint rather than a dimming of the rest, which would read as chords that cannot be played.
        .background(animateColorAsState(if (isMarked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent).value)
        .then(
            if (onClick == null) {
                Modifier
            } else {
                Modifier.clickable(role = Role.Button) {
                    println("Dock: chord diagram")
                    onClick()
                }
            },
        )
        .padding(horizontal = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(CELL_LINE_GAP),
) {
    val gloss = cell.letterName ?: cell.degree
    if (glossHeight > 0.dp) {
        Box(modifier = Modifier.height(glossHeight - CELL_LINE_GAP), contentAlignment = Alignment.Center) {
            if (gloss != null) {
                Text(
                    text = gloss,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
    Text(
        text = cell.name,
        style = nameStyle,
        color = LocalSecondAccentColor.current,
        maxLines = 1,
    )
    val geometry = remember(cell) {
        cell.selection.shape?.let { chordDiagramGeometryOf(it, cell.tuning, cell.root, cell.capo) } ?: emptyChordDiagramGeometryOf(cell.tuning.instrument)
    }
    val description = chordCellDescription(cell)
    Crossfade(modifier = Modifier.size(diagramSize), targetState = geometry) { shownGeometry ->
        ChordDiagram(
            modifier = Modifier.fillMaxSize(),
            geometry = shownGeometry,
            description = description,
        )
    }
}

/**
 * How large a dock's diagram is drawn: at the Chords section's size where [maxHeight] allows it, else as tall as
 * [maxHeight] and as much narrower, the keyboard's wider than tall either way.
 */
internal fun dockDiagramSize(isFretted: Boolean, maxHeight: Dp): DpSize {
    val full = if (isFretted) DpSize(FRETTED_WIDTH, FRETTED_HEIGHT) else DpSize(KEYBOARD_WIDTH, KEYBOARD_HEIGHT)
    val height = maxHeight.coerceIn(MIN_DIAGRAM_HEIGHT, full.height)
    return DpSize(full.width * (height / full.height), height)
}

private fun keyLabelId(keyName: String) = "key:$keyName"

private val CELL_LINE_GAP = 2.dp
private val KEY_LABEL_WIDTH = 56.dp
private val MIN_DIAGRAM_HEIGHT = 24.dp
private val FRETTED_WIDTH = 56.dp
private val FRETTED_HEIGHT = 70.dp
private val KEYBOARD_WIDTH = 76.dp
private val KEYBOARD_HEIGHT = 40.dp
