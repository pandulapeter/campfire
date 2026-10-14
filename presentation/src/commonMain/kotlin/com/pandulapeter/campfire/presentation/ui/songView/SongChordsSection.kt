/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import com.pandulapeter.campfire.presentation.ui.songView.model.ChordDiagrams
import com.pandulapeter.campfire.presentation.ui.songView.model.FoldToggle
import com.pandulapeter.campfire.presentation.ui.songView.model.RenderSection
import com.pandulapeter.campfire.presentation.ui.songView.model.withChordsSection
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_chord_shape_choose
import com.pandulapeter.campfire.presentation.resources.song_details_chords
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.ui.chords.ChordDiagramGeometry
import com.pandulapeter.campfire.presentation.ui.chords.chordCellDescription
import com.pandulapeter.campfire.presentation.ui.chords.drawChordDiagram
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.ui.songLayout.chordSlotRows
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * One chunk of the song's chords as diagrams: the slots [items] names (counted as [RenderSection.Chords.itemCount]),
 * the first headed by a pill that folds them like any section's. The cells are drawn in rows that wrap rather than
 * scroll sideways, since a song may be read with a pedal, one slot per row the section may be cut between, as a run of
 * tablature is drawn as systems. A tap on a cell opens that chord's sheet (see [ChordDiagrams.onChordClicked]); a tap
 * between them, or on any of them in read only mode, turns the page like a tap anywhere on the song.
 *
 * The instrument and the tuning the diagrams are drawn for are chosen from the chips heading the song's first section
 * ([SongInstrumentChips], see [withChordsSection]), which an instrument without shapes, and so without this section,
 * keeps.
 *
 * @param onFoldToggled Folds the section or unfolds it, see [ChordDiagrams.onFoldToggled].
 * @param isFadingIn Whether the fold was toggled while the page is open, so that the rows coming back fade in, in
 * every chunk they come back in.
 */
@Composable
internal fun SongChordsSection(
    modifier: Modifier = Modifier,
    section: RenderSection.Chords,
    items: IntRange,
    layouts: ChordCellLayouts,
    chordDiagrams: ChordDiagrams?,
    onFoldToggled: (() -> Unit)?,
    isFadingIn: Boolean,
    headerStyle: TextStyle,
    fontScale: Float,
) = Column(modifier = modifier) {
    val isFirstChunk = items.first == 0
    if (isFirstChunk) {
        SectionHeaderPill(
            header = stringResource(Res.string.song_details_chords),
            toggle = onFoldToggled?.let {
                FoldToggle(
                    isExpanded = !section.isFolded,
                    onToggled = it,
                )
            },
            style = headerStyle,
            chevronSize = FOLD_CHEVRON_SIZE * fontScale,
        )
    }
    if (section.isFolded) return@Column
    ChordRowsBlock(
        modifier = Modifier
            .fillMaxWidth()
            .fadingIn(isFadingIn = isFadingIn)
            .padding(top = if (isFirstChunk) HEADER_GAP else 0.dp),
        layouts = layouts,
        firstSlot = items.first,
        lastSlot = items.last,
        isLastSlot = items.last == section.itemCount - 1,
        onCellClicked = chordDiagrams?.onChordClicked?.let { onChordClicked -> { cell -> onChordClicked(layouts.cells[cell].name) } },
    )
}

/**
 * The rows of diagrams the slots from [firstSlot] to [lastSlot] hold at the width they are measured at, every row
 * after them too where [isLastSlot]. Drawn rather than composed, like a run of tablature, because which cells a slot
 * holds depends on that width and the section is measured at several before one wins (see [SongSectionsLayout]); its
 * intrinsic sizes are its own measure policy's, so that a height asked for at a candidate width already counts the rows
 * the cells wrap into there. Each cell is drawn as it was composed: its names centred over its diagram, or its diagram
 * under a wider pair of names, the second name after the first and level with its bottom, top-aligned in its row and
 * mirrored in a right-to-left layout (the diagram itself never is: a guitar's low string stays on the left). A cell wider
 * than the block is held to it, its names clipped at its edge, as a composed cell was held to its row.
 *
 * A cell whose chord is drawn with another shape than it was crossfades from the old diagram to the new one (see
 * [rememberChordShapeCrossfade]).
 *
 * Where [onCellClicked] is given, a press on a cell lights the whole of its row's height behind it, as a button's
 * press would, and a tap that the scroll did not take is answered with the cell's index; a screen reader is offered the
 * same as one action per cell it is told about.
 */
