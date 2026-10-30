/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.print

import com.pandulapeter.campfire.presentation.ui.notation.NotationDrawing
import com.pandulapeter.campfire.presentation.ui.notation.AbcTune
import com.pandulapeter.campfire.presentation.ui.notation.abcTune
import com.pandulapeter.campfire.chordpro.tabs.ChordProTabWrapper
import com.pandulapeter.campfire.chordpro.language.WritingDirection
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.CommentStyle
import com.pandulapeter.campfire.chordpro.model.GridToken
import com.pandulapeter.campfire.chordpro.model.SectionType
import com.pandulapeter.campfire.chordpro.syntax.ChordProMarkup
import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.presentation.ui.songLayout.FoldableKind
import com.pandulapeter.campfire.presentation.ui.songLayout.alignedGridBars
import com.pandulapeter.campfire.presentation.ui.songLayout.firstSection
import com.pandulapeter.campfire.presentation.ui.songLayout.foldableKind
import com.pandulapeter.campfire.presentation.ui.songLayout.groupIntoRuns
import com.pandulapeter.campfire.presentation.ui.songLayout.header
import com.pandulapeter.campfire.presentation.ui.songLayout.joinCutSections
import kotlin.math.roundToInt

/**
 * The rows a printed song is made of, built at the width of a column of the layout and in the styles its options give:
 * the blocks of a song, and the wrapped texts the layout prints around them. Rows taller than [capacity], a column's
 * height, are cut into rows that fit one.
 */
