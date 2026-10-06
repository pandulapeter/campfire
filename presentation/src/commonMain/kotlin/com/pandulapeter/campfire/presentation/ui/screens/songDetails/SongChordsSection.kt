/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.ChordProChords
import com.pandulapeter.campfire.chordpro.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.Chord
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.model.ChordVoicing
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_edit
import com.pandulapeter.campfire.presentation.resources.song_details_chord_shapes
import com.pandulapeter.campfire.presentation.resources.song_details_chord_diagram
import com.pandulapeter.campfire.presentation.resources.song_details_chord_diagram_none
import com.pandulapeter.campfire.presentation.resources.song_details_chord_letters
import com.pandulapeter.campfire.presentation.resources.song_details_chord_sounding
import com.pandulapeter.campfire.presentation.resources.song_details_chords
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.ui.chords.SelectedShape
import com.pandulapeter.campfire.presentation.ui.chords.SongChord
import com.pandulapeter.campfire.presentation.ui.chords.chordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.chords.emptyChordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.chords.selectShape
import com.pandulapeter.campfire.presentation.ui.components.drawChordDiagram
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

/**
 * What a song's Chords section needs beyond the song: the instrument the diagrams are drawn for, the shapes the player
 * chose, whether the section is folded, and what its header does.
 *
 * @param onFoldToggled Folds the section or unfolds it, one preference for every song; null where nothing folds.
 * @param onShapesClicked Opens the Chord shapes sheet of the song, null in read only mode (performance mode, or a song
 * read from an archived setlist), which takes the controls off the page. Offered only while the section is unfolded.
 * @param showsDefinitionsOnly Whether the section holds the song's own definitions and nothing else, each on the
 * instrument it is written for, which is the editor's preview: it shows what is being written, and the app's own shapes
 * are not written. [notation] is what their names are read in.
 */
@Immutable
internal data class ChordDiagrams(
    val instrument: ChordInstrument,
    val storedShapes: Map<String, String> = emptyMap(),
    val isFolded: Boolean = false,
    val onFoldToggled: (() -> Unit)? = null,
    val onShapesClicked: (() -> Unit)? = null,
    val showsDefinitionsOnly: Boolean = false,
    val notation: ChordNotation = ChordNotation.STANDARD,
)

/**
 * One diagram of the Chords section: the chord as the page names it and the shape it is drawn with, and the names
 * [SongChord.soundingName] and [SongChord.letterName] give it after that.
 */
@Immutable
internal data class ChordCell(
    val name: String,
    val soundingName: String?,
    val letterName: String? = null,
    val instrument: ChordInstrument,
    val root: Int,
    val selection: SelectedShape,
)

/** The cells of [chords], each with the shape [selectShape] picks for it. */
internal fun chordCellsOf(chords: List<SongChord>, instrument: ChordInstrument, storedShapes: Map<String, String>) = chords.map { chord ->
    ChordCell(
        name = chord.name,
        soundingName = chord.soundingName,
        letterName = chord.letterName,
        instrument = instrument,
        root = chord.chord.root,
        selection = selectShape(chord, instrument, storedShapes),
    )
}

/**
 * The cells of every definition [song] holds, in file order and each on its own instrument, see
 * [ChordDiagrams.showsDefinitionsOnly]. A numbering leaves a song's definitions in letters, which is how they are named.
 */
internal fun definitionCellsOf(song: ChordProSong, notation: ChordNotation) = song.metadata.definitions.map { definition ->
    ChordCell(
        name = definition.name,
        soundingName = null,
        instrument = definition.instrument,
        root = ChordProChords.parse(definition.name, notation)?.root ?: 0,
        selection = SelectedShape(definition.voicing, SelectedShape.Source.DEFINED, definition.movedBy),
    )
}

/**
 * [sections] with the Chords section after the metadata section, or first where there is none: the diagrams are part of
 * how the song is played, which the metadata section starts with. None where there is no chord to draw.
 */
