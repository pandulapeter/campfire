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

import com.pandulapeter.campfire.presentation.ui.notation.AbcTune
import com.pandulapeter.campfire.presentation.ui.notation.NotationDrawing
import com.pandulapeter.campfire.presentation.ui.notation.groupStartOffsetsOf
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
 * [this] with what the app's own feature switches take away left out too: the chords, the tablature, the sheet music, their diagrams, their
 * scales, and the key with the Chords switch, the scales with the Scale suggestions one too, the tempo and the time signature with
 * the Metronome one. The options themselves are kept as they
 * were chosen, since a switch only hides, and switched back on the export prints them again.
 */
internal fun PrintSettings.withinFeatures(areChordsEnabled: Boolean, areScaleSuggestionsEnabled: Boolean, isMetronomeEnabled: Boolean) = copy(
    showChords = showChords && areChordsEnabled,
    showTabs = showTabs && areChordsEnabled,
    showSheetMusic = showSheetMusic && areChordsEnabled,
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
    notations: Map<String, Map<AbcTune, NotationDrawing>> = emptyMap(),
): PrintDocument = PrintLayouter(
    options = settings.normalized(),
    labels = labels,
    measureText = measureText,
    notations = notations,
).layout(source)

/** How wide a column of the song sheets is, in PDF points: what an engraved tune is rendered for. */
internal fun printColumnWidth(settings: PrintSettings): Float = settings.normalized().let { options ->
    val width = if (options.isLandscape) options.paper.height else options.paper.width
    (width - 2 * options.marginPoints - COLUMN_GUTTER * (options.columns - 1)) / options.columns
}

/**
 * The PDF points one SVG unit of an engraved tune is printed at where its column allows: its lyrics, set at abc2svg's
 * 13 units, then come out as large as the song's own.
 */
internal fun printNotationUnit(settings: PrintSettings) = settings.fontSize / ABC_LYRICS_UNITS

private val PrintSettings.marginPoints get() = marginMm * 72f / 25.4f
private const val COLUMN_GUTTER = 18f
private const val ABC_LYRICS_UNITS = 13f

/**
 * One layout run: the geometry and the styles the options give, and the cursor ([column] and [y] on the last page) that
 * [place] moves on. The rows of a block are built first and placed whole, so that whether they fit is decided once.
 */