internal class PrintRows(
    private val options: PrintSettings,
    private val labels: PrintLabels,
    private val columnWidth: Float,
    private val capacity: Float,
    private val measureText: (text: String, style: PrintStyle) -> Float,
    measureLyric: PrintLyricMeasure?,
) {
    /**
     * The direction of the song being laid out (`ChordProSong.textDirection`), which its own texts are set in and its
     * rows laid out in: a right-to-left song's from the right edge of the column. Music reads left to right whatever the
     * song does, so tablature, grids, and sheet music keep their insides and only move to that edge. Null outside a song.
     */
    var direction: WritingDirection? = null

    // The page reads from the title down to the lyrics: the title large, the details and the section labels smaller and
    // gray, so that a label is never taken for a chord, and only tablature and grids in the monospace face.
    val titleStyle get() = PrintStyle(size = (options.fontSize * 1.6f).roundToInt().toFloat(), bold = true, direction = direction)
    private val lyricStyle get() = PrintStyle(size = options.fontSize.toFloat(), direction = direction)
    val chordStyle = PrintStyle(size = options.fontSize.toFloat(), bold = true)
    private val annotationStyle = PrintStyle(size = options.fontSize.toFloat(), italic = true)
    private val commentStyle get() = PrintStyle(size = options.fontSize.toFloat(), italic = true, gray = 90, direction = direction)
    val detailStyle get() = PrintStyle(size = options.fontSize - 1f, gray = 90, direction = direction)
    val labelStyle get() = PrintStyle(size = options.fontSize - 1f, bold = true, gray = 90, direction = direction)
    val overviewArtistStyle = PrintStyle(size = options.fontSize.toFloat(), gray = 90)
    private val tabStyle = PrintStyle(size = options.fontSize.toFloat(), monospace = true, direction = WritingDirection.LEFT_TO_RIGHT)
    private val gridStyle = PrintStyle(size = options.fontSize.toFloat(), bold = true, monospace = true, direction = WritingDirection.LEFT_TO_RIGHT)
    private val tabEngraver = PrintTabEngraver(options.fontSize.toFloat(), ::measure)
    private val lyricRows = PrintLyricRows(options, capacity, ::measure, measureLyric)
    private val labelSpace = options.fontSize * 0.3f
    private val isRightToLeft get() = direction == WritingDirection.RIGHT_TO_LEFT

    /** The engraved ABC tunes of the song being laid out, by tune; a run with none prints as its text. */
    var notations: Map<AbcTune, NotationDrawing> = emptyMap()

    // Chord names, spaces and the other short strings are measured over and over; only they are kept, so that the
    // substrings of lyrics the wrapping tries are not held for the life of a large setlist.
    private val shortMeasurements = mutableMapOf<Pair<String, PrintStyle>, Float>()

    fun measure(text: String, style: PrintStyle): Float =
        if (text.length <= 8) shortMeasurements.getOrPut(text to style) { measureText(text, style) } else measureText(text, style)

    fun tempoDetails(tempo: String?, time: String?) = if (options.showTempo) {
        listOfNotNull(
            tempo?.takeIf { it.isNotBlank() }?.let { labels.labelled(labels.tempo, if (it.all(Char::isDigit)) labels.tempoValue.replace(TEMPO_VALUE, it) else it) },
            time?.takeIf { it.isNotBlank() }?.let { labels.labelled(labels.time, it) },
        )
    } else {
        emptyList()
    }

    /**
     * The rows of a group of `joinCutSections`, in the segments the breaks inside it cut it into, a segment that prints
     * nothing being empty, so that the layout still sees every break: a page has columns to break, which the screen does
     * not. Every other cut of a section is joined over as the screen joins it, so that a section is one unit to place.
     * [startOffset] is the key change the group starts under (`groupStartOffsetsOf`), which moves its ABC tunes.
     */
    fun groupSegments(group: List<ChordProBlock>, width: Float, startOffset: Int): List<List<Row>> = when (val first = group.firstSection()) {
        is ChordProBlock.Section -> sectionSegments(group, first, width, labelOverride = null, startOffset).segments
        is ChordProBlock.Comment -> listOf(commentRows(first, width))
        is ChordProBlock.ChorusRecall -> listOf(recallRows(first, width, startOffset))
        is ChordProBlock.Transpose -> listOf(keyChangeRows(first, width))
        is ChordProBlock.Timing -> listOf(timingRows(first, width))
        ChordProBlock.Break -> listOf(emptyList())
    }

    /**
     * A section's group as the screen's `addSection` builds it: its lines, joined into one run of lines again over a cut
     * that prints nothing, and the comments and key changes that cut it or open or end it, with a gap between two, all
     * of a chorus indented behind its bar. The label goes on the first segment that prints anything, so that it is never
     * left at the bottom of a column with what it names in the next.
     */
    private fun sectionSegments(
        group: List<ChordProBlock>,
        first: ChordProBlock.Section,
        width: Float,
        labelOverride: String?,
        startOffset: Int,
    ): SectionSegments {
        val isChorus = first.type == SectionType.Chorus
        val lineWidth = if (isChorus) width - CHORUS_INDENT else width
        // A paragraph of lyrics is headed on screen by its fold toggle alone, an empty header, which a page has no use for.
        val sectionLabel = first.header(labels.sections).ifEmpty { null }
        val segments = mutableListOf(mutableListOf<SectionPart>())
        var tuneOffset = startOffset
        group.forEach { block ->
            val parts = segments.last()
            when (block) {
                is ChordProBlock.Section -> if (block.lines.isNotEmpty()) {
                    val previous = parts.lastOrNull()
                    if (previous is SectionPart.Lines) {
                        parts[parts.lastIndex] = SectionPart.Lines(previous.lines + block.lines, previous.tuneOffset)
                    } else {
                        parts += SectionPart.Lines(block.lines, tuneOffset)
                    }
                }
                is ChordProBlock.Comment -> if (commentRows(block, lineWidth).isNotEmpty()) parts += SectionPart.Comment(block)
                is ChordProBlock.Transpose -> {
                    tuneOffset = block.semitones
                    if (keyChangeRows(block, lineWidth).isNotEmpty()) parts += SectionPart.KeyChange(block)
                }
                ChordProBlock.Break -> segments += mutableListOf<SectionPart>()
                is ChordProBlock.Timing, is ChordProBlock.ChorusRecall -> Unit
            }
        }
        val printed = segments.map { parts -> parts.map { partRows(it, sectionLabel, lineWidth) } }
        val printsLines = segments.zip(printed).any { (parts, rows) -> parts.indices.any { parts[it] is SectionPart.Lines && rows[it].isNotEmpty() } }
        // A section whose every line the options hide leaves out its label too, rather than printing a heading with
        // nothing under it, and keeps only its comments, outside it as the screen keeps them; one written with no lines
        // at all keeps its label, since there the label is the cue.
        if (!printsLines && group.any { it is ChordProBlock.Section && it.lines.isNotEmpty() }) {
            return SectionSegments(
                segments = segments.map { parts -> parts.filterIsInstance<SectionPart.Comment>().map { commentRows(it.comment, width) }.joinedWithGaps() },
                isLabelled = false,
            )
        }
        val label = labelOverride ?: sectionLabel?.takeUnless { first.isContinuation }
        val rows = printed.map { it.joinedWithGaps() }
        val labelledIndex = if (label == null) -1 else rows.indexOfFirst { it.isNotEmpty() }.coerceAtLeast(0)
        return SectionSegments(
            segments = rows.mapIndexed { index, segment ->
                val labelRows = if (index == labelledIndex && label != null) labelRows(label, lineWidth) else emptyList()
                val all = labelRows + segment
                if (isChorus && all.isNotEmpty()) chorusBar(all, startsWithLabel = labelRows.isNotEmpty(), width) else all
            },
            isLabelled = labelledIndex >= 0,
        )
    }

    /** What a part of a section prints, nothing for lines that print only blank rows. */
    private fun partRows(part: SectionPart, sectionLabel: String?, lineWidth: Float): List<Row> = when (part) {
        is SectionPart.Lines -> part.lines.groupIntoRuns().flatMap { run ->
            when (run.first().foldableKind()) {
                FoldableKind.TAB -> if (options.showTabs) tabRows(run, sectionLabel, lineWidth) else emptyList()
                FoldableKind.GRID -> if (options.showChords) gridRows(run, lineWidth) else emptyList()
                FoldableKind.VERBATIM -> if (options.showSheetMusic) verbatimRows(run, part.tuneOffset, lineWidth) else emptyList()
                null -> run.flatMap { line ->
                    when (line) {
                        is ChordProLine.Lyrics -> lyricRows.rows(line, lineWidth, lyricStyle, chordStyle, annotationStyle)
                        ChordProLine.Blank -> listOf(Row(emptyList(), options.fontSize * 0.7f))
                        is ChordProLine.Tab, is ChordProLine.Grid, is ChordProLine.Verbatim -> emptyList()
                    }
                }
            }
        }.takeIf { rows -> rows.any { row -> row.notations.isNotEmpty() || row.parts.any { !it.text.isPrintBlank() } } }.orEmpty()
        is SectionPart.Comment -> commentRows(part.comment, lineWidth)
        is SectionPart.KeyChange -> keyChangeRows(part.keyChange, lineWidth)
    }

    /** The parts that print anything, one under the other, with the gap between them that the layout leaves between two units. */
    private fun List<List<Row>>.joinedWithGaps(): List<Row> = filter { it.isNotEmpty() }.flatMapIndexed { index, rows ->
        if (index > 0) listOf(Row(emptyList(), options.fontSize * 0.65f)) + rows else rows
    }

    /** A run of tab lines (`groupIntoRuns`), wrapped into systems of the column's width. */
    private fun tabRows(lines: List<ChordProLine>, sectionLabel: String?, lineWidth: Float): List<Row> {
        val run = lines.map { (it as ChordProLine.Tab).text }
        val label = (lines.first() as ChordProLine.Tab).label?.takeUnless { it == sectionLabel }?.let { labelRows(it, lineWidth) }.orEmpty()
        val systems = if (ChordProTabWrapper.isTablature(run)) tabEngraver.rows(run, lineWidth) else monospaceRows(run, isSelectable = true, lineWidth)
        return label + systems.atLineEnd(lineWidth)
    }

    /**
     * A run of verbatim lines (`groupIntoRuns`), always preformatted, as the viewer draws it. Another program's notation
     * is left out of the selectable text: read back by the importer, ABC's `"G"` and `[CEG]` would become chords.
     */
    private fun verbatimRows(lines: List<ChordProLine>, tuneOffset: Int, lineWidth: Float): List<Row> =
        lines.abcTune()?.let { notations[AbcTune(it, tuneOffset)] }?.let { notationRows(it, lineWidth) }
            ?: monospaceRows(lines.map { (it as ChordProLine.Verbatim).text }, isSelectable = false, lineWidth).atLineEnd(lineWidth)

    /**
     * An engraved ABC tune, a row per staff system so that a page is never cut through one: at the size that sets its
     * lyrics as large as the song's ([printNotationUnit]), or smaller in a column narrower than the page it was rendered
     * for (a chorus is indented). Drawn rather than written, so nothing of it reaches the selectable text.
     */
    private fun notationRows(drawing: NotationDrawing, lineWidth: Float): List<Row> {
        val pageWidth = drawing.systems.maxOfOrNull { it.width } ?: return emptyList()
        val scale = minOf(printNotationUnit(options), lineWidth / pageWidth)
        val x = if (isRightToLeft) lineWidth - pageWidth * scale else 0f
        return drawing.systems.mapIndexed { index, system ->
            Row(parts = emptyList(), height = system.height * scale, notations = listOf(PrintNotation(drawing, index, x = x, y = 0f, scale = scale)))
        }
    }

    /**
     * The rows of a run of preformatted lines (chord names over lyrics most often), read like wrapped text with no gap
     * between its pieces. A tab run's line of chord names prints in the chords' weight, as on screen, but reaches the
     * selectable text as the file has it ([PrintTabEngraver.selectable]).
     */
    private fun monospaceRows(run: List<String>, isSelectable: Boolean, lineWidth: Float): List<Row> = buildList {
        val characters = (lineWidth / measure("M", tabStyle)).toInt().coerceAtLeast(1)
        ChordProTabWrapper.wrapPreformatted(run, characters).forEach { system ->
            val rows = system.flatMap { line ->
                if (!isSelectable || !ChordProTabWrapper.isChordLine(line)) return@flatMap wrapped(line, tabStyle, lineWidth, isSelectable)
                wrapped(line, gridStyle, lineWidth, isSelectable = false).map { row -> row.copy(parts = row.parts + row.parts.map(tabEngraver::selectable)) }
            }
            val systemHeight = rows.sumOf { it.height.toDouble() }.toFloat()
            if (systemHeight <= capacity) {
                var rowY = 0f
                add(Row(rows.flatMap { row -> row.parts.map { it.copy(y = rowY) }.also { rowY += row.height } }, systemHeight))
            } else {
                addAll(rows)
            }
        }
    }

    /**
     * A run of grid lines (`groupIntoRuns`), aligned into columns as the viewer aligns them, each line broken
     * between bars as the viewer breaks it, a row taking as many whole bars as fit; only a bar wider than the column is
     * wrapped inside itself.
     */
    private fun gridRows(lines: List<ChordProLine>, lineWidth: Float): List<Row> {
        val run = lines.map { (it as ChordProLine.Grid).tokens }
        return run.alignedGridBars { it.printText() }.flatMap { bars -> gridLineRows(bars.map { bar -> bar.joinToString("") { it.text } }, lineWidth) }
            .atLineEnd(lineWidth)
    }

    private fun gridLineRows(bars: List<String>, lineWidth: Float): List<Row> = buildList {
        var row = ""
        bars.forEach { text ->
            if (row.isNotEmpty() && measure(row + text.trimEnd(), gridStyle) <= lineWidth) {
                row += text
            } else {
                if (row.isNotEmpty()) addAll(wrapped(row.trimEnd(), gridStyle, lineWidth))
                if (measure(text.trimEnd(), gridStyle) <= lineWidth) {
                    row = text
                } else {
                    addAll(wrapped(text.trimEnd(), gridStyle, lineWidth))
                    row = ""
                }
            }
        }
        if (row.isNotEmpty()) addAll(wrapped(row.trimEnd(), gridStyle, lineWidth))
    }

    /** The key a `{transpose}` further down takes the song to, for a song that declares one, as the heading names its first. */
    private fun keyChangeRows(keyChange: ChordProBlock.Transpose, width: Float): List<Row> =
        keyChange.key?.takeIf { options.showKey && it.isNotBlank() }?.let { wrapped(labels.labelled(labels.key, it), detailStyle, width) }.orEmpty()

    /**
     * How the song is played from a `{tempo}` or a `{time}` further down on, in the words and the type the heading names
     * the opening values in, so that an import of the PDF reads it back as the change it is. Only what the song names is
     * printed, since a time signature the file never gave would come back as the song's own.
     */
    private fun timingRows(timing: ChordProBlock.Timing, width: Float): List<Row> = detailRows(tempoDetails(timing.tempo, timing.time), width)

    /**
     * The labelled values [details] (`Key: Am`, `Tempo: 96 BPM`) in a row, as many to a row as fit [width]: a row breaks
     * between two of them rather than inside one, which an import of the PDF would read as the label's whole value and
     * a lyric under it.
     */
    fun detailRows(details: List<String>, width: Float = columnWidth): List<Row> = details.fold(emptyList<String>()) { lines, detail ->
        val joined = lines.lastOrNull()?.let { it + METADATA_SEPARATOR + detail }
        if (joined != null && measure(joined, detailStyle) <= width) lines.dropLast(1) + joined else lines + detail
    }.flatMap { wrapped(it, detailStyle, width) }

    private fun commentRows(comment: ChordProBlock.Comment, width: Float): List<Row> = when {
        !options.showComments || (comment.isInTabOrGrid && !(if (comment.isInTab) options.showTabs else options.showChords)) -> emptyList()
        comment.style == CommentStyle.BOX -> boxedRows(comment.text, width)
        else -> formattedRows(comment.text, commentStyle, width)
    }

    /** [wrapped] for a text that may carry markup, a comment's: one text a row, carrying the markup that reaches it. */
    private fun formattedRows(text: String, style: PrintStyle, width: Float): List<Row> {
        val stripped = ChordProMarkup.strip(text)
        if (stripped.formatting.isEmpty()) return wrapped(stripped.text, style, width)
        val wrappingStyle = stripped.formatting.wrappingStyle(style)
        var start = 0
        return wrapPrintText(stripped.text, width) { measure(it, wrappingStyle) }.map { fragment ->
            val formatting = stripped.formatting.clippedTo(start, fragment)
            val x = if (style.isRightToLeft) width - styledPieces(formatting, 0, fragment, style).widthUntil(fragment.length, ::measure) else 0f
            val part = Part(fragment, x = x, style = style, formatting = formatting)
            start += fragment.length
            Row(listOf(part), style.size * 1.45f)
        }
    }

    /**
     * As in the viewer, the recall's own heading goes on the first recalled section that prints its lines, and is printed
     * on the chorus's bar after whatever else of it prints when none does: a recall says where the chorus is sung, even
     * with nothing under it. A recall is one unit, the breaks the parser copied into it included.
     */
    private fun recallRows(recall: ChordProBlock.ChorusRecall, width: Float, startOffset: Int): List<Row> {
        val recalled = recall.blocks.firstOrNull { it is ChordProBlock.Section } as? ChordProBlock.Section
        var header: String? = recall.label ?: recalled?.header(labels.sections) ?: labels.sections.chorus
        val rows = recall.blocks.joinCutSections().flatMap { group ->
            when (val first = group.firstSection()) {
                is ChordProBlock.Section -> sectionSegments(group, first, width, labelOverride = header, startOffset).let {
                    if (it.isLabelled) header = null
                    it.segments.flatten()
                }
                is ChordProBlock.Comment -> commentRows(first, width)
                else -> emptyList()
            }
        }
        return rows + header?.let { chorusBar(labelRows(it, width - CHORUS_INDENT), startsWithLabel = true, width) }.orEmpty()
    }

    /**
     * A line break inside a text (a setlist description has up to three lines) starts a row of its own, since a row is
     * one line tall and the renderer would draw the rest over whatever comes under it. A right-to-left text ends at the
     * right edge of [width].
     */
    fun wrapped(
        text: String,
        style: PrintStyle = lyricStyle,
        width: Float = columnWidth,
        isSelectable: Boolean = true,
    ): List<Row> = text.lines().flatMap { line ->
        wrapPrintText(line, width) { measure(it, style) }.map {
            val x = if (style.isRightToLeft) width - measure(it, style) else 0f
            Row(listOf(Part(it, x = x, style = style, isSelectable = isSelectable)), style.size * 1.45f)
        }
    }

    /**
     * [this], rows of music [lineWidth] wide at most, moved as one block to the right edge in a right-to-left song: a tab
     * system, a grid, or a preformatted run reads left to right, and lines of it moved one by one would no longer line
     * up in its columns.
     */
    private fun List<Row>.atLineEnd(lineWidth: Float): List<Row> {
        if (!isRightToLeft || isEmpty()) return this
        val extent = maxOf { row ->
            maxOf(row.parts.maxOfOrNull { it.x + measure(it.text, it.style) } ?: 0f, row.rules.maxOfOrNull { it.x + it.width } ?: 0f)
        }
        val shift = (lineWidth - extent).coerceAtLeast(0f)
        return map { row -> row.copy(parts = row.parts.map { it.copy(x = it.x + shift) }, rules = row.rules.map { it.copy(x = it.x + shift) }) }
    }

    /** The space above a label belongs to its first row, so that it never ends a column on its own or opens one with a gap. */
    private fun labelRows(text: String, width: Float): List<Row> = wrapped(text, labelStyle, width).mapIndexed { index, row ->
        if (index == 0) Row(row.parts.map { it.copy(y = it.y + labelSpace) }, row.height + labelSpace) else row
    }

    /**
     * Every row of a boxed comment carries its own piece of the frame, inside its own height, so that a box the placement
     * splits between columns continues as an open frame and never draws over what is before or after it.
     */
    private fun boxedRows(text: String, width: Float): List<Row> {
        val rows = formattedRows(text, lyricStyle, width - 10f)
        return rows.mapIndexed { index, row ->
            val top = if (index == 0) 3f else 0f
            val height = row.height + top + if (index == rows.lastIndex) 3f else 0f
            Row(
                parts = row.parts.map { it.copy(x = it.x + 5f, y = it.y + top) },
                height = height,
                rules = buildList {
                    add(PrintRule(x = 0f, y = 0f, width = FRAME_WIDTH, height = height))
                    add(PrintRule(x = width - FRAME_WIDTH, y = 0f, width = FRAME_WIDTH, height = height))
                    if (index == 0) add(PrintRule(x = 0f, y = 0f, width = width, height = FRAME_WIDTH))
                    if (index == rows.lastIndex) add(PrintRule(x = 0f, y = height - FRAME_WIDTH, width = width, height = FRAME_WIDTH))
                },
            )
        }
    }

    /**
     * A chorus is indented, with a bar down its starting side that every row carries its own piece of, so that the bar
     * continues without a gap across a column or page break; on a label's row it starts at the label, not above it.
     * [rows] were laid out in [width] less the indent, which in a right-to-left song is already the indent on the right.
     */
    private fun chorusBar(rows: List<Row>, startsWithLabel: Boolean, width: Float): List<Row> = rows.mapIndexed { index, row ->
        val top = if (index == 0 && startsWithLabel) labelSpace else 0f
        val indent = if (isRightToLeft) 0f else CHORUS_INDENT
        Row(
            parts = row.parts.map { it.copy(x = it.x + indent) },
            height = row.height,
            rules = row.rules.map { it.copy(x = it.x + indent) } +
                PrintRule(x = if (isRightToLeft) width - CHORUS_BAR_WIDTH else 0f, y = top, width = CHORUS_BAR_WIDTH, height = row.height - top),
            notations = row.notations.map { it.copy(x = it.x + indent) },
        )
    }
}