internal fun withChordsSection(sections: List<RenderSection>, cells: List<ChordCell>, isFolded: Boolean): List<RenderSection> {
    if (cells.isEmpty()) return sections
    val index = if (sections.firstOrNull() is RenderSection.Metadata) 1 else 0
    return sections.take(index) + RenderSection.Chords(cells = cells, isFolded = isFolded) + sections.drop(index)
}

/**
 * The cells of a Chords section as its slots measure and draw them: every name laid out once, every cell's width and
 * height, and the rows they wrap into at the last few widths the layout asked about ([MAX_CHORD_ROW_WIDTHS], as
 * `TabRows` keeps them), shared by every slot of the section so that a width is broken into rows once. Everything is
 * measured as the composed cell was: the names in [chordStyle] on one line, the second one 4dp after the first, over a
 * diagram growing with [fontScale], the cells [CELL_GAP] apart.
 *
 * None of this is state: it is filled in by whoever asks first, and the answers never change.
 */
internal class ChordCellLayouts(
    val cells: List<ChordCell>,
    chordStyle: TextStyle,
    textMeasurer: TextMeasurer,
    density: Density,
    fontScale: Float,
) {

    val names by lazy(LazyThreadSafetyMode.NONE) {
        cells.map { textMeasurer.measure(AnnotatedString(it.name), chordStyle, maxLines = 1, softWrap = false) }
    }
    val secondNames by lazy(LazyThreadSafetyMode.NONE) {
        cells.map { cell ->
            (cell.soundingName ?: cell.letterName)?.let { textMeasurer.measure(AnnotatedString(it), chordStyle, maxLines = 1, softWrap = false) }
        }
    }
    val nameGap = with(density) { NAME_GAP.roundToPx() }
    val gap = with(density) { (CELL_GAP * fontScale).roundToPx() }
    val diagramSizes = with(density) {
        cells.map { cell ->
            IntSize(
                width = ((if (cell.instrument.isFretted) FRETTED_WIDTH else KEYBOARD_WIDTH) * fontScale).roundToPx(),
                height = ((if (cell.instrument.isFretted) FRETTED_HEIGHT else KEYBOARD_HEIGHT) * fontScale).roundToPx(),
            )
        }
    }

    /** How wide the names of each cell are together, the second one after its gap where there is one. */
    val nameRowWidths by lazy(LazyThreadSafetyMode.NONE) {
        IntArray(cells.size) { cell -> names[cell].size.width + (secondNames[cell]?.let { nameGap + it.size.width } ?: 0) }
    }

    /** How tall the names of each cell are, the taller of the two, which share a style. */
    val nameRowHeights by lazy(LazyThreadSafetyMode.NONE) {
        IntArray(cells.size) { cell -> maxOf(names[cell].size.height, secondNames[cell]?.size?.height ?: 0) }
    }
    val cellWidths by lazy(LazyThreadSafetyMode.NONE) { IntArray(cells.size) { cell -> maxOf(diagramSizes[cell].width, nameRowWidths[cell]) } }
    val cellHeights by lazy(LazyThreadSafetyMode.NONE) { IntArray(cells.size) { cell -> nameRowHeights[cell] + diagramSizes[cell].height } }

    /** Every cell on one line, which is what a slot answers for the widest it would be. */
    val lineWidth by lazy(LazyThreadSafetyMode.NONE) { cellWidths.sum() + gap * (cells.size - 1).coerceAtLeast(0) }
    val widestCell by lazy(LazyThreadSafetyMode.NONE) { cellWidths.maxOrNull() ?: 0 }
    val geometries by lazy(LazyThreadSafetyMode.NONE) {
        cells.map { cell ->
            cell.selection.shape?.let { chordDiagramGeometryOf(it, cell.instrument, cell.root) } ?: emptyChordDiagramGeometryOf(cell.instrument)
        }
    }
    private val rowStartsByWidth = mutableMapOf<Int, IntArray>()
    private val recentWidths = ArrayDeque<Int>()

    /** Where each row starts at [width] (see [chordRowStarts]); an unbounded width is one row. */
    fun rowStartsAt(width: Int): IntArray {
        recentWidths.remove(width)
        recentWidths.addLast(width)
        if (recentWidths.size > MAX_CHORD_ROW_WIDTHS) rowStartsByWidth.remove(recentWidths.removeFirst())
        return rowStartsByWidth.getOrPut(width) { chordRowStarts(cellWidths, gap, width) }
    }

    /** The cells of row [row] when the rows start at [starts]. */
    fun cellsOfRow(starts: IntArray, row: Int) = starts[row] until (starts.getOrNull(row + 1) ?: cells.size)

    /** How tall row [row] is: its tallest cell, since the editor preview's definitions may mix a fretted and a keyboard one. */
    fun rowHeight(starts: IntArray, row: Int) = cellsOfRow(starts, row).maxOf { cellHeights[it] }

    /**
     * How tall the slots from [firstSlot] to [lastSlot] are at [width]: their rows [gap] apart, and one more [gap] after
     * the last of them where a row follows, so that the slots stacked uncut are exactly as tall as the rows were in one
     * piece, and a cut leaves the gap at the bottom of a page rather than at the top of the next.
     */
    fun height(width: Int, firstSlot: Int, lastSlot: Int, isLastSlot: Boolean): Int {
        val starts = rowStartsAt(width)
        val rows = chordSlotRows(starts.size, firstSlot, lastSlot, isLastSlot)
        if (rows.isEmpty()) return 0
        return rows.sumOf { rowHeight(starts, it) } + gap * (rows.last - rows.first) + if (rows.last < starts.size - 1) gap else 0
    }

    /** The cells the slots from [firstSlot] to [lastSlot] show at [width], which a screen reader is told about. */
    fun cellsAt(width: Int, firstSlot: Int, lastSlot: Int, isLastSlot: Boolean): IntRange {
        val starts = rowStartsAt(width)
        val rows = chordSlotRows(starts.size, firstSlot, lastSlot, isLastSlot)
        return if (rows.isEmpty()) IntRange.EMPTY else starts[rows.first] until (starts.getOrNull(rows.last + 1) ?: cells.size)
    }
}