private class PrintLayouter(
    private val options: PrintSettings,
    private val labels: PrintLabels,
    measureText: (text: String, style: PrintStyle) -> Float,
    private val notations: Map<String, Map<AbcTune, NotationDrawing>>,
) {
    private val width = if (options.isLandscape) options.paper.height else options.paper.width
    private val height = if (options.isLandscape) options.paper.width else options.paper.height
    private val margin = options.marginPoints
    private val gutter = COLUMN_GUTTER

    /** The columns being filled: the song sheets' by default, the running order's while it is laid out. */
    private var columns = options.columns
    private var columnWidth = columnWidthFor(columns)

    /** Where a column after the first starts on the current page: below a heading the running order spans the page with. */
    private var columnTop = 0f
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
        // Only a setlist has a running order to print instead of its song sheets; any other source always prints them.
        val hasSongSheets = !source.hasRunningOrder || options.includeSongSheets
        if (source.hasRunningOrder && options.includeSetlistOverview) {
            overview(source)
            // Song sheets follow the running order on a fresh page, including in the compact layout.
            if (hasSongSheets) newPage()
        }
        if (hasSongSheets) source.songs.forEachIndexed { index, entry ->
            yield()
            if (index > 0 && options.startSongsOnNewPage) newPage()
            song(entry)
        }
        return finish()
    }

    private fun columnWidthFor(count: Int) = (width - 2 * margin - gutter * (count - 1)) / count

    private fun page() {
        pages.add(PageContent())
        column = 0
        y = margin
        columnTop = margin
    }

    private fun nextColumn() {
        if (column + 1 < columns) {
            column++
            y = columnTop
        } else {
            page()
        }
    }

    private fun newPage() {
        if (pages.isNotEmpty() && pages.last().hasContent) page()
    }

    private fun space(amount: Float) {
        y = (y + amount).coerceAtMost(bottom)
    }

    /**
     * Puts [rows] under the cursor: [keepWhole] rows that fit a column start a new one rather than being split, and
     * otherwise at least their first two rows go together. Rows holding sheet music always flow on from where the cursor
     * is, a staff system to a row: a tune runs to many systems, and moving it whole would leave most of a page blank.
     */
    private fun place(rows: List<Row>, keepWhole: Boolean = true) {
        val total = rows.height()
        val firstRows = rows.take(2).height()
        val fitsWhole = keepWhole && !rows.holdsSheetMusic() && total <= capacity
        if ((fitsWhole && y + total > bottom) || (!fitsWhole && firstRows <= capacity && y + firstRows > bottom)) nextColumn()
        rows.forEach { row ->
            if (y + row.height > bottom) nextColumn()
            val x = margin + column * (columnWidth + gutter)
            row.parts.forEach { part -> pages.last().texts.add(PrintText(part.text, x + part.x, y + part.y, part.style, part.isSelectable, part.formatting)) }
            row.rules.forEach { rule -> pages.last().rules.add(rule.copy(x = x + rule.x, y = y + rule.y)) }
            row.diagrams.forEach { diagram -> pages.last().diagrams.add(diagram.copy(x = x + diagram.x, y = y + diagram.y)) }
            row.notations.forEach { notation -> pages.last().notations.add(notation.copy(x = x + notation.x, y = y + notation.y)) }
            y += row.height
        }
    }

    /**
     * The setlist's title, its date and description, across the page, then one entry per song in its running order, in
     * the running order's own columns ([PrintSettings.overviewColumns]), which start under the heading on its page. The
     * date, the description, and the songs' artists follow [PrintSettings.showOverviewDetails], not the song sheets'
     * artist option.
     */
    private fun overview(source: PrintSource) {
        columns = 1
        columnWidth = columnWidthFor(columns)
        place(printRows.wrapped(source.title, printRows.titleStyle, columnWidth))
        if (options.showOverviewDetails) {
            source.date?.let { place(printRows.wrapped(it, width = columnWidth)) }
            if (source.description.isNotBlank()) place(printRows.wrapped(source.description, width = columnWidth))
        }
        space(options.fontSize.toFloat())
        columns = options.overviewColumns
        columnWidth = columnWidthFor(columns)
        columnTop = y
        source.songs.forEach { entry ->
            val detail = entry.song?.metadata?.key?.takeIf { options.showKey }?.let { " (${labels.key}: $it)" }.orEmpty()
            val missing = if (entry.song == null) " [${labels.missing}]" else ""
            val title = printRows.wrapped("${entry.index}. ${entry.title}$detail$missing", printRows.chordStyle, columnWidth)
            val artist = if (options.showOverviewDetails && !entry.artist.isNullOrBlank()) {
                printRows.wrapped(entry.artist, printRows.overviewArtistStyle, columnWidth)
            } else {
                emptyList()
            }
            place(title + artist)
            space(options.fontSize / 2f)
        }
        columns = options.columns
        columnWidth = columnWidthFor(columns)
    }

    /** One song sheet: its heading, how it is played, its chord diagrams and its scales, then its blocks. */
    private suspend fun song(entry: PrintSong) {
        printRows.notations = notations[entry.fileName].orEmpty()
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
        // Worked out once rather than kept while placing, since the groups up to the first that prints are asked for twice.
        val startOffsets = groupStartOffsetsOf(groups)
        val chordRows = entry.song?.let { chordDiagramRows(entry.chords).withSpaceBetween(scaleRows(entry.scales)) }.orEmpty()
        var firstGroup = -1
        var firstSegment = -1
        var firstBlockRows = if (entry.song == null) printRows.wrapped(labels.missing) else emptyList()
        for ((index, group) in groups.withIndex()) {
            if (group.isBreak()) continue
            val segments = printRows.groupSegments(group, columnWidth, startOffsets[index])
            firstSegment = segments.indexOfFirst { it.isNotEmpty() }
            if (firstSegment >= 0) {
                firstGroup = index
                firstBlockRows = segments[firstSegment]
                break
            }
        }
        val firstRows = chordRows.ifEmpty { firstBlockRows }
        val headingHeight = heading.height()
        val keepFirstWhole = !firstRows.holdsSheetMusic() && headingHeight + headingGap + firstRows.height() <= capacity
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
                    index > firstGroup && group.singleOrNull() is ChordProBlock.Timing -> timingRows = printRows.groupSegments(group, columnWidth, startOffsets[index]).single()
                    else -> {
                        val segments = printRows.groupSegments(group, columnWidth, startOffsets[index]).let { if (index == firstGroup) it.drop(firstSegment) else it }
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
     * chord's name, under its second name where it has one, over its diagram, sized by the text so that they grow with
     * it, a row being one unit [place] keeps together, under the line naming the instrument and the tuning
     * ([PrintLabels.chordTuning]), which is not selectable for the reason the names are not: the importer would read it
     * back as a line of the song. As on the screen, where the chords are in more than one key, each key's chords
     * ([PrintChord.keyName]) start a row of their own under a line naming the key, set as a label rather than as a chord,
     * part of that row so that it is never left at the foot of a column.
     */
    private fun chordDiagramRows(chords: List<PrintChord>): List<Row> {
        if (!options.showChordDiagrams || chords.isEmpty()) return emptyList()
        val gap = options.fontSize * DIAGRAM_GAP
        val cells = chords.map { chord ->
            val isFretted = chord.geometry is ChordDiagramGeometry.Fretted
            val diagramWidth = options.fontSize * if (isFretted) FRETTED_DIAGRAM_WIDTH else KEYBOARD_DIAGRAM_WIDTH
            val diagramHeight = options.fontSize * if (isFretted) FRETTED_DIAGRAM_HEIGHT else KEYBOARD_DIAGRAM_HEIGHT
            val secondLines = (chord.letterName ?: chord.degree)?.let { wrappedNameLines(it, printRows.detailStyle) }.orEmpty()
            val nameLines = wrappedNameLines(chord.name, printRows.chordStyle)
            val nameEnd = (secondLines + nameLines).maxOf { line -> line.maxOf { printRows.measure(it.text, it.style) } }
            DiagramCell(chord, diagramWidth, diagramHeight, maxOf(diagramWidth, nameEnd).coerceAtMost(columnWidth), secondLines, nameLines)
        }
        // As on the screen, every name stands under the room the section keeps for the second names over them, so that a
        // row's names share a line whichever chords the key counts.
        val secondHeight = cells.maxOf { cell -> cell.secondLines.sumOf { line -> line.lineHeight().toDouble() }.toFloat() }
        val groupStarts = cells.indices.filter { it == 0 || cells[it].chord.keyName != cells[it - 1].chord.keyName }
        val starts = groupStarts.flatMapIndexed { group, start ->
            val end = groupStarts.getOrNull(group + 1) ?: cells.size
            balancedRowStarts(FloatArray(end - start) { cells[start + it].width }, gap, columnWidth).map { start + it }
        }
        val rows = starts.mapIndexed { index, start -> cells.subList(start, starts.getOrNull(index + 1) ?: cells.size) }
        return rows.mapIndexed { index, row ->
            var x = 0f
            val parts = mutableListOf<Part>()
            val diagrams = mutableListOf<PrintDiagram>()
            var headingHeight = 0f
            row.first().chord.keyName?.takeIf { starts[index] in groupStarts }?.let { key ->
                wrappedNameLines("${labels.key}: $key", printRows.labelStyle).forEach { line ->
                    line.forEach { run -> parts += Part(run.text, x = 0f, y = headingHeight, style = run.style, isSelectable = false) }
                    headingHeight += line.lineHeight()
                }
                headingHeight += gap
            }
            // The diagrams of a row stand on one line, under the name of the cell that takes the most lines.
            val rowNameHeight = headingHeight + secondHeight + row.maxOf { cell -> cell.nameLines.sumOf { line -> line.lineHeight().toDouble() }.toFloat() }
            row.forEach { cell ->
                var lineTop = headingHeight + secondHeight - cell.secondLines.sumOf { line -> line.lineHeight().toDouble() }.toFloat()
                (cell.secondLines + cell.nameLines).forEach { line ->
                    line.forEach { run ->
                        val runX = (cell.width - printRows.measure(run.text, run.style)).coerceAtLeast(0f) / 2
                        parts += Part(run.text, x = x + runX, y = lineTop, style = run.style, isSelectable = false)
                    }
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
     * diagrams' names are not: the importer would read the lines back as the song's own.
     */
    private fun scaleRows(scales: List<PrintScaleRow>): List<Row> {
        if (!options.showScales || scales.isEmpty()) return emptyList()
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

    /** [text] in [style] broken into the lines of a diagram's name, each inside the column like every other text of the page. */
    private fun wrappedNameLines(text: String, style: PrintStyle) =
        wrapPrintText(text, columnWidth) { printRows.measure(it, style) }.map { listOf(DiagramNamePart(it, style)) }

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
        val nonempty = pages.filter { it.hasContent }
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
                PrintPage(texts, page.rules, page.diagrams, page.notations)
            },
        )
    }
}

/**
 * One chord of a row of diagrams, [width] wide: its diagram's, or its names' where they are wider, its second name
 * ([PrintChord.letterName] or [PrintChord.degree]) on [secondLines] over the name's [nameLines].
 */
private data class DiagramCell(
    val chord: PrintChord,
    val diagramWidth: Float,
    val diagramHeight: Float,
    val width: Float,
    val secondLines: List<List<DiagramNamePart>>,
    val nameLines: List<List<DiagramNamePart>>,
)

/** A run of a diagram's name line, centered in its cell as the screen centers it. */
private data class DiagramNamePart(
    val text: String,
    val style: PrintStyle,
)

/** As tall as the largest style of its runs, with the line spacing the rest of the page's names have. */
private fun List<DiagramNamePart>.lineHeight() = maxOf { it.style.size } * 1.45f

/** A page while it is being filled. */
private class PageContent {
    val texts = mutableListOf<PrintText>()
    val rules = mutableListOf<PrintRule>()
    val diagrams = mutableListOf<PrintDiagram>()
    val notations = mutableListOf<PrintNotation>()

    /** Whether anything was put on the page: a staff system alone, a tune running on past a page, is something. */
    val hasContent get() = texts.isNotEmpty() || notations.isNotEmpty()
}

private fun List<Row>.height() = sumOf { it.height.toDouble() }.toFloat()

private fun List<Row>.holdsSheetMusic() = any { it.notations.isNotEmpty() }

/** Whether a group of `joinCutSections` is a break of its own, which the layout makes rather than prints. */
private fun List<ChordProBlock>.isBreak() = singleOrNull() == ChordProBlock.Break

// The sizes of a diagram and the gap between two, in multiples of the text size: at 12 pt a guitar diagram is about the
// one the song details screen draws at its own text size, and the cells keep that proportion as the text grows.
private const val FRETTED_DIAGRAM_WIDTH = 3.5f
private const val FRETTED_DIAGRAM_HEIGHT = 4.4f
private const val KEYBOARD_DIAGRAM_WIDTH = 4.75f
private const val KEYBOARD_DIAGRAM_HEIGHT = 2.5f
private const val DIAGRAM_GAP = 0.75f