@Composable
private fun ChordRowsBlock(
    modifier: Modifier = Modifier,
    layouts: ChordCellLayouts,
    firstSlot: Int,
    lastSlot: Int,
    isLastSlot: Boolean,
    onCellClicked: ((cell: Int) -> Unit)?,
) {
    val nameColor = LocalSecondAccentColor.current
    val secondNameColor = MaterialTheme.colorScheme.onSurfaceVariant
    val lineColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val backgroundColor = MaterialTheme.colorScheme.surface
    // The page's measurer keeps nothing, since whoever asks keeps what it measured; a diagram measures its base fret's
    // number on every draw, so it gets one with a cache of its own, as the composed diagram had.
    val diagramTextMeasurer = rememberTextMeasurer()
    // The width the block was last placed at, which is all its cells depend on that composition does not know: kept
    // rather than the cells, so that a new layout of the cells is described from it at once.
    var shownWidth by remember { mutableIntStateOf(-1) }
    val shownCells = if (shownWidth < 0) IntRange.EMPTY else layouts.cellsAt(shownWidth, firstSlot, lastSlot, isLastSlot)
    val descriptions = mutableListOf<String>()
    for (cell in shownCells) descriptions += chordCellDescription(layouts.cells[cell])
    val description = descriptions.joinToString("\n")
    val actions = if (onCellClicked == null) emptyList() else shownCells.map { cell ->
        CustomAccessibilityAction(textResource(Res.string.song_details_chord_shape_choose, layouts.cells[cell].name)) {
            onCellClicked(cell)
            true
        }
    }
    val isRightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currentOnCellClicked by rememberUpdatedState(onCellClicked)
    // The last cell pressed, kept while its highlight fades out after the finger has gone.
    var pressedCell by remember { mutableIntStateOf(-1) }
    var isPressed by remember { mutableStateOf(false) }
    // The highlight a tap quicker than the delay is shown with, kept outside pointerInput, whose block a change of its
    // keys restarts.
    val pulseScope = rememberCoroutineScope()
    var pressPulse by remember { mutableStateOf<Job?>(null) }
    val pressProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
    )
    val pressColor = MaterialTheme.colorScheme.onSurface.copy(alpha = PRESSED_ALPHA)
    val shapeCrossfade = rememberChordShapeCrossfade(layouts)
    // One for every layer of every fading cell, its alpha set before each, rather than one allocated per layer per frame.
    val layerPaint = remember { Paint() }
    val measurePolicy = remember(layouts, firstSlot, lastSlot, isLastSlot) { ChordRowsMeasurePolicy(layouts, firstSlot, lastSlot, isLastSlot) }
    Layout(
        modifier = modifier
            .onSizeChanged { shownWidth = it.width }
            .then(
                if (description.isEmpty()) {
                    Modifier
                } else {
                    Modifier.semantics {
                        contentDescription = description
                        if (actions.isNotEmpty()) customActions = actions
                    }
                },
            )
            .then(
                if (onCellClicked == null) {
                    Modifier
                } else {
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .pointerInput(layouts, firstSlot, lastSlot, isLastSlot, isRightToLeft) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                // A pulse cancelled during its delay never puts its highlight out, which would light this
                                // press at once or keep a scroll it turns into lit.
                                pressPulse?.cancel()
                                pressPulse = null
                                isPressed = false
                                // A right or middle click is a context menu or a paste, as it is to the step taps around the song.
                                if (down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                                val cell = layouts.cellAt(down.position.x, down.position.y, size.width, firstSlot, lastSlot, isLastSlot, isRightToLeft)
                                if (cell < 0) return@awaitEachGesture
                                pressedCell = cell
                                // Null where the scroll took the press as a drag, which then scrolls the song as anywhere else.
                                // A touch is only lit once it has lasted the delay, so a scroll started on a diagram never
                                // flashes it; a mouse never starts one by dragging the content, so it lights at once.
                                var isFinished = false
                                var up = if (down.type == PointerType.Mouse) {
                                    null
                                } else {
                                    withTimeoutOrNull(PRESS_HIGHLIGHT_DELAY_MILLIS) { waitForUpOrCancellation().also { isFinished = true } }
                                }
                                if (!isFinished) {
                                    isPressed = true
                                    up = waitForUpOrCancellation()
                                    isPressed = false
                                }
                                if (up != null) {
                                    // Consumed, so that the tap does not also turn the page around it.
                                    up.consume()
                                    // A gesture handler cannot wait once the finger is up, and a highlight set and cleared
                                    // in one frame draws nothing.
                                    if (isFinished) {
                                        pressPulse = pulseScope.launch {
                                            isPressed = true
                                            delay(PRESS_HIGHLIGHT_DELAY_MILLIS)
                                            isPressed = false
                                        }
                                    }
                                    currentOnCellClicked?.invoke(cell)
                                }
                            }
                        }
                },
            )
            .drawBehind {
                val width = size.width.roundToInt()
                val starts = layouts.rowStartsAt(width)
                var y = 0f
                for (row in chordSlotRows(starts.size, firstSlot, lastSlot, isLastSlot)) {
                    var x = 0
                    val rowHeight = layouts.rowHeight(starts, row)
                    for (cell in layouts.cellsOfRow(starts, row)) {
                        val cellWidth = minOf(layouts.cellWidths[cell], width)
                        val cellLeft = if (isRightToLeft) width - x - cellWidth else x
                        if (cell == pressedCell && pressProgress > 0f) {
                            val inset = layouts.gap / 2f
                            drawRoundRect(
                                color = pressColor,
                                alpha = pressProgress,
                                topLeft = Offset(cellLeft - inset, y - inset),
                                size = Size(cellWidth + inset * 2, rowHeight + inset * 2),
                                cornerRadius = CornerRadius(PRESSED_CORNER_RADIUS.toPx()),
                            )
                        }
                        drawChordCell(
                            layouts = layouts,
                            cell = cell,
                            left = cellLeft.toFloat(),
                            top = y,
                            width = cellWidth,
                            isRightToLeft = isRightToLeft,
                            nameColor = nameColor,
                            secondNameColor = secondNameColor,
                            lineColor = lineColor,
                            mutedColor = mutedColor,
                            backgroundColor = backgroundColor,
                            diagramTextMeasurer = diagramTextMeasurer,
                            fadingOut = shapeCrossfade.fadingOut[cell],
                            fadeProgress = shapeCrossfade.progress.value,
                            layerPaint = layerPaint,
                        )
                        x += cellWidth + layouts.gap
                    }
                    y += rowHeight + layouts.gap
                }
            },
        measurePolicy = measurePolicy,
    )
}

