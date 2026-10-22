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

import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.presentation.ui.chords.ChordDiagramGeometry
import com.pandulapeter.campfire.presentation.ui.songLayout.balancedRowStarts
import com.pandulapeter.campfire.presentation.ui.songLayout.joinCutSections
import com.pandulapeter.campfire.presentation.ui.songLayout.withNumberedSections
import kotlinx.coroutines.yield

/**
 * A single layout for the preview and export. Measuring is supplied by the same Compose text renderer that draws
 * both. A lyric/chord pair is indivisible; a section that fits in a column is kept together. Longer sections flow
 * through columns and pages without reducing the requested size or clipping the remainder.
 *
 * It suspends between songs and every few dozen blocks: on the web the layout shares the page's one thread, and only
 * while it is suspended can the page paint and a newer choice of options cancel a stale layout.
 */
/**
 * [this] with what the app's own feature switches take away left out too: the chords, their diagrams, their scales, and
 * the key with the Chords switch, the scales with the Scale suggestions one too, the tempo and the time signature with
 * the Metronome one. The options themselves are kept as they
 * were chosen, since a switch only hides, and switched back on the export prints them again.
 */
internal fun PrintSettings.withinFeatures(areChordsEnabled: Boolean, areScaleSuggestionsEnabled: Boolean, isMetronomeEnabled: Boolean) = copy(
    showChords = showChords && areChordsEnabled,
    showChordDiagrams = showChordDiagrams && areChordsEnabled,
    showScales = showScales && areChordsEnabled && areScaleSuggestionsEnabled,
    showKey = showKey && areChordsEnabled,
    showTempo = showTempo && isMetronomeEnabled,
)

internal suspend fun layoutPrintDocument(
    source: PrintSource,
    settings: PrintSettings,
    labels: PrintLabels,
    measureText: (text: String, style: PrintStyle) -> Float,
): PrintDocument = PrintLayouter(
    options = settings.normalized(),
    labels = labels,
    measureText = measureText,
).layout(source)

/**
 * One layout run: the geometry and the styles the options give, and the cursor ([column] and [y] on the last page) that
 * [place] moves on. The rows of a block are built first and placed whole, so that whether they fit is decided once.
 */