/**
 * One chunk of the song's chords as diagrams: the slots [items] names (counted as [RenderSection.Chords.itemCount]),
 * the first headed by a pill that folds them like any section's. The cells are drawn in rows that wrap rather than
 * scroll sideways, since a song may be read with a pedal, one slot per row the section may be cut between, as a run of
 * tablature is drawn as systems, and take no press: a tap on them turns the page like a tap anywhere on the song.
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
        ChordsSectionHeader(
            isFolded = section.isFolded,
            onFoldToggled = onFoldToggled,
            onShapesClicked = chordDiagrams?.onShapesClicked,
            headerStyle = headerStyle,
            fontScale = fontScale,
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
    )
}

@Composable
private fun ChordsSectionHeader(
    isFolded: Boolean,
    onFoldToggled: (() -> Unit)?,
    onShapesClicked: (() -> Unit)?,
    headerStyle: TextStyle,
    fontScale: Float,
) = Row(verticalAlignment = Alignment.CenterVertically) {
    SectionHeaderPill(
        header = stringResource(Res.string.song_details_chords),
        toggle = onFoldToggled?.let {
            FoldToggle(
                isExpanded = !isFolded,
                onToggled = it,
            )
        },
        style = headerStyle,
        chevronSize = FOLD_CHEVRON_SIZE * fontScale,
    )
    // As tall as the pill and growing with it, like the controls of the song's first section. It comes and goes with
    // the diagrams, since choosing a shape is choosing between the diagrams it would be drawn next to.
    onShapesClicked?.let {
        AnimatedVisibility(
            visible = !isFolded,
            // Unclipped, since a clip cuts the round button off while the room for it opens and closes: the scale is
            // what shows it arriving instead.
            enter = fadeIn() + scaleIn() + expandHorizontally(clip = false),
            exit = fadeOut() + scaleOut() + shrinkHorizontally(clip = false),
        ) {
            val height = songControlHeight(headerStyle)
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides height) {
                IconButton(
                    modifier = Modifier.size(height),
                    onClick = it,
                ) {
                    Icon(
                        modifier = Modifier.size(SHAPES_ICON_SIZE * fontScale),
                        painter = painterResource(Res.drawable.ic_edit),
                        contentDescription = stringResource(Res.string.song_details_chord_shapes),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
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
 */