/**
 * Draws [cell] of [layouts] [width] wide with its top left corner at [left] and [top], see [ChordRowsBlock]: its
 * diagram [fadeProgress] of the way from [fadingOut] to its own where it is fading out of another shape.
 */
private fun DrawScope.drawChordCell(
    layouts: ChordCellLayouts,
    cell: Int,
    left: Float,
    top: Float,
    width: Int,
    isRightToLeft: Boolean,
    nameColor: Color,
    secondNameColor: Color,
    lineColor: Color,
    mutedColor: Color,
    backgroundColor: Color,
    diagramTextMeasurer: TextMeasurer,
    fadingOut: ChordDiagramGeometry?,
    fadeProgress: Float,
    layerPaint: Paint,
) {
    val name = layouts.names[cell]
    val secondName = layouts.secondNames[cell]
    val nameRowHeight = layouts.nameRowHeights[cell]
    val nameRowWidth = layouts.nameRowWidths[cell]
    clipRect(left = left, top = top, right = left + width, bottom = top + nameRowHeight) {
        val rowLeft = left + ((width - nameRowWidth).coerceAtLeast(0) / 2f).roundToInt()
        val nameLeft = if (isRightToLeft) rowLeft + nameRowWidth - name.size.width else rowLeft
        drawText(textLayoutResult = name, color = nameColor, topLeft = Offset(nameLeft, top + nameRowHeight - name.size.height))
        secondName?.let {
            val secondLeft = if (isRightToLeft) rowLeft else rowLeft + name.size.width + layouts.nameGap
            drawText(textLayoutResult = it, color = secondNameColor, topLeft = Offset(secondLeft, top + nameRowHeight - it.size.height))
        }
    }
    val diagramSize = layouts.diagramSizes[cell]
    translate(left = left + ((width - diagramSize.width) / 2f).roundToInt(), top = top + nameRowHeight) {
        val area = Size(diagramSize.width.toFloat(), diagramSize.height.toFloat())
        val drawDiagram = { geometry: ChordDiagramGeometry ->
            drawChordDiagram(
                geometry = geometry,
                area = area,
                lineColor = lineColor,
                mutedColor = mutedColor,
                rootColor = nameColor,
                backgroundColor = backgroundColor,
                textMeasurer = diagramTextMeasurer,
            )
        }
        if (fadingOut == null || fadeProgress >= 1f) {
            drawDiagram(layouts.geometries[cell])
        } else {
            withLayerAlpha(alpha = 1f - fadeProgress, area = area, paint = layerPaint) { drawDiagram(fadingOut) }
            withLayerAlpha(alpha = fadeProgress, area = area, paint = layerPaint) { drawDiagram(layouts.geometries[cell]) }
        }
    }
}