private class PrintLayouter(
    private val options: PrintSettings,
    private val labels: PrintLabels,
    measureText: (text: String, style: PrintStyle) -> Float,
) {
    private val width = if (options.isLandscape) options.paper.height else options.paper.width
    private val height = if (options.isLandscape) options.paper.width else options.paper.height
    private val margin = options.marginMm * 72f / 25.4f
    private val gutter = 18f
    private val columnWidth = (width - 2 * margin - gutter * (options.columns - 1)) / options.columns
    private val bottom = height - margin - if (options.showPageNumbers) 18f else 0f
    private val capacity = bottom - margin
    private val headingGap = options.fontSize * 0.8f

    private val printRows = PrintRows(
        options = options,
        labels = labels,
        columnWidth = columnWidth,
        capacity = capacity,
        measureText = measureText,
    )

    private val pages = mutableListOf<PageContent>()
    private var column = 0
    private var y = margin
    private var placedGroups = 0

    suspend fun layout(source: PrintSource): PrintDocument {
        page()
        if (source.hasRunningOrder && options.includeSetlistOverview) {
            overview(source)
            // Song sheets follow the running order on a fresh page, including in the compact layout.
            newPage()
        }
        source.songs.forEachIndexed { index, entry ->
            yield()
            if (index > 0 && options.startSongsOnNewPage) newPage()
            song(entry)
        }
        return finish()
    }

    private fun page() {
        pages.add(PageContent())
        column = 0
        y = margin
    }

    private fun nextColumn() {
        if (column + 1 < options.columns) {
            column++
            y = margin
        } else {
            page()
        }
    }

    private fun newPage() {
        if (pages.isNotEmpty() && pages.last().texts.isNotEmpty()) page()
    }

    private fun space(amount: Float) {
        y = (y + amount).coerceAtMost(bottom)
    }

    /**
     * Puts [rows] under the cursor: [keepWhole] rows that fit a column start a new one rather than being split, and
     * otherwise at least their first two rows go together.
     */
    private fun place(rows: List<Row>, keepWhole: Boolean = true) {
        val total = rows.height()
        val firstRows = rows.take(2).height()
        val fitsWhole = keepWhole && total <= capacity
        if ((fitsWhole && y + total > bottom) || (!fitsWhole && firstRows <= capacity && y + firstRows > bottom)) nextColumn()
        rows.forEach { row ->
            if (y + row.height > bottom) nextColumn()
            val x = margin + column * (columnWidth + gutter)
            row.parts.forEach { part -> pages.last().texts.add(PrintText(part.text, x + part.x, y + part.y, part.style, part.isSelectable)) }
            row.rules.forEach { rule -> pages.last().rules.add(rule.copy(x = x + rule.x, y = y + rule.y)) }
            row.diagrams.forEach { diagram -> pages.last().diagrams.add(diagram.copy(x = x + diagram.x, y = y + diagram.y)) }
            y += row.height
        }
    }

    /** The setlist's title, its date and description, and one entry per song in its running order. */
    private fun overview(source: PrintSource) {
        place(printRows.wrapped(source.title, printRows.titleStyle))
        if (options.showMetadata) {
            source.date?.let { place(printRows.wrapped(it)) }
            if (source.description.isNotBlank()) place(printRows.wrapped(source.description))
        }
        space(options.fontSize.toFloat())
        source.songs.forEach { entry ->
            val detail = entry.song?.metadata?.key?.takeIf { options.showKey }?.let { " (${labels.key}: $it)" }.orEmpty()
            val missing = if (entry.song == null) " [${labels.missing}]" else ""
            val title = printRows.wrapped("${entry.index}. ${entry.title}$detail$missing", printRows.chordStyle)
            val artist = if (options.showMetadata && !entry.artist.isNullOrBlank()) {
                printRows.wrapped(entry.artist, printRows.overviewArtistStyle)
            } else {
                emptyList()
            }
            place(title + artist)
            space(options.fontSize / 2f)
        }
    }

    /** One song sheet: its heading, how it is played, its chord diagrams and its scales, then its blocks. */
    private suspend fun song(entry: PrintSong) {
        val title = entry.index?.let { "$it. ${entry.title}" } ?: entry.title
        val heading = printRows.wrapped(title, printRows.titleStyle).toMutableList()
        if (options.showMetadata) entry.artist?.takeIf { it.isNotBlank() }?.let { heading += printRows.wrapped(it) }
        entry.song?.let { playingDetails(entry, it).takeIf(String::isNotBlank)?.let { heading += printRows.wrapped(it, printRows.detailStyle) } }
        // The heading travels with the first segment that prints anything (a missing song's notice standing in for it),
        // or with the chord diagrams and scales where the song has them: that segment whole where heading and segment fit
        // a column together, otherwise its first two rows, after which it flows on from under its heading. A break before
        // the first segment is ignored, since the heading has just started the song where it is. The blocks are grouped
        // as the screen groups them, which joins a change of tempo or time over exactly where it is not shown.
        val groups = entry.song?.blocks.orEmpty().withNumberedSections(labels.sections).joinCutSections(joinsTimings = !options.showTempo)
        val chordRows = entry.song?.let { chordDiagramRows(entry.chords).withSpaceBetween(scaleRows(entry.scales)) }.orEmpty()
        var firstGroup = -1
        var firstSegment = -1
        var firstBlockRows = if (entry.song == null) printRows.wrapped(labels.missing) else emptyList()
        for ((index, group) in groups.withIndex()) {
            if (group.isBreak()) continue
            val segments = printRows.groupSegments(group, columnWidth)
            firstSegment = segments.indexOfFirst { it.isNotEmpty() }
            if (firstSegment >= 0) {
                firstGroup = index
                firstBlockRows = segments[firstSegment]
                break
            }
        }
        val firstRows = chordRows.ifEmpty { firstBlockRows }
        val headingHeight = heading.height()
        val keepFirstWhole = headingHeight + headingGap + firstRows.height() <= capacity
        val together = headingHeight + headingGap + if (keepFirstWhole) firstRows.height() else firstRows.take(2).height()
        if (firstRows.isNotEmpty() && together <= capacity && y + together > bottom) nextColumn()
        place(heading)
        space(headingGap)
        if (chordRows.isNotEmpty()) {
            place(chordRows, keepWhole = keepFirstWhole)
            space(options.fontSize * 0.65f)
        }
        if (entry.song == null) {
            place(firstRows, keepWhole = keepFirstWhole)
        } else if (firstGroup >= 0) {
            var timingRows = emptyList<Row>()
            groups.forEachIndexed { index, group ->
                if (++placedGroups % 50 == 0) yield()
                when {
                    index < firstGroup -> Unit
                    group.isBreak() -> if (y > margin) nextColumn()
                    // A change of tempo or time is kept with what it is written above, the way a heading is; one with
                    // nothing printed after it says nothing and is left out.
                    index > firstGroup && group.singleOrNull() is ChordProBlock.Timing -> timingRows = printRows.groupSegments(group, columnWidth).single()
                    else -> {
                        val segments = printRows.groupSegments(group, columnWidth).let { if (index == firstGroup) it.drop(firstSegment) else it }
                        var hasPlaced = false
                        segments.forEachIndexed { segmentIndex, rows ->
                            // A break inside a section starts a new column whether or not anything after it prints.
                            if (segmentIndex > 0 && y > margin) nextColumn()
                            if (index == firstGroup && segmentIndex == 0) {
                                if (chordRows.isEmpty()) place(rows, keepWhole = keepFirstWhole) else place(rows)
                                hasPlaced = true
                            } else if (rows.isNotEmpty()) {
                                place(timingRows + rows)
                                timingRows = emptyList()
                                hasPlaced = true
                            }
                        }
                        if (hasPlaced) space(options.fontSize * 0.65f)
                    }
                }
            }
        }
        space(options.fontSize.toFloat())
    }

    /**
     * The song's chord diagrams as the song details screen's Chords section has them, in rows of cells each holding the
     * chord's name over its diagram, sized by the text so that they grow with it, a row being one unit [place] keeps
     * together, under the line naming the instrument and the tuning ([PrintLabels.chordTuning]), which is not selectable
     * for the reason the names are not: the importer would read it back as a line of the song. Only with the chords, since
     * the diagrams finger chords the page would otherwise not print.
     */
    private fun chordDiagramRows(chords: List<PrintChord>): List<Row> {
        if (!options.showChords || !options.showChordDiagrams || chords.isEmpty()) return emptyList()
        val gap = options.fontSize * DIAGRAM_GAP
        val cells = chords.map { chord ->
            val isFretted = chord.geometry is ChordDiagramGeometry.Fretted
            val diagramWidth = options.fontSize * if (isFretted) FRETTED_DIAGRAM_WIDTH else KEYBOARD_DIAGRAM_WIDTH
            val diagramHeight = options.fontSize * if (isFretted) FRETTED_DIAGRAM_HEIGHT else KEYBOARD_DIAGRAM_HEIGHT
            val nameLines = diagramNameLines(chord)
            val nameEnd = nameLines.maxOf { line -> line.maxOf { it.x + printRows.measure(it.text, it.style) } }
            DiagramCell(chord, diagramWidth, diagramHeight, maxOf(diagramWidth, nameEnd).coerceAtMost(columnWidth), nameLines)
        }
        val starts = balancedRowStarts(FloatArray(cells.size) { cells[it].width }, gap, columnWidth)
        val rows = starts.mapIndexed { index, start -> cells.subList(start, starts.getOrNull(index + 1) ?: cells.size) }
        return rows.mapIndexed { index, row ->
            var x = 0f
            val parts = mutableListOf<Part>()
            val diagrams = mutableListOf<PrintDiagram>()
            // The diagrams of a row stand on one line, under the name of the cell that takes the most lines.
            val rowNameHeight = row.maxOf { cell -> cell.nameLines.sumOf { line -> line.lineHeight().toDouble() }.toFloat() }
            row.forEach { cell ->
                var lineTop = 0f
                cell.nameLines.forEach { line ->
                    line.forEach { run -> parts += Part(run.text, x = x + run.x, y = lineTop, style = run.style, isSelectable = false) }
                    lineTop += line.lineHeight()
                }
                diagrams += PrintDiagram(cell.chord.geometry, x = x + (cell.width - cell.diagramWidth) / 2, y = rowNameHeight, width = cell.diagramWidth, height = cell.diagramHeight)
                x += cell.width + gap
            }
            val height = rowNameHeight + row.maxOf { it.diagramHeight } + if (index < rows.lastIndex) gap else 0f
            Row(parts, height, diagrams = diagrams)
        }.let { diagramRows -> labels.chordTuning?.let { printRows.wrapped(it, printRows.detailStyle, isSelectable = false) + diagramRows } ?: diagramRows }
    }

    /**
     * The song's suggested scales as its Scales section has them: under the heading, the home's line first, then a line
     * for each other part and chord worth a scale of its own, titled by it. None of it is selectable, for the reason the
     * diagrams' names are not: the importer would read the lines back as the song's own. Only with the chords, which the
     * scales are read off.
     */
    private fun scaleRows(scales: List<PrintScaleRow>): List<Row> {
        if (!options.showChords || !options.showScales || scales.isEmpty()) return emptyList()
        val heading = labels.scales?.let { printRows.wrapped(it, printRows.labelStyle, isSelectable = false) }.orEmpty()
        return heading + scales.flatMap { row ->
            printRows.wrapped(row.title?.let { "$it: ${row.text}" } ?: row.text, printRows.detailStyle, isSelectable = false)
        }
    }

    /** [this] and [other] one after the other, a diagrams' gap between them where both have rows. */
    private fun List<Row>.withSpaceBetween(other: List<Row>) = when {
        isEmpty() -> other
        other.isEmpty() -> this
        else -> this + Row(emptyList(), options.fontSize * DIAGRAM_GAP) + other
    }

    /**
     * The lines a diagram's name is printed on, each inside the column like every other text of the page: the name with
     * its second name after it where the two fit side by side, the second name on a line of its own under the name where
     * they do not, and either broken across lines where it is wider than the column on its own.
     */
    private fun diagramNameLines(chord: PrintChord): List<List<DiagramNamePart>> {
        val nameWidth = printRows.measure(chord.name, printRows.chordStyle)
        val secondary = chord.letterName
        if (nameWidth <= columnWidth && (secondary == null || nameWidth + printRows.measure(" $secondary", printRows.detailStyle) <= columnWidth)) {
            return listOf(
                listOfNotNull(
                    DiagramNamePart(chord.name, 0f, printRows.chordStyle),
                    secondary?.let { DiagramNamePart(" $it", nameWidth, printRows.detailStyle) },
                ),
            )
        }
        return wrappedNameLines(chord.name, printRows.chordStyle) + secondary?.let { wrappedNameLines(it, printRows.detailStyle) }.orEmpty()
    }

    private fun wrappedNameLines(text: String, style: PrintStyle) =
        wrapPrintText(text, columnWidth) { printRows.measure(it, style) }.map { listOf(DiagramNamePart(it, 0f, style)) }

    /**
     * How [song] is played, in the key it is printed in: the key, the transposition that took it there and the capo
     * where there is one, then the tempo and the time signature — each only where the song names it, since a value the
     * file never gave would come back from an import as the song's own. The transposition is named rather than undone,
     * so that a band reading from the page knows the chords are not the ones the song was written in.
     */
    private fun playingDetails(entry: PrintSong, song: ChordProSong): String = listOfNotNull(
        song.metadata.key?.takeIf { options.showKey && it.isNotBlank() }?.let { "${labels.key}: $it" },
        entry.transposition.takeIf { options.showKey && it != 0 }?.let { "${labels.transposition}: ${if (it > 0) "+$it" else it}" },
        song.metadata.capo?.takeIf { options.showKey && it != 0 }?.let { "${labels.capo}: $it" },
    ).plus(printRows.tempoDetails(song.metadata.tempo, song.metadata.time)).joinToString(METADATA_SEPARATOR)

    /**
     * The page number sits centred in the band the layout keeps free above the bottom margin (a 9 pt line is about 11 pt
     * tall), so it never reaches into the margin the user chose, where printers may not print.
     */
    private fun finish(): PrintDocument {
        val nonempty = pages.filter { it.texts.isNotEmpty() }
        val style = PrintStyle(size = 9, gray = 90)
        return PrintDocument(
            width = width,
            height = height,
            pages = nonempty.mapIndexed { index, page ->
                val number = "${index + 1} / ${nonempty.size}"
                val texts = if (options.showPageNumbers) {
                    page.texts + PrintText(number, (width - printRows.measure(number, style)) / 2, height - margin - 11f, style)
                } else {
                    page.texts
                }
                PrintPage(texts, page.rules, page.diagrams)
            },
        )
    }
}