/**
 * A text of a [Row], placed relative to its top left corner, with [PrintText.formatting]'s markup, drawn as
 * [PrintText.isDrawn] says, and marked as [PrintText.isContinuation] says.
 */
internal data class Part(
    val text: String,
    val x: Float = 0f,
    val y: Float = 0f,
    val style: PrintStyle,
    val isSelectable: Boolean = true,
    val formatting: List<ChordProLine.Lyrics.Formatting> = emptyList(),
    val isDrawn: Boolean = true,
    val isContinuation: Boolean = false,
)

/**
 * The unit [PrintLayouter.place] never splits: a lyric line with its chords, a tab system, a row of chord diagrams, a
 * staff system.
 */
internal data class Row(
    val parts: List<Part>,
    val height: Float,
    val rules: List<PrintRule> = emptyList(),
    val diagrams: List<PrintDiagram> = emptyList(),
    val notations: List<PrintNotation> = emptyList(),
)

/**
 * What stands between two values on the line that says how a song is played: wide enough a gap for the importer to read
 * each as a value of its own.
 */
internal const val METADATA_SEPARATOR = "   "

private const val CHORUS_INDENT = 8f
private const val CHORUS_BAR_WIDTH = 1.5f
private const val FRAME_WIDTH = 0.75f

/** What a section's group prints, in order, as the screen's `SectionPart`s are. */
private sealed interface SectionPart {
    data class Lines(val lines: List<ChordProLine>, val tuneOffset: Int) : SectionPart
    data class Comment(val comment: ChordProBlock.Comment) : SectionPart
    data class KeyChange(val keyChange: ChordProBlock.Transpose) : SectionPart
}

/** The segments of a section's group, and whether one of them carries its label. */
private class SectionSegments(val segments: List<List<Row>>, val isLabelled: Boolean)

private fun GridToken.printText() = when (this) {
    is GridToken.Bar -> text
    is GridToken.Chord -> name
    is GridToken.Text -> text
    is GridToken.Repeat -> text
    GridToken.Beat -> "."
}