/**
 * Draws [block] at [alpha] as one picture rather than stroke by stroke, since a diagram paints the background over its
 * own lines (under a dot, inside a ring), and those would show through one another at any alpha less than whole. The
 * layer reaches [LAYER_MARGIN] of the diagram's shorter side past [area] all around, since a stroke on its edge is drawn
 * half outside it, and no stroke of a diagram is that wide; no further, since every fading cell draws two layers a frame.
 * [paint] is read as the layer is saved, so one serves every layer.
 */
private inline fun DrawScope.withLayerAlpha(alpha: Float, area: Size, paint: Paint, block: () -> Unit) {
    val canvas = drawContext.canvas
    val margin = minOf(area.width, area.height) * LAYER_MARGIN
    paint.alpha = alpha
    canvas.saveLayer(Rect(-margin, -margin, area.width + margin, area.height + margin), paint)
    block()
    canvas.restore()
}

/**
 * What [ChordRowsBlock] measures as. All four intrinsic sizes are answered here rather than left to the measure block,
 * which would answer the whole line as the minimum width too and make the section look like a staff of tablature that
 * needs a row of its own. Asked how wide it would be, every slot answers for the whole section, as every piece of a run
 * of tablature does: the layout narrows a column to the widest of what it holds, and a column narrowed to one row's
 * share would break the other slots into other rows than the page was planned with.
 */
private class ChordRowsMeasurePolicy(
    private val layouts: ChordCellLayouts,
    private val firstSlot: Int,
    private val lastSlot: Int,
    private val isLastSlot: Boolean,
) : MeasurePolicy {

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        // A layout refuses an unbounded size, so the block is as wide as its cells on one line there, as a tab is.
        val width = if (constraints.maxWidth == Constraints.Infinity) layouts.lineWidth.coerceAtLeast(constraints.minWidth) else constraints.maxWidth
        return layout(width = width, height = layouts.height(width, firstSlot, lastSlot, isLastSlot).coerceIn(constraints.minHeight, constraints.maxHeight)) {}
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = layouts.widestCell

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = layouts.lineWidth

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) =
        layouts.height(width, firstSlot, lastSlot, isLastSlot)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) =
        layouts.height(width, firstSlot, lastSlot, isLastSlot)
}

/**
 * How long a touch on a chord cell lasts before the cell is lit, and how long a quicker tap lights it for: the tap
 * timeout Material waits inside a scrolling container before showing a press (Android's `getTapTimeout()`, which
 * Compose's common `ViewConfiguration` does not expose), so that a drag the song's scroll takes never lights it.
 */
private const val PRESS_HIGHLIGHT_DELAY_MILLIS = 100L

private const val PRESSED_ALPHA = 0.1f
private const val LAYER_MARGIN = 0.1f
private val PRESSED_CORNER_RADIUS = 8.dp