/** One chord of a row of diagrams, [width] wide: its diagram's, or its name's where that is wider. */
private data class DiagramCell(
    val chord: PrintChord,
    val diagramWidth: Float,
    val diagramHeight: Float,
    val width: Float,
    val nameLines: List<List<DiagramNamePart>>,
)

/** A run of a diagram's name line, [x] from the cell's start. */
private data class DiagramNamePart(
    val text: String,
    val x: Float,
    val style: PrintStyle,
)

/** As tall as the largest style of its runs, with the line spacing the rest of the page's names have. */
private fun List<DiagramNamePart>.lineHeight() = maxOf { it.style.size } * 1.45f

/** A page while it is being filled. */
private class PageContent {
    val texts = mutableListOf<PrintText>()
    val rules = mutableListOf<PrintRule>()
    val diagrams = mutableListOf<PrintDiagram>()
}

private fun List<Row>.height() = sumOf { it.height.toDouble() }.toFloat()

/** Whether a group of `joinCutSections` is a break of its own, which the layout makes rather than prints. */
private fun List<ChordProBlock>.isBreak() = singleOrNull() == ChordProBlock.Break

// The sizes of a diagram and the gap between two, in multiples of the text size: at 12 pt a guitar diagram is about the
// one the song details screen draws at its own text size, and the cells keep that proportion as the text grows.
private const val FRETTED_DIAGRAM_WIDTH = 3.5f
private const val FRETTED_DIAGRAM_HEIGHT = 4.4f
private const val KEYBOARD_DIAGRAM_WIDTH = 4.75f
private const val KEYBOARD_DIAGRAM_HEIGHT = 2.5f
private const val DIAGRAM_GAP = 0.75f
