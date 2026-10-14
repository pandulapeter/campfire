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

import com.pandulapeter.campfire.chordpro.edit.ChordProTabWrapper
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.CommentStyle
import com.pandulapeter.campfire.chordpro.model.GridToken
import com.pandulapeter.campfire.chordpro.model.SectionType
import com.pandulapeter.campfire.data.model.domain.PrintSettings
import com.pandulapeter.campfire.presentation.ui.songLayout.FoldableKind
import com.pandulapeter.campfire.presentation.ui.songLayout.alignedGridBars
import com.pandulapeter.campfire.presentation.ui.songLayout.firstSection
import com.pandulapeter.campfire.presentation.ui.songLayout.foldableKind
import com.pandulapeter.campfire.presentation.ui.songLayout.groupIntoRuns
import com.pandulapeter.campfire.presentation.ui.songLayout.header
import com.pandulapeter.campfire.presentation.ui.songLayout.joinCutSections
import com.pandulapeter.campfire.presentation.ui.songLayout.padLyricsToFitChords
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
) {
    // The page reads from the title down to the lyrics: the title large, the details and the section labels smaller and
    // gray, so that a label is never taken for a chord, and only tablature and grids in the monospace face.
    val titleStyle = PrintStyle(size = (options.fontSize * 1.6f).roundToInt(), bold = true)
    private val lyricStyle = PrintStyle(size = options.fontSize)
    val chordStyle = PrintStyle(size = options.fontSize, bold = true)
    private val annotationStyle = PrintStyle(size = options.fontSize, italic = true)
    private val commentStyle = PrintStyle(size = options.fontSize, italic = true, gray = 90)
    val detailStyle = PrintStyle(size = options.fontSize - 1, gray = 90)
    private val labelStyle = PrintStyle(size = options.fontSize - 1, bold = true, gray = 90)
    val overviewArtistStyle = PrintStyle(size = options.fontSize, gray = 90)
    private val tabStyle = PrintStyle(size = options.fontSize, monospace = true)
    private val gridStyle = PrintStyle(size = options.fontSize, bold = true, monospace = true)
    private val labelSpace = options.fontSize * 0.3f

    // Chord names, spaces and the other short strings are measured over and over; only they are kept, so that the
    // substrings of lyrics the wrapping tries are not held for the life of a large setlist.
    private val shortMeasurements = mutableMapOf<Pair<String, PrintStyle>, Float>()

    fun measure(text: String, style: PrintStyle): Float =
        if (text.length <= 8) shortMeasurements.getOrPut(text to style) { measureText(text, style) } else measureText(text, style)

    fun tempoDetails(tempo: String?, time: String?) = if (options.showTempo) {
        listOfNotNull(
            tempo?.takeIf { it.isNotBlank() }?.let { "${labels.tempo}: ${if (it.all(Char::isDigit)) labels.tempoValue.replace(TEMPO_VALUE, it) else it}" },
            time?.takeIf { it.isNotBlank() }?.let { "${labels.time}: $it" },
        )
    } else {
        emptyList()
    }

    /**
     * The rows of a group of `joinCutSections`, in the segments the breaks inside it cut it into, a segment that prints
     * nothing being empty, so that the layout still sees every break: a page has columns to break, which the screen does
     * not. Every other cut of a section is joined over as the screen joins it, so that a section is one unit to place.
     */
    fun groupSegments(group: List<ChordProBlock>, width: Float): List<List<Row>> = when (val first = group.firstSection()) {
        is ChordProBlock.Section -> sectionSegments(group, first, width, labelOverride = null).segments
        is ChordProBlock.Comment -> listOf(commentRows(first, width))
        is ChordProBlock.ChorusRecall -> listOf(recallRows(first, width))
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
    ): SectionSegments {
        val isChorus = first.type == SectionType.Chorus
        val lineWidth = if (isChorus) width - CHORUS_INDENT else width
        // A paragraph of lyrics is headed on screen by its fold toggle alone, an empty header, which a page has no use for.
        val sectionLabel = first.header(labels.sections).ifEmpty { null }
        val segments = mutableListOf(mutableListOf<SectionPart>())
        group.forEach { block ->
            val parts = segments.last()
            when (block) {
                is ChordProBlock.Section -> if (block.lines.isNotEmpty()) {
                    val previous = parts.lastOrNull()
                    if (previous is SectionPart.Lines) parts[parts.lastIndex] = SectionPart.Lines(previous.lines + block.lines) else parts += SectionPart.Lines(block.lines)
                }
                is ChordProBlock.Comment -> if (commentRows(block, lineWidth).isNotEmpty()) parts += SectionPart.Comment(block)
                is ChordProBlock.Transpose -> if (keyChangeRows(block, lineWidth).isNotEmpty()) parts += SectionPart.KeyChange(block)
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
                if (isChorus && all.isNotEmpty()) chorusBar(all, startsWithLabel = labelRows.isNotEmpty()) else all
            },
            isLabelled = labelledIndex >= 0,
        )
    }

    /** What a part of a section prints, nothing for lines that print only blank rows. */
    private fun partRows(part: SectionPart, sectionLabel: String?, lineWidth: Float): List<Row> = when (part) {
        is SectionPart.Lines -> part.lines.groupIntoRuns().flatMap { run ->
            when (run.first().foldableKind()) {
                FoldableKind.TAB -> if (options.showChords) tabRows(run, sectionLabel, lineWidth) else emptyList()
                FoldableKind.GRID -> if (options.showChords) gridRows(run, lineWidth) else emptyList()
                null -> run.flatMap { line ->
                    when (line) {
                        is ChordProLine.Lyrics -> lyricsRows(line, lineWidth)
                        ChordProLine.Blank -> listOf(Row(emptyList(), options.fontSize * 0.7f))
                        is ChordProLine.Tab, is ChordProLine.Grid -> emptyList()
                    }
                }
            }
        }.takeIf { rows -> rows.any { row -> row.parts.any { !it.text.isPrintBlank() } } }.orEmpty()
        is SectionPart.Comment -> commentRows(part.comment, lineWidth)
        is SectionPart.KeyChange -> keyChangeRows(part.keyChange, lineWidth)
    }

    /** The parts that print anything, one under the other, with the gap between them that the layout leaves between two units. */
    private fun List<List<Row>>.joinedWithGaps(): List<Row> = filter { it.isNotEmpty() }.flatMapIndexed { index, rows ->
        if (index > 0) listOf(Row(emptyList(), options.fontSize * 0.65f)) + rows else rows
    }

    private fun lyricsRows(line: ChordProLine.Lyrics, lineWidth: Float): List<Row> {
        val visible = line.copy(chords = line.chords.filter { if (it.isAnnotation) options.showComments else options.showChords })
        // Hiding a chord-only line must also remove the empty lyric row beneath it.
        if (line.chords.isNotEmpty() && visible.chords.isEmpty() && visible.text.isPrintBlank()) return emptyList()
        // A chord wider than the column is wrapped onto rows of its own, so the lyrics under it are padded to a column at
        // most rather than to its full width.
        val lyrics = if (visible.chords.isNotEmpty()) {
            visible.padLyricsToFitChords(
                chordWidths = visible.chords.map { chord ->
                    minOf(measure(chord.name, if (chord.isAnnotation) annotationStyle else chordStyle), lineWidth)
                },
                gap = measure(" ", lyricStyle),
                paddingWidth = measure("\u00A0", lyricStyle),
                measureWidth = { measure(it, lyricStyle) },
            )
        } else {
            visible
        }
        val chordLineHeight = options.fontSize * 1.3f
        val fragments = wrapPrintText(lyrics.text, lineWidth) { measure(it, lyricStyle) }
        var start = 0
        return buildList {
            fragments.forEachIndexed { fragmentIndex, fragment ->
                val end = start + fragment.length
                val chords = lyrics.chords.filter { it.position >= start && (it.position < end || fragmentIndex == fragments.lastIndex) }
                // Chords anchored at the same character are kept beside each other; if the chord run is wider than the
                // column, extra chord-only rows keep every symbol readable.
                val parts = mutableListOf<Part>()
                var chordX = 0f
                var chordY = 0f
                chords.forEach { chord ->
                    val anchor = measure(fragment.take((chord.position - start).coerceIn(0, fragment.length)), lyricStyle)
                    var x = maxOf(anchor, chordX)
                    // An annotation is an instruction rather than a chord, so it is set apart from the chords.
                    val style = if (chord.isAnnotation) annotationStyle else chordStyle
                    val chordWidth = measure(chord.name, style)
                    if (x > 0f && x + chordWidth > lineWidth) {
                        chordY += chordLineHeight
                        x = 0f
                    }
                    val pieces = wrapPrintText(chord.name, lineWidth) { measure(it, style) }
                    pieces.forEachIndexed { pieceIndex, name ->
                        if (pieceIndex > 0) {
                            chordY += chordLineHeight
                            x = 0f
                        }
                        parts += Part(name, x = x, y = chordY, style = style)
                    }
                    val lastWidth = if (pieces.size == 1) chordWidth else measure(pieces.last(), style)
                    chordX = x + lastWidth + measure(" ", lyricStyle)
                }
                // A fragment of chords over nothing but padding is one row of chords, not chords over an empty lyric row;
                // a blank fragment without chords is an empty line the song asked for.
                val isChordsOnly = parts.isNotEmpty() && fragment.isPrintBlank()
                val lyricY = if (parts.isEmpty()) 0f else chordY + chordLineHeight
                if (!isChordsOnly) parts += Part(fragment, y = lyricY, style = lyricStyle)
                val rowHeight = (if (isChordsOnly) chordY else lyricY) + options.fontSize * 1.45f
                if (rowHeight <= capacity) {
                    add(Row(parts, rowHeight))
                } else {
                    // A very long annotation can span a page by itself. Keep its final line with the lyrics while
                    // allowing its preceding lines to flow through the document.
                    val byLine = parts.groupBy { it.y }.entries.toList()
                    byLine.dropLast(2).forEach { (_, values) -> add(Row(values.map { it.copy(y = 0f) }, chordLineHeight)) }
                    val last = byLine.takeLast(2)
                    val top = last.first().key
                    add(Row(last.flatMap { it.value }.map { it.copy(y = it.y - top) }, options.fontSize * 2.75f))
                }
                start = end
            }
        }
    }

    /** A run of tab lines (`groupIntoRuns`), wrapped into systems of the column's width. */
    private fun tabRows(lines: List<ChordProLine>, sectionLabel: String?, lineWidth: Float): List<Row> {
        val run = lines.map { (it as ChordProLine.Tab).text }
        return buildList {
            (lines.first() as ChordProLine.Tab).label?.takeUnless { it == sectionLabel }?.let { addAll(labelRows(it, lineWidth)) }
            val characters = (lineWidth / measure("M", tabStyle)).toInt().coerceAtLeast(1)
            val isTablature = ChordProTabWrapper.isTablature(run)
            val systems = if (isTablature) {
                ChordProTabWrapper.wrap(run, characters)
            } else {
                ChordProTabWrapper.wrapPreformatted(run, characters)
            }
            systems.forEachIndexed { systemIndex, system ->
                // Staves without a gap between them read as one staff of twice the strings; a preformatted run is chord
                // names over lyrics, read like wrapped text, so it gets none.
                if (isTablature && systemIndex > 0) add(Row(emptyList(), options.fontSize * 0.7f))
                val rows = system.flatMap { wrapped(it, tabStyle, lineWidth) }
                val systemHeight = rows.sumOf { it.height.toDouble() }.toFloat()
                if (systemHeight <= capacity) {
                    var rowY = 0f
                    add(Row(rows.flatMap { row -> row.parts.map { it.copy(y = rowY) }.also { rowY += row.height } }, systemHeight))
                } else {
                    addAll(rows)
                }
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
        keyChange.key?.takeIf { options.showKey && it.isNotBlank() }?.let { wrapped("${labels.key}: $it", detailStyle, width) }.orEmpty()

    /**
     * How the song is played from a `{tempo}` or a `{time}` further down on, in the words and the type the heading names
     * the opening values in, so that an import of the PDF reads it back as the change it is. Only what the song names is
     * printed, since a time signature the file never gave would come back as the song's own.
     */
    private fun timingRows(timing: ChordProBlock.Timing, width: Float): List<Row> =
        tempoDetails(timing.tempo, timing.time).joinToString(METADATA_SEPARATOR).takeIf { it.isNotEmpty() }?.let { wrapped(it, detailStyle, width) }.orEmpty()

    private fun commentRows(comment: ChordProBlock.Comment, width: Float): List<Row> = when {
        !options.showComments || (comment.isInTabOrGrid && !options.showChords) -> emptyList()
        comment.style == CommentStyle.BOX -> boxedRows(comment.text, width)
        else -> wrapped(comment.text, commentStyle, width)
    }

    /**
     * As in the viewer, the recall's own heading goes on the first recalled section that prints its lines, and is printed
     * on the chorus's bar after whatever else of it prints when none does: a recall says where the chorus is sung, even
     * with nothing under it. A recall is one unit, the breaks the parser copied into it included.
     */
    private fun recallRows(recall: ChordProBlock.ChorusRecall, width: Float): List<Row> {
        val recalled = recall.blocks.firstOrNull { it is ChordProBlock.Section } as? ChordProBlock.Section
        var header: String? = recall.label ?: recalled?.header(labels.sections) ?: labels.sections.chorus
        val rows = recall.blocks.joinCutSections().flatMap { group ->
            when (val first = group.firstSection()) {
                is ChordProBlock.Section -> sectionSegments(group, first, width, labelOverride = header).let {
                    if (it.isLabelled) header = null
                    it.segments.flatten()
                }
                is ChordProBlock.Comment -> commentRows(first, width)
                else -> emptyList()
            }
        }
        return rows + header?.let { chorusBar(labelRows(it, width - CHORUS_INDENT), startsWithLabel = true) }.orEmpty()
    }

    /**
     * A line break inside a text (a setlist description has up to three lines) starts a row of its own, since a row is
     * one line tall and the renderer would draw the rest over whatever comes under it.
     */
    fun wrapped(
        text: String,
        style: PrintStyle = lyricStyle,
        width: Float = columnWidth,
        isSelectable: Boolean = true,
    ): List<Row> = text.lines().flatMap { line ->
        wrapPrintText(line, width) { measure(it, style) }.map { Row(listOf(Part(it, style = style, isSelectable = isSelectable)), style.size * 1.45f) }
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
        val rows = wrapped(text, lyricStyle, width - 10f)
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
     * A chorus is indented, with a bar down its left side that every row carries its own piece of, so that the bar
     * continues without a gap across a column or page break; on a label's row it starts at the label, not above it.
     */
    private fun chorusBar(rows: List<Row>, startsWithLabel: Boolean): List<Row> = rows.mapIndexed { index, row ->
        val top = if (index == 0 && startsWithLabel) labelSpace else 0f
        Row(
            parts = row.parts.map { it.copy(x = it.x + CHORUS_INDENT) },
            height = row.height,
            rules = row.rules.map { it.copy(x = it.x + CHORUS_INDENT) } +
                PrintRule(x = 0f, y = top, width = CHORUS_BAR_WIDTH, height = row.height - top),
        )
    }
}

/** A text of a [Row], placed relative to the row's top left corner. */
internal data class Part(
    val text: String,
    val x: Float = 0f,
    val y: Float = 0f,
    val style: PrintStyle,
    val isSelectable: Boolean = true,
)

/** The unit [PrintLayouter.place] never splits: a lyric line with its chords, a tab system, a row of chord diagrams. */
internal data class Row(
    val parts: List<Part>,
    val height: Float,
    val rules: List<PrintRule> = emptyList(),
    val diagrams: List<PrintDiagram> = emptyList(),
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
    data class Lines(val lines: List<ChordProLine>) : SectionPart
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

/** Blank once the padding the chords add (no-break spaces and the zero-width break opportunities) is disregarded too. */
private fun String.isPrintBlank() = all { it.isWhitespace() || it == '\u00A0' || it == '\u200B' }
