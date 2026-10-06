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

import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.ChordProTabWrapper
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.model.CommentPlacement
import com.pandulapeter.campfire.chordpro.model.CommentStyle
import com.pandulapeter.campfire.chordpro.model.GridToken
import com.pandulapeter.campfire.chordpro.model.SectionType
import com.pandulapeter.campfire.data.model.domain.PrintSettings
import com.pandulapeter.campfire.presentation.ui.chords.ChordDiagramGeometry
import com.pandulapeter.campfire.presentation.ui.chords.chordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.chords.emptyChordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.chords.secondaryName
import com.pandulapeter.campfire.presentation.ui.chords.selectShape
import com.pandulapeter.campfire.presentation.ui.chords.songChordsOf
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.DefaultSectionLabels
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.header
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.withNumber
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.withNumberedSections
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.alignedGridBars
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.areAll
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.labelOf
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.padLyricsToFitChords
import kotlinx.coroutines.yield
import kotlin.math.roundToInt

/**
 * What a PDF is made of, read once when the export screen opens: a snapshot, so that the preview and the file agree even
 * if the library changes underneath the screen. [date] is already formatted in the app's language.
 */
internal data class PrintSource(
    val title: String,
    val description: String = "",
    val date: String? = null,
    val isSetlist: Boolean = false,
    val songs: List<PrintSong>,
)

/**
 * One song of a [PrintSource], [index] being its one-based slot in a setlist, kept when songs are left out, and
 * [transposition] the semitones the viewer moves it by, which [song] is already rendered in.
 */
internal data class PrintSong(
    val fileName: String,
    val title: String,
    val artist: String?,
    val index: Int? = null,
    val transposition: Int = 0,
    /** Null for an unreadable or missing file; its place remains visible in the running order. */
    val song: ChordProSong?,
    /** The file as it is stored, which the export screen shows for a song exported as ChordPro, and null when it could not be read. */
    val text: String? = null,
    /**
     * Every chord the song plays, in the order it is first played, with the shape its Chords section draws it with on
     * the reader's instrument; empty where the chord diagrams are switched off in the app, whatever the export asks for.
     */
    val chords: List<PrintChord> = emptyList(),
)

/**
 * One chord diagram of a [PrintSong]: the chord as the page names it, the one it sounds as where that differs or else
 * its letters where the page counts it ([SongChord.secondaryName]), and its shape.
 */
internal data class PrintChord(
    val name: String,
    val secondaryName: String? = null,
    val geometry: ChordDiagramGeometry,
)

/**
 * The chords of [song], transposed as it is printed and still in the standard notation, named in [notation] and drawn
 * the way the song details screen's Chords section draws them
 * on [instrument]: the song's own definition, the player's shape from [storedShapes], or the app's own, and an empty
 * frame for a chord with none, so that the page and the screen finger every chord alike.
 */
internal fun printChordsOf(
    song: ChordProSong,
    notation: ChordNotation,
    instrument: ChordInstrument,
    storedShapes: Map<String, String>,
): List<PrintChord> = songChordsOf(song, notation, instrument, song.metadata.capo ?: 0).map { chord ->
    PrintChord(
        name = chord.name,
        secondaryName = chord.secondaryName,
        geometry = selectShape(chord, instrument, storedShapes).shape?.let { chordDiagramGeometryOf(it, instrument, chord.chord.root) }
            ?: emptyChordDiagramGeometryOf(instrument),
    )
}

/**
 * How a text is set: [size] in PDF points, [monospace] for the tablature and grids whose characters have to line up
 * (everything else is in the app's text font, as in the viewer), and [gray] from 0 (black) to 255 (white).
 */
internal data class PrintStyle(
    val size: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val monospace: Boolean = false,
    val gray: Int = 0,
)

/**
 * Coordinates and sizes are PDF points (1/72 inch), independent of screen density and accessibility text size.
 *
 * @property isSelectable False for the names over the chord diagrams, which are left out of the file's selectable text:
 *   read back by an import, a row of them would be a line of chords above the song's first line.
 */
internal data class PrintText(
    val text: String,
    val x: Float,
    val y: Float,
    val style: PrintStyle,
    val isSelectable: Boolean = true,
)

/** A filled rectangle, the way frames and bars are drawn; in PDF points like the texts, [gray] from 0 (black) to 255. */
internal data class PrintRule(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val gray: Int = 0,
)