@Composable
private fun ChordRowsBlock(
    modifier: Modifier = Modifier,
    layouts: ChordCellLayouts,
    firstSlot: Int,
    lastSlot: Int,
    isLastSlot: Boolean,
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
    val measurePolicy = remember(layouts, firstSlot, lastSlot, isLastSlot) { ChordRowsMeasurePolicy(layouts, firstSlot, lastSlot, isLastSlot) }
    Layout(
        modifier = modifier
            .onSizeChanged { shownWidth = it.width }
            .then(if (description.isEmpty()) Modifier else Modifier.semantics { contentDescription = description })
            .drawBehind {
                val width = size.width.roundToInt()
                val starts = layouts.rowStartsAt(width)
                val isRightToLeft = layoutDirection == LayoutDirection.Rtl
                var y = 0f
                for (row in chordSlotRows(starts.size, firstSlot, lastSlot, isLastSlot)) {
                    var x = 0
                    for (cell in layouts.cellsOfRow(starts, row)) {
                        val cellWidth = minOf(layouts.cellWidths[cell], width)
                        val cellLeft = if (isRightToLeft) width - x - cellWidth else x
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
                        )
                        x += cellWidth + layouts.gap
                    }
                    y += layouts.rowHeight(starts, row) + layouts.gap
                }
            },
        measurePolicy = measurePolicy,
    )
}

/** Draws [cell] of [layouts] [width] wide with its top left corner at [left] and [top], see [ChordRowsBlock]. */
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
        drawChordDiagram(
            geometry = layouts.geometries[cell],
            area = Size(diagramSize.width.toFloat(), diagramSize.height.toFloat()),
            lineColor = lineColor,
            mutedColor = mutedColor,
            rootColor = nameColor,
            backgroundColor = backgroundColor,
            textMeasurer = diagramTextMeasurer,
        )
    }
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
 * How a chord is read out: its name, the one it sounds as where that differs or else its letters where the page counts
 * it, and its shape as it would be dictated.
 */
@Composable
internal fun chordCellDescription(cell: ChordCell): String {
    val name = cell.soundingName?.let { textResource(Res.string.song_details_chord_sounding, cell.name, it) }
        ?: cell.letterName?.let { textResource(Res.string.song_details_chord_letters, cell.name, it) }
        ?: cell.name
    val shape = cell.selection.shape ?: return textResource(Res.string.song_details_chord_diagram_none, name)
    return textResource(Res.string.song_details_chord_diagram, name, spokenShape(shape))
}

/** A fretted shape as its frets from the lowest string, a keyboard one as the notes it presses from the lowest up. */
private fun spokenShape(shape: ChordVoicing) = when (shape) {
    is ChordVoicing.Fretted -> ChordVoicings.write(shape)
    is ChordVoicing.Keys -> (listOfNotNull(shape.bass) + shape.notes).joinToString(" ") { note ->
        ChordProChords.noteNames(Chord(root = note % 12, intervals = listOf(0))).first()
    }
}

private val CELL_GAP = 6.dp
private val NAME_GAP = 4.dp
private val SHAPES_ICON_SIZE = 18.dp
private val FRETTED_WIDTH = 56.dp
private val FRETTED_HEIGHT = 70.dp
private val KEYBOARD_WIDTH = 76.dp
private val KEYBOARD_HEIGHT = 40.dp

/** How many widths a section's rows are kept for, see [ChordCellLayouts.rowStartsAt]. */
private const val MAX_CHORD_ROW_WIDTHS = 8