/** A chord diagram drawn into the [width] by [height] box at [x], [y], in PDF points like the texts. */
internal data class PrintDiagram(
    val geometry: ChordDiagramGeometry,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

/** Everything on one page, in PDF points from its top left corner; the rules are drawn under the texts and the diagrams. */
internal data class PrintPage(
    val texts: List<PrintText>,
    val rules: List<PrintRule> = emptyList(),
    val diagrams: List<PrintDiagram> = emptyList(),
)

/** The pages of one export, all of one size, which is the paper's in PDF points turned for the orientation. */
internal data class PrintDocument(
    val width: Float,
    val height: Float,
    val pages: List<PrintPage>,
)

/**
 * The words the layout prints, resolved by the export screen in the app's language, since the layout runs off the composition
 * where no string resource can be read.
 */
internal data class PrintLabels(
    val key: String,
    val transposition: String,
    val capo: String,
    val tempo: String,
    /** A tempo's value as the app writes it, [TEMPO_VALUE] standing for the number: "96 BPM". */
    val tempoValue: String,
    val time: String,
    val missing: String,
    /** The headings of the sections the file leaves unnamed, the viewer's own. */
    val sections: DefaultSectionLabels,
)

/**
 * A single layout for the preview and export. Measuring is supplied by the same Compose text renderer that draws
 * both. A lyric/chord pair is indivisible; a section that fits in a column is kept together. Longer sections flow
 * through columns and pages without reducing the requested size or clipping the remainder.
 *
 * It suspends between songs and every few dozen blocks: on the web the layout shares the page's one thread, and only
 * while it is suspended can the page paint and a newer choice of options cancel a stale layout.
 */
/**
 * [this] with what the app's own feature switches take away left out too: the chords, their diagrams and the key with
 * the Chords switch, the tempo and the time signature with the Metronome one. The options themselves are kept as they
 * were chosen, since a switch only hides, and switched back on the export prints them again.
 */
internal fun PrintSettings.withinFeatures(areChordsEnabled: Boolean, isMetronomeEnabled: Boolean) = copy(
    showChords = showChords && areChordsEnabled,
    showChordDiagrams = showChordDiagrams && areChordsEnabled,
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
    private val measureText: (text: String, style: PrintStyle) -> Float,
) {
    private val width = if (options.isLandscape) options.paper.height else options.paper.width
    private val height = if (options.isLandscape) options.paper.width else options.paper.height
    private val margin = options.marginMm * 72f / 25.4f
    private val gutter = 18f
    private val columnWidth = (width - 2 * margin - gutter * (options.columns - 1)) / options.columns
    private val bottom = height - margin - if (options.showPageNumbers) 18f else 0f
    private val capacity = bottom - margin
    private val headingGap = options.fontSize * 0.8f

    // The page reads from the title down to the lyrics: the title large, the details and the section labels smaller and
    // gray, so that a label is never taken for a chord, and only tablature and grids in the monospace face.
    private val titleStyle = PrintStyle(size = (options.fontSize * 1.6f).roundToInt(), bold = true)
    private val lyricStyle = PrintStyle(size = options.fontSize)
    private val chordStyle = PrintStyle(size = options.fontSize, bold = true)
    private val annotationStyle = PrintStyle(size = options.fontSize, italic = true)
    private val commentStyle = PrintStyle(size = options.fontSize, italic = true, gray = 90)
    private val detailStyle = PrintStyle(size = options.fontSize - 1, gray = 90)
    private val labelStyle = PrintStyle(size = options.fontSize - 1, bold = true, gray = 90)
    private val overviewArtistStyle = PrintStyle(size = options.fontSize, gray = 90)
    private val tabStyle = PrintStyle(size = options.fontSize, monospace = true)
    private val gridStyle = PrintStyle(size = options.fontSize, bold = true, monospace = true)
    private val labelSpace = options.fontSize * 0.3f

    // Chord names, spaces and the other short strings are measured over and over; only they are kept, so that the
    // substrings of lyrics the wrapping tries are not held for the life of a large setlist.
    private val shortMeasurements = mutableMapOf<Pair<String, PrintStyle>, Float>()
    private val pages = mutableListOf<PageContent>()
    private var column = 0
    private var y = margin
    private var placedBlocks = 0

    suspend fun layout(source: PrintSource): PrintDocument {
        page()
        if (source.isSetlist && (options.includeSetlistOverview || options.setlistMode == PrintSettings.SetlistMode.RUNNING_ORDER)) {
            overview(source)
            if (options.setlistMode == PrintSettings.SetlistMode.RUNNING_ORDER) return finish()
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

    private fun measure(text: String, style: PrintStyle): Float =
        if (text.length <= 8) shortMeasurements.getOrPut(text to style) { measureText(text, style) } else measureText(text, style)

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
        place(wrapped(source.title, titleStyle))
        if (options.showMetadata) {
            source.date?.let { place(wrapped(it)) }
            if (source.description.isNotBlank()) place(wrapped(source.description))
        }
        space(options.fontSize.toFloat())
        source.songs.forEach { entry ->
            val detail = entry.song?.metadata?.key?.takeIf { options.showKey }?.let { " (${labels.key}: $it)" }.orEmpty()
            val missing = if (entry.song == null) " [${labels.missing}]" else ""
            val title = wrapped("${entry.index}. ${entry.title}$detail$missing", chordStyle)
            val artist = if (options.showMetadata && !entry.artist.isNullOrBlank()) {
                wrapped(entry.artist, overviewArtistStyle)
            } else {
                emptyList()
            }
            place(title + artist)
            space(options.fontSize / 2f)
        }
    }

    /** One song sheet: its heading, how it is played, its chord diagrams, then its blocks. */
    private suspend fun song(entry: PrintSong) {
        val title = entry.index?.let { "$it. ${entry.title}" } ?: entry.title
        val heading = wrapped(title, titleStyle).toMutableList()
        if (options.showMetadata) entry.artist?.takeIf { it.isNotBlank() }?.let { heading += wrapped(it) }
        entry.song?.let { playingDetails(entry, it).takeIf(String::isNotBlank)?.let { heading += wrapped(it, detailStyle) } }
        // The heading travels with the first block that prints anything (a missing song's notice standing in for it), or
        // with the chord diagrams where the song has them: that block whole where heading and block fit a column
        // together, otherwise its first two rows, after which the block flows on from under its heading. A break before
        // the first block is ignored, since the heading has just started the song where it is.
        val blocks = entry.song?.blocks.orEmpty().withNumberedSections(labels.sections)
        val diagramRows = entry.song?.let { chordDiagramRows(entry.chords) }.orEmpty()
        var firstIndex = -1
        var firstBlockRows = if (entry.song == null) wrapped(labels.missing) else emptyList()
        blocks.forEachIndexed { index, block ->
            if (firstIndex < 0 && block != ChordProBlock.Break) {
                blockRowsAt(blocks, index).takeIf { it.isNotEmpty() }?.let {
                    firstIndex = index
                    firstBlockRows = it
                }
            }
        }
        val firstRows = diagramRows.ifEmpty { firstBlockRows }
        val headingHeight = heading.height()
        val keepFirstWhole = headingHeight + headingGap + firstRows.height() <= capacity
        val together = headingHeight + headingGap + if (keepFirstWhole) firstRows.height() else firstRows.take(2).height()
        if (firstRows.isNotEmpty() && together <= capacity && y + together > bottom) nextColumn()
        place(heading)
        space(headingGap)
        if (diagramRows.isNotEmpty()) {
            place(diagramRows, keepWhole = keepFirstWhole)
            space(options.fontSize * 0.65f)
        }
        if (entry.song == null) {
            place(firstRows, keepWhole = keepFirstWhole)
        } else if (firstIndex >= 0) {
            var timingRows = emptyList<Row>()
            blocks.forEachIndexed { index, block ->
                if (++placedBlocks % 50 == 0) yield()
                when {
                    index < firstIndex -> Unit
                    index == firstIndex -> {
                        if (diagramRows.isEmpty()) place(firstBlockRows, keepWhole = keepFirstWhole) else place(firstBlockRows)
                        spaceAfter(blocks, index)
                    }
                    block == ChordProBlock.Break -> if (y > margin) nextColumn()
                    // A change of tempo or time is kept with what it is written above, the way a heading is; one with
                    // nothing printed after it says nothing and is left out.
                    block is ChordProBlock.Timing -> timingRows = blockRowsAt(blocks, index)
                    else -> {
                        val rows = blockRowsAt(blocks, index)
                        if (rows.isNotEmpty()) {
                            place(timingRows + rows)
                            timingRows = emptyList()
                            spaceAfter(blocks, index)
                        }
                    }
                }
            }
        }
        space(options.fontSize.toFloat())
    }

    /**
     * The song's chord diagrams as the song details screen's Chords section has them, in rows of cells each holding the
     * chord's name over its diagram, sized by the text so that they grow with it, a row being one unit [place] keeps
     * together. Only with the chords, since the diagrams finger chords the page would otherwise not print.
     */
    private fun chordDiagramRows(chords: List<PrintChord>): List<Row> {
        if (!options.showChords || !options.showChordDiagrams || chords.isEmpty()) return emptyList()
        val gap = options.fontSize * DIAGRAM_GAP
        val cells = chords.map { chord ->
            val isFretted = chord.geometry is ChordDiagramGeometry.Fretted
            val diagramWidth = options.fontSize * if (isFretted) FRETTED_DIAGRAM_WIDTH else KEYBOARD_DIAGRAM_WIDTH
            val diagramHeight = options.fontSize * if (isFretted) FRETTED_DIAGRAM_HEIGHT else KEYBOARD_DIAGRAM_HEIGHT
            val nameLines = diagramNameLines(chord)
            val nameEnd = nameLines.maxOf { line -> line.maxOf { it.x + measure(it.text, it.style) } }
            DiagramCell(chord, diagramWidth, diagramHeight, maxOf(diagramWidth, nameEnd).coerceAtMost(columnWidth), nameLines)
        }
        val rows = mutableListOf<List<DiagramCell>>()
        var rowWidth = 0f
        cells.forEach { cell ->
            if (rows.isEmpty() || rowWidth + gap + cell.width > columnWidth) {
                rows += listOf(cell)
                rowWidth = cell.width
            } else {
                rows[rows.lastIndex] = rows.last() + cell
                rowWidth += gap + cell.width
            }
        }
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
        }
    }

    /**
     * The lines a diagram's name is printed on, each inside the column like every other text of the page: the name with
     * its second name after it where the two fit side by side, the second name on a line of its own under the name where
     * they do not, and either broken across lines where it is wider than the column on its own.
     */
    private fun diagramNameLines(chord: PrintChord): List<List<DiagramNamePart>> {
        val nameWidth = measure(chord.name, chordStyle)
        val secondary = chord.secondaryName
        if (nameWidth <= columnWidth && (secondary == null || nameWidth + measure(" $secondary", detailStyle) <= columnWidth)) {
            return listOf(
                listOfNotNull(
                    DiagramNamePart(chord.name, 0f, chordStyle),
                    secondary?.let { DiagramNamePart(" $it", nameWidth, detailStyle) },
                ),
            )
        }
        return wrappedNameLines(chord.name, chordStyle) + secondary?.let { wrappedNameLines(it, detailStyle) }.orEmpty()
    }

    private fun wrappedNameLines(text: String, style: PrintStyle) =
        wrapPrintText(text, columnWidth) { measure(it, style) }.map { listOf(DiagramNamePart(it, 0f, style)) }

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
    ).plus(tempoDetails(song.metadata.tempo, song.metadata.time)).joinToString(METADATA_SEPARATOR)

    private fun tempoDetails(tempo: String?, time: String?) = if (options.showTempo) {
        listOfNotNull(
            tempo?.takeIf { it.isNotBlank() }?.let { "${labels.tempo}: ${if (it.all(Char::isDigit)) labels.tempoValue.replace(TEMPO_VALUE, it) else it}" },
            time?.takeIf { it.isNotBlank() }?.let { "${labels.time}: $it" },
        )
    } else {
        emptyList()
    }

    /** Between two blocks of one chorus the gap carries the chorus's bar, so that a comment does not cut it in two. */
    private fun blockRowsAt(blocks: List<ChordProBlock>, index: Int): List<Row> {
        val rows = blockRows(blocks[index], columnWidth, isInChorus = blocks.isInChorus(index))
        return if (rows.isNotEmpty() && blocks.continuesChorusAfter(index)) {
            val gap = options.fontSize * 0.65f
            rows + Row(emptyList(), gap, listOf(PrintRule(x = 0f, y = 0f, width = CHORUS_BAR_WIDTH, height = gap)))
        } else {
            rows
        }
    }

    private fun spaceAfter(blocks: List<ChordProBlock>, index: Int) {
        if (!blocks.continuesChorusAfter(index)) space(options.fontSize * 0.65f)
    }

    private fun blockRows(
        block: ChordProBlock,
        width: Float,
        labelOverride: String? = null,
        isInChorus: Boolean = false,
    ): List<Row> = when (block) {
        is ChordProBlock.Section -> sectionRows(block, width, labelOverride)
        is ChordProBlock.Comment -> commentRows(block, width, isInChorus)
        is ChordProBlock.ChorusRecall -> recallRows(block, width)
        is ChordProBlock.Transpose -> keyChangeRows(block, width)
        is ChordProBlock.Timing -> timingRows(block, width)
        ChordProBlock.Break -> emptyList()
    }

    private fun sectionRows(section: ChordProBlock.Section, width: Float, labelOverride: String?): List<Row> {
        val isChorus = section.type == SectionType.Chorus
        val lineWidth = if (isChorus) width - CHORUS_INDENT else width
        val sectionLabel = section.label ?: when (val type = section.type) {
            SectionType.Verse -> labels.sections.verse
            SectionType.Chorus -> labels.sections.chorus
            SectionType.Bridge -> labels.sections.bridge
            is SectionType.Custom -> labels.sections.labelOf(type)
            // A paragraph of nothing but tablature or a grid is a bare environment, which names itself, as on screen; one of
            // lyrics is headed on screen by its fold toggle alone, which a page has no use for.
            SectionType.Paragraph -> when {
                section.lines.areAll<ChordProLine.Tab>() -> labels.sections.tab
                section.lines.areAll<ChordProLine.Grid>() -> labels.sections.grid
                else -> null
            }
        }?.withNumber(section.number)
        val lines = section.lines.flatMapIndexed { lineIndex, line ->
            when (line) {
                is ChordProLine.Lyrics -> lyricsRows(line, lineWidth)
                is ChordProLine.Tab -> {
                    val startsRun = lineIndex == 0 || section.lines[lineIndex - 1] !is ChordProLine.Tab || !line.continuesEnvironment
                    if (options.showChords && startsRun) tabRows(section.lines, lineIndex, sectionLabel, lineWidth) else emptyList()
                }
                is ChordProLine.Grid -> {
                    val previous = section.lines.getOrNull(lineIndex - 1)
                    val startsRun = previous !is ChordProLine.Grid || previous.label != line.label
                    if (options.showChords && startsRun) gridRows(section.lines, lineIndex, lineWidth) else emptyList()
                }
                ChordProLine.Blank -> listOf(Row(emptyList(), options.fontSize * 0.7f))
            }
        }
        // A section whose every line the options hide leaves out its label too, rather than printing a heading with
        // nothing under it; one written with no lines at all keeps it, since there the label is the cue.
        if (section.lines.isNotEmpty() && lines.none { row -> row.parts.any { !it.text.isPrintBlank() } }) return emptyList()
        val labelRows = (labelOverride ?: sectionLabel?.takeUnless { section.isContinuation })?.let { labelRows(it, lineWidth) }.orEmpty()
        return if (isChorus) chorusBar(labelRows + lines, startsWithLabel = labelRows.isNotEmpty()) else labelRows + lines
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

    /** The run of tab lines that starts at [lineIndex], wrapped into systems of the column's width. */
    private fun tabRows(lines: List<ChordProLine>, lineIndex: Int, sectionLabel: String?, lineWidth: Float): List<Row> {
        val line = lines[lineIndex] as ChordProLine.Tab
        val run = lines.drop(lineIndex)
            .takeWhile { it is ChordProLine.Tab && (it === line || it.continuesEnvironment) }
            .map { (it as ChordProLine.Tab).text }
        return buildList {
            line.label?.takeUnless { it == sectionLabel }?.let { addAll(labelRows(it, lineWidth)) }
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
     * The run of grid lines starting at [lineIndex], aligned into columns as the viewer aligns them, each line broken
     * between bars as the viewer breaks it, a row taking as many whole bars as fit; only a bar wider than the column is
     * wrapped inside itself.
     */
    private fun gridRows(lines: List<ChordProLine>, lineIndex: Int, lineWidth: Float): List<Row> {
        val first = lines[lineIndex] as ChordProLine.Grid
        val run = lines.subList(lineIndex, lines.size)
            .takeWhile { it is ChordProLine.Grid && it.label == first.label }
            .map { (it as ChordProLine.Grid).tokens }
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

    private fun commentRows(comment: ChordProBlock.Comment, width: Float, isInChorus: Boolean): List<Row> = when {
        !options.showComments || (comment.isInTabOrGrid && !options.showChords) -> emptyList()
        isInChorus -> chorusBar(commentRows(comment, width - CHORUS_INDENT, isInChorus = false), startsWithLabel = false)
        comment.style == CommentStyle.BOX -> boxedRows(comment.text, width)
        else -> wrapped(comment.text, commentStyle, width)
    }

    /**
     * As in the viewer, the recall's own heading goes on the first recalled piece that prints anything, and is printed on
     * its own when nothing is: a recall says where the chorus is sung, even with nothing under it.
     */
    private fun recallRows(recall: ChordProBlock.ChorusRecall, width: Float): List<Row> {
        val recalled = recall.blocks.firstOrNull { it is ChordProBlock.Section } as? ChordProBlock.Section
        var header: String? = recall.label ?: recalled?.header(labels.sections) ?: labels.sections.chorus
        return recall.blocks.flatMapIndexed { index, piece ->
            val isPieceInChorus = recall.blocks.isInChorus(index)
            if (piece is ChordProBlock.Section && header != null) {
                blockRows(piece, width, labelOverride = header).also { if (it.isNotEmpty()) header = null }
            } else {
                blockRows(piece, width, isInChorus = isPieceInChorus)
            }
        }.ifEmpty { header?.let { labelRows(it, width) }.orEmpty() }
    }

    /**
     * A line break inside a text (a setlist description has up to three lines) starts a row of its own, since a row is
     * one line tall and the renderer would draw the rest over whatever comes under it.
     */
    private fun wrapped(
        text: String,
        style: PrintStyle = lyricStyle,
        width: Float = columnWidth,
    ): List<Row> = text.lines().flatMap { line ->
        wrapPrintText(line, width) { measure(it, style) }.map { Row(listOf(Part(it, style = style)), style.size * 1.45f) }
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
                    page.texts + PrintText(number, (width - measure(number, style)) / 2, height - margin - 11f, style)
                } else {
                    page.texts
                }
                PrintPage(texts, page.rules, page.diagrams)
            },
        )
    }
}

/** A text of a [Row], placed relative to the row's top left corner. */
private data class Part(
    val text: String,
    val x: Float = 0f,
    val y: Float = 0f,
    val style: PrintStyle,
    val isSelectable: Boolean = true,
)

/** The unit [PrintLayouter.place] never splits: a lyric line with its chords, a tab system, a row of chord diagrams. */
private data class Row(
    val parts: List<Part>,
    val height: Float,
    val rules: List<PrintRule> = emptyList(),
    val diagrams: List<PrintDiagram> = emptyList(),
)

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

/**
 * What stands between two values on the line that says how a song is played: wide enough a gap for the importer to read
 * each as a value of its own.
 */
private const val METADATA_SEPARATOR = "   "

/** What [PrintLabels.tempoValue] holds in place of the number. */
internal const val TEMPO_VALUE = "\u0001"

private const val CHORUS_INDENT = 8f
private const val CHORUS_BAR_WIDTH = 1.5f
private const val FRAME_WIDTH = 0.75f

// The sizes of a diagram and the gap between two, in multiples of the text size: at 12 pt a guitar diagram is about the
// one the song details screen draws at its own text size, and the cells keep that proportion as the text grows.
private const val FRETTED_DIAGRAM_WIDTH = 3.5f
private const val FRETTED_DIAGRAM_HEIGHT = 4.4f
private const val KEYBOARD_DIAGRAM_WIDTH = 4.75f
private const val KEYBOARD_DIAGRAM_HEIGHT = 2.5f
private const val DIAGRAM_GAP = 0.75f

private fun ChordProBlock?.isChorus() = this is ChordProBlock.Section && type == SectionType.Chorus

/** A chorus section, or a comment written inside one or at the start of one, which is printed as part of it. */
private fun List<ChordProBlock>.isInChorus(index: Int): Boolean = when (val block = this[index]) {
    is ChordProBlock.Section -> block.isChorus()
    is ChordProBlock.Comment -> (block.placement == CommentPlacement.IN_SECTION && getOrNull(index - 1).isChorus()) ||
        (block.placement == CommentPlacement.START_OF_SECTION && getOrNull(index + 1).isChorus())
    else -> false
}

/** Whether the block after [index] is the same chorus going on: its continuation, or a comment inside or at the start of it. */
private fun List<ChordProBlock>.continuesChorusAfter(index: Int): Boolean {
    if (!isInChorus(index) || index + 1 > lastIndex || !isInChorus(index + 1)) return false
    return when (val next = this[index + 1]) {
        is ChordProBlock.Section -> next.isContinuation ||
            (this[index] as? ChordProBlock.Comment)?.placement == CommentPlacement.START_OF_SECTION
        is ChordProBlock.Comment -> next.placement == CommentPlacement.IN_SECTION
        else -> false
    }
}

private fun GridToken.printText() = when (this) {
    is GridToken.Bar -> text
    is GridToken.Chord -> name
    is GridToken.Text -> text
    is GridToken.Repeat -> text
    GridToken.Beat -> "."
}

/** Blank once the padding the chords add (no-break spaces and the zero-width break opportunities) is disregarded too. */
private fun String.isPrintBlank() = all { it.isWhitespace() || it == '\u00A0' || it == '\u200B' }

/** Preserve every character (including spaces used as chord anchors), splitting at words where there is room. */
internal fun wrapPrintText(text: String, width: Float, measure: (String) -> Float): List<String> {
    if (text.isEmpty()) return listOf("")
    if (measure(text) <= width) return listOf(text)
    val result = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        // Bound the search by a few screenfuls, rather than shaping the whole remaining line for every wrap.
        var upper = minOf(start + 32, text.length)
        while (upper < text.length && measure(text.substring(start, upper)) <= width) {
            upper = minOf(text.length, start + (upper - start) * 2)
        }
        var low = start + 1
        var high = upper
        var end = low
        while (low <= high) {
            val middle = (low + high) / 2
            if (measure(text.substring(start, middle)) <= width) {
                end = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        if (end < text.length) {
            // Break after the last space or break opportunity, unless the word after it would not fit a line of its own
            // either, in which case the hard cut is the right one. The cut only ever moves back, so the fragments still
            // add up to the text and chord positions still map onto them by index.
            val space = maxOf(text.lastIndexOf(' ', end - 1), text.lastIndexOf('\u200B', end - 1))
            if (space > start) {
                val next = listOf(text.indexOf(' ', space + 1), text.indexOf('\u200B', space + 1))
                    .filter { it >= 0 }
                    .minOrNull() ?: text.length
                if (measure(text.substring(space + 1, next)) <= width) end = space + 1
            }
            // A cut inside a grapheme cluster would print a mark, a modifier or half a flag on its own, so it moves back to
            // the start of the cluster, or past its end where the cluster is all the fragment has.
            while (end - start > 1 && text.splitsClusterAt(end)) end--
            while (text.splitsClusterAt(end)) end++
        }
        result += text.substring(start, end)
        start = end
    }
    return result
}

/** Whether a cut between `index - 1` and `index` would separate a character from what is drawn as one with it. */
private fun String.splitsClusterAt(index: Int): Boolean {
    if (index <= 0 || index >= length) return false
    val char = this[index]
    if (char.isLowSurrogate() || char == '\u200D' || char == '\uFE0E' || char == '\uFE0F' || this[index - 1] == '\u200D') return true
    if (char.category in COMBINING_CATEGORIES) return true
    val codePoint = codePointAt(index)
    if (codePoint in SKIN_TONE_MODIFIERS) return true
    // Regional indicators pair up into flags from the start of their run, so a cut after an odd number of them is inside one.
    if (codePoint !in REGIONAL_INDICATORS) return false
    var count = 0
    var position = index
    while (position >= 2 && codePointAt(position - 2) in REGIONAL_INDICATORS) {
        count++
        position -= 2
    }
    return count % 2 == 1
}

private fun String.codePointAt(index: Int): Int =
    if (this[index].isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()) {
        0x10000 + ((this[index].code - 0xD800) shl 10) + (this[index + 1].code - 0xDC00)
    } else {
        this[index].code
    }

private val COMBINING_CATEGORIES = setOf(
    CharCategory.NON_SPACING_MARK,
    CharCategory.ENCLOSING_MARK,
    CharCategory.COMBINING_SPACING_MARK,
)
private val SKIN_TONE_MODIFIERS = 0x1F3FB..0x1F3FF
private val REGIONAL_INDICATORS = 0x1F1E6..0x1F1FF
