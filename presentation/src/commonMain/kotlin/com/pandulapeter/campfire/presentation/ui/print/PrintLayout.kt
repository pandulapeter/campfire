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

import com.pandulapeter.campfire.chordpro.model.*
import com.pandulapeter.campfire.chordpro.ChordProTabWrapper
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.bars
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.padLyricsToFitChords
import com.pandulapeter.campfire.data.model.domain.PrintSettings
import kotlinx.coroutines.yield
import kotlin.math.roundToInt

internal data class PrintSource(
    val title: String,
    val description: String = "",
    val date: String? = null,
    val isSetlist: Boolean = false,
    val songs: List<PrintSong>,
)

internal data class PrintSong(
    val fileName: String,
    val title: String,
    val artist: String?,
    val index: Int? = null,
    /** Null for an unreadable or missing file; its place remains visible in the running order. */
    val song: ChordProSong?,
)

/**
 * How a text is set: [size] in PDF points, [monospace] for the tablature and grids whose characters have to line up
 * (everything else is in the app's text font, as in the viewer), and [gray] from 0 (black) to 255 (white).
 */
internal data class PrintStyle(val size: Int, val bold: Boolean = false, val italic: Boolean = false, val monospace: Boolean = false, val gray: Int = 0)

/** Coordinates and sizes are PDF points (1/72 inch), independent of screen density and accessibility text size. */
internal data class PrintText(val text: String, val x: Float, val y: Float, val style: PrintStyle)
/** A filled rectangle, the way frames and bars are drawn; in PDF points like the texts, [gray] from 0 (black) to 255. */
internal data class PrintRule(val x: Float, val y: Float, val width: Float, val height: Float, val gray: Int = 0)
internal data class PrintPage(val texts: List<PrintText>, val rules: List<PrintRule> = emptyList())
internal data class PrintDocument(val width: Float, val height: Float, val pages: List<PrintPage>)
internal data class PrintLabels(val key: String, val capo: String, val tempo: String, val time: String, val missing: String, val chorus: String, val bridge: String)

/**
 * A single layout for the preview and export. Measuring is supplied by the same Compose text renderer that draws
 * both. A lyric/chord pair is indivisible; a section that fits in a column is kept together. Longer sections flow
 * through columns and pages without reducing the requested size or clipping the remainder.
 *
 * It suspends between songs and every few dozen blocks: on the web the layout shares the page's one thread, and only
 * while it is suspended can the page paint and a newer choice of options cancel a stale layout.
 */
internal suspend fun layoutPrintDocument(
    source: PrintSource,
    settings: PrintSettings,
    labels: PrintLabels,
    measureText: (text: String, style: PrintStyle) -> Float,
): PrintDocument {
    val options = settings.normalized()
    val width = if (options.isLandscape) options.paper.height else options.paper.width
    val height = if (options.isLandscape) options.paper.width else options.paper.height
    val margin = options.marginMm * 72f / 25.4f
    val gutter = 18f
    val columnWidth = (width - 2 * margin - gutter * (options.columns - 1)) / options.columns
    val bottom = height - margin - if (options.showPageNumbers) 18f else 0f
    val capacity = bottom - margin
    val headingGap = options.fontSize * 0.8f
    // The page reads from the title down to the lyrics: the title large, the details and the section labels smaller and
    // gray, so that a label is never taken for a chord, and only tablature and grids in the monospace face.
    val titleStyle = PrintStyle((options.fontSize * 1.6f).roundToInt(), bold = true)
    val lyricStyle = PrintStyle(options.fontSize)
    val chordStyle = PrintStyle(options.fontSize, bold = true)
    val annotationStyle = PrintStyle(options.fontSize, italic = true)
    val commentStyle = PrintStyle(options.fontSize, italic = true, gray = 90)
    val detailStyle = PrintStyle(options.fontSize - 1, gray = 90)
    val labelStyle = PrintStyle(options.fontSize - 1, bold = true, gray = 90)
    val tabStyle = PrintStyle(options.fontSize, monospace = true)
    val gridStyle = PrintStyle(options.fontSize, bold = true, monospace = true)
    val labelSpace = options.fontSize * 0.3f
    // Chord names, spaces and the other short strings are measured over and over; only they are kept, so that the
    // substrings of lyrics the wrapping tries are not held for the life of a large setlist.
    val shortMeasurements = mutableMapOf<Pair<String, PrintStyle>, Float>()
    fun measure(text: String, style: PrintStyle): Float =
        if (text.length <= 8) shortMeasurements.getOrPut(text to style) { measureText(text, style) } else measureText(text, style)
    val pages = mutableListOf<PageContent>()
    var column = 0
    var y = margin
    fun page() { pages.add(PageContent()); column = 0; y = margin }
    fun nextColumn() {
        if (column + 1 < options.columns) { column++; y = margin } else page()
    }
    fun newPage() { if (pages.isNotEmpty() && pages.last().texts.isNotEmpty()) page() }
    fun space(amount: Float) { y = (y + amount).coerceAtMost(bottom) }
    fun place(rows: List<Row>, keepWhole: Boolean = true) {
        val total = rows.height()
        val firstRows = rows.take(2).height()
        val fitsWhole = keepWhole && total <= capacity
        if ((fitsWhole && y + total > bottom) || (!fitsWhole && firstRows <= capacity && y + firstRows > bottom)) nextColumn()
        rows.forEach { row ->
            if (y + row.height > bottom) nextColumn()
            val x = margin + column * (columnWidth + gutter)
            row.parts.forEach { part -> pages.last().texts.add(PrintText(part.text, x + part.x, y + part.y, part.style)) }
            row.rules.forEach { rule -> pages.last().rules.add(rule.copy(x = x + rule.x, y = y + rule.y)) }
            y += row.height
        }
    }
    // A line break inside a text (a setlist description has up to three lines) starts a row of its own, since a row is
    // one line tall and the renderer would draw the rest over whatever comes under it.
    fun wrapped(text: String, style: PrintStyle = lyricStyle, width: Float = columnWidth): List<Row> = text.lines().flatMap { line ->
        wrapPrintText(line, width) { measure(it, style) }.map { Row(listOf(Part(it, style = style)), style.size * 1.45f) }
    }
    // The space above a label belongs to its first row, so that it never ends a column on its own or opens one with a gap.
    fun label(text: String, width: Float): List<Row> = wrapped(text, labelStyle, width).mapIndexed { index, row ->
        if (index == 0) Row(row.parts.map { it.copy(y = it.y + labelSpace) }, row.height + labelSpace) else row
    }
    // Every row of a boxed comment carries its own piece of the frame, inside its own height, so that a box the
    // placement splits between columns continues as an open frame and never draws over what is before or after it.
    fun boxed(text: String, width: Float): List<Row> {
        val rows = wrapped(text, lyricStyle, width - 10f)
        return rows.mapIndexed { index, row ->
            val top = if (index == 0) 3f else 0f
            val height = row.height + top + if (index == rows.lastIndex) 3f else 0f
            Row(row.parts.map { it.copy(x = it.x + 5f, y = it.y + top) }, height, buildList {
                add(PrintRule(0f, 0f, 0.75f, height))
                add(PrintRule(width - 0.75f, 0f, 0.75f, height))
                if (index == 0) add(PrintRule(0f, 0f, width, 0.75f))
                if (index == rows.lastIndex) add(PrintRule(0f, height - 0.75f, width, 0.75f))
            })
        }
    }
    // A chorus is indented, with a bar down its left side that every row carries its own piece of, so that the bar
    // continues without a gap across a column or page break; on a label's row it starts at the label, not above it.
    fun chorusBar(rows: List<Row>, startsWithLabel: Boolean): List<Row> = rows.mapIndexed { index, row ->
        val top = if (index == 0 && startsWithLabel) labelSpace else 0f
        Row(
            parts = row.parts.map { it.copy(x = it.x + CHORUS_INDENT) },
            height = row.height,
            rules = row.rules.map { it.copy(x = it.x + CHORUS_INDENT) } + PrintRule(0f, top, CHORUS_BAR_WIDTH, row.height - top),
        )
    }
    fun metadata(song: ChordProSong): String = listOfNotNull(
        song.metadata.key?.takeIf { options.showChords }?.let { "${labels.key}: $it" },
        song.metadata.capo?.takeIf { options.showChords }?.let { "${labels.capo}: $it" },
        song.metadata.tempo?.let { "${labels.tempo}: $it" },
        song.metadata.time?.let { "${labels.time}: $it" },
    ).joinToString("   ")
    fun rowsFor(block: ChordProBlock, width: Float, labelOverride: String? = null, isInChorus: Boolean = false): List<Row> = when (block) {
        is ChordProBlock.Section -> {
            val isChorus = block.type == SectionType.Chorus
            val lineWidth = if (isChorus) width - CHORUS_INDENT else width
            val sectionLabel = block.label ?: when (val type = block.type) {
                // An unnamed verse is set apart by the gap before it, as in the viewer, rather than by a heading.
                SectionType.Verse -> null
                SectionType.Chorus -> labels.chorus
                SectionType.Bridge -> labels.bridge
                is SectionType.Custom -> type.name.replace('_', ' ').replaceFirstChar { it.uppercase() }
                SectionType.Paragraph -> null
            }
            val lines = buildList {
                block.lines.forEachIndexed lineLoop@ { lineIndex, line ->
                    when (line) {
                        is ChordProLine.Lyrics -> {
                            val visible = line.copy(chords = line.chords.filter { if (it.isAnnotation) options.showComments else options.showChords })
                            // Hiding a chord-only line must also remove the empty lyric row beneath it.
                            if (line.chords.isNotEmpty() && visible.chords.isEmpty() && visible.text.isPrintBlank()) return@lineLoop
                            // A chord wider than the column is wrapped onto rows of its own, so the lyrics under it are padded
                            // to a column at most rather than to its full lineWidth.
                            val lyrics = if (visible.chords.isNotEmpty()) visible.padLyricsToFitChords(
                                chordWidths = visible.chords.map { minOf(measure(it.name, if (it.isAnnotation) annotationStyle else chordStyle), lineWidth) },
                                gap = measure(" ", lyricStyle),
                                paddingWidth = measure("\u00A0", lyricStyle),
                                measureWidth = { measure(it, lyricStyle) },
                            ) else visible
                            val fragments = wrapPrintText(lyrics.text, lineWidth) { measure(it, lyricStyle) }
                            var start = 0
                            fragments.forEachIndexed { fragmentIndex, fragment ->
                                val end = start + fragment.length
                                val chords = lyrics.chords.filter { it.position >= start && (it.position < end || fragmentIndex == fragments.lastIndex) }
                                // Chords anchored at the same character are kept beside each other; if the chord
                                // run is wider than the column, extra chord-only rows keep every symbol readable.
                                val parts = mutableListOf<Part>()
                                var chordX = 0f
                                var chordY = 0f
                                chords.forEach { chord ->
                                    val anchor = measure(fragment.take((chord.position - start).coerceIn(0, fragment.length)), lyricStyle)
                                    var x = maxOf(anchor, chordX)
                                    // An annotation is an instruction rather than a chord, so it is set apart from the chords.
                                    val style = if (chord.isAnnotation) annotationStyle else chordStyle
                                    val chordWidth = measure(chord.name, style)
                                    if (x > 0f && x + chordWidth > lineWidth) { chordY += options.fontSize * 1.3f; x = 0f }
                                    val pieces = wrapPrintText(chord.name, lineWidth) { measure(it, style) }
                                    pieces.forEachIndexed { i, name ->
                                        if (i > 0) { chordY += options.fontSize * 1.3f; x = 0f }
                                        parts += Part(name, x = x, y = chordY, style = style)
                                    }
                                    val lastWidth = if (pieces.size == 1) chordWidth else measure(pieces.last(), style)
                                    chordX = x + lastWidth + measure(" ", lyricStyle)
                                }
                                // A fragment of chords over nothing but padding is one row of chords, not chords over an empty
                                // lyric row; a blank fragment without chords is an empty line the song asked for.
                                val isChordsOnly = parts.isNotEmpty() && fragment.isPrintBlank()
                                val lyricY = if (parts.isEmpty()) 0f else chordY + options.fontSize * 1.3f
                                if (!isChordsOnly) parts += Part(fragment, y = lyricY, style = lyricStyle)
                                val rowHeight = (if (isChordsOnly) chordY else lyricY) + options.fontSize * 1.45f
                                if (rowHeight <= capacity) add(Row(parts, rowHeight)) else {
                                    // A very long annotation can span a page by itself. Keep its final line with
                                    // the lyrics while allowing its preceding lines to flow through the document.
                                    val byLine = parts.groupBy { it.y }.entries.toList()
                                    byLine.dropLast(2).forEach { (_, values) -> add(Row(values.map { it.copy(y = 0f) }, options.fontSize * 1.3f)) }
                                    val last = byLine.takeLast(2)
                                    val top = last.first().key
                                    add(Row(last.flatMap { it.value }.map { it.copy(y = it.y - top) }, options.fontSize * 2.75f))
                                }
                                start = end
                            }
                        }
                        is ChordProLine.Tab -> if (options.showChords && (lineIndex == 0 || block.lines[lineIndex - 1] !is ChordProLine.Tab || !line.continuesEnvironment)) {
                            val run = block.lines.drop(lineIndex).takeWhile { it is ChordProLine.Tab && (it === line || it.continuesEnvironment) }.map { (it as ChordProLine.Tab).text }
                            line.label?.takeUnless { it == sectionLabel }?.let { addAll(label(it, lineWidth)) }
                            val characters = (lineWidth / measure("M", tabStyle)).toInt().coerceAtLeast(1)
                            val isTablature = ChordProTabWrapper.isTablature(run)
                            val systems = if (isTablature) ChordProTabWrapper.wrap(run, characters) else ChordProTabWrapper.wrapPreformatted(run, characters)
                            systems.forEachIndexed { systemIndex, system ->
                                // Staves without a gap between them read as one staff of twice the strings; a preformatted
                                // run is chord names over lyrics, read like wrapped text, so it gets none.
                                if (isTablature && systemIndex > 0) add(Row(emptyList(), options.fontSize * 0.7f))
                                val rows = system.flatMap { wrapped(it, tabStyle, lineWidth) }
                                val systemHeight = rows.sumOf { it.height.toDouble() }.toFloat()
                                if (systemHeight <= capacity) {
                                    var rowY = 0f
                                    add(Row(rows.flatMap { row -> row.parts.map { it.copy(y = rowY) }.also { rowY += row.height } }, systemHeight))
                                } else addAll(rows)
                            }
                        }
                        is ChordProLine.Grid -> if (options.showChords) {
                            // A grid line is broken between bars, as the viewer breaks it, each row taking as many whole
                            // bars as fit; only a bar wider than the column is wrapped inside itself.
                            val space = measure(" ", gridStyle)
                            var row = ""
                            var rowWidth = 0f
                            line.tokens.bars().forEach { bar ->
                                val text = bar.joinToString(" ") { it.printText() }
                                val barWidth = measure(text, gridStyle)
                                if (row.isNotEmpty() && rowWidth + space + barWidth <= lineWidth) { row += " $text"; rowWidth += space + barWidth } else {
                                    if (row.isNotEmpty()) addAll(wrapped(row, gridStyle, lineWidth))
                                    if (barWidth <= lineWidth) { row = text; rowWidth = barWidth } else { addAll(wrapped(text, gridStyle, lineWidth)); row = ""; rowWidth = 0f }
                                }
                            }
                            if (row.isNotEmpty()) addAll(wrapped(row, gridStyle, lineWidth))
                        }
                        ChordProLine.Blank -> add(Row(emptyList(), options.fontSize * 0.7f))
                    }
                }
            }
            // A section whose every line the options hide leaves out its label too, rather than printing a heading with
            // nothing under it; one written with no lines at all keeps it, since there the label is the cue.
            if (block.lines.isNotEmpty() && lines.none { row -> row.parts.any { !it.text.isPrintBlank() } }) emptyList() else {
                val labelRows = (labelOverride ?: sectionLabel?.takeUnless { block.isContinuation })?.let { label(it, lineWidth) }.orEmpty()
                if (isChorus) chorusBar(labelRows + lines, startsWithLabel = labelRows.isNotEmpty()) else labelRows + lines
            }
        }
        is ChordProBlock.Comment -> when {
            !options.showComments || (block.isInTabOrGrid && !options.showChords) -> emptyList()
            isInChorus -> chorusBar(rowsFor(block, width - CHORUS_INDENT), startsWithLabel = false)
            block.style == CommentStyle.BOX -> boxed(block.text, width)
            else -> wrapped(block.text, commentStyle, width)
        }
        is ChordProBlock.ChorusRecall -> {
            // As in the viewer, the recall's own heading goes on the first recalled piece that prints anything, and is
            // printed on its own when nothing is: a recall says where the chorus is sung, even with nothing under it.
            var header: String? = block.label ?: (block.blocks.firstOrNull() as? ChordProBlock.Section)?.label ?: labels.chorus
            block.blocks.flatMapIndexed { index, piece ->
                val isPieceInChorus = block.blocks.isInChorus(index)
                if (piece is ChordProBlock.Section && header != null) rowsFor(piece, width, header).also { if (it.isNotEmpty()) header = null }
                else rowsFor(piece, width, isInChorus = isPieceInChorus)
            }.ifEmpty { header?.let { label(it, width) }.orEmpty() }
        }
        is ChordProBlock.Transpose, ChordProBlock.Break -> emptyList()
    }

    page()
    if (source.isSetlist && (options.includeSetlistOverview || options.setlistMode == PrintSettings.SetlistMode.RUNNING_ORDER)) {
        place(wrapped(source.title, titleStyle))
        if (options.showMetadata) {
            source.date?.let { place(wrapped(it)) }
            if (source.description.isNotBlank()) place(wrapped(source.description))
        }
        space(options.fontSize.toFloat())
        source.songs.forEach { entry ->
            val detail = entry.song?.metadata?.key?.takeIf { options.showChords }?.let { " (${labels.key}: $it)" }.orEmpty()
            val missing = if (entry.song == null) " [${labels.missing}]" else ""
            place(wrapped("${entry.index}. ${entry.title}$detail$missing", chordStyle) +
                if (options.showMetadata && !entry.artist.isNullOrBlank()) wrapped(entry.artist, PrintStyle(options.fontSize, gray = 90)) else emptyList())
            space(options.fontSize / 2f)
        }
        if (options.setlistMode == PrintSettings.SetlistMode.RUNNING_ORDER) return finishPrintDocument(width, height, pages, options, margin, ::measure)
        // Song sheets follow the running order on a fresh page, including in the compact layout.
        newPage()
    }
    var laidOutBlocks = 0
    source.songs.forEachIndexed { index, entry ->
        yield()
        if (index > 0 && options.startSongsOnNewPage) newPage()
        val title = entry.index?.let { "$it. ${entry.title}" } ?: entry.title
        val heading = wrapped(title, titleStyle).toMutableList()
        if (options.showMetadata) {
            entry.artist?.takeIf { it.isNotBlank() }?.let { heading += wrapped(it) }
            entry.song?.let { metadata(it).takeIf(String::isNotBlank)?.let { heading += wrapped(it, detailStyle) } }
        }
        // The heading travels with the first block that prints anything (a missing song's notice standing in for it):
        // that block whole where heading and block fit a column together, otherwise its first two rows, after which the
        // block flows on from under its heading. A break before that block is ignored, since the heading has just
        // started the song where it is.
        val blocks = entry.song?.blocks.orEmpty()
        // Between two blocks of one chorus the gap carries the chorus's bar, so that a comment does not cut it in two.
        fun rowsAt(index: Int): List<Row> {
            val rows = rowsFor(blocks[index], columnWidth, isInChorus = blocks.isInChorus(index))
            return if (rows.isNotEmpty() && blocks.continuesChorusAfter(index)) {
                rows + Row(emptyList(), options.fontSize * 0.65f, listOf(PrintRule(0f, 0f, CHORUS_BAR_WIDTH, options.fontSize * 0.65f)))
            } else rows
        }
        fun spaceAfter(index: Int) { if (!blocks.continuesChorusAfter(index)) space(options.fontSize * 0.65f) }
        var firstIndex = -1
        var firstRows = if (entry.song == null) wrapped(labels.missing) else emptyList()
        blocks.forEachIndexed { index, block ->
            if (firstIndex < 0 && block != ChordProBlock.Break) rowsAt(index).takeIf { it.isNotEmpty() }?.let { firstIndex = index; firstRows = it }
        }
        val headingHeight = heading.height()
        val keepFirstWhole = headingHeight + headingGap + firstRows.height() <= capacity
        val together = headingHeight + headingGap + if (keepFirstWhole) firstRows.height() else firstRows.take(2).height()
        if (firstRows.isNotEmpty() && together <= capacity && y + together > bottom) nextColumn()
        place(heading)
        space(headingGap)
        if (entry.song == null) place(firstRows, keepWhole = keepFirstWhole) else if (firstIndex >= 0) {
            blocks.forEachIndexed { index, block ->
                if (++laidOutBlocks % 50 == 0) yield()
                when {
                    index < firstIndex -> Unit
                    index == firstIndex -> { place(firstRows, keepWhole = keepFirstWhole); spaceAfter(index) }
                    block == ChordProBlock.Break -> if (y > margin) nextColumn()
                    else -> {
                        val rows = rowsAt(index)
                        if (rows.isNotEmpty()) { place(rows); spaceAfter(index) }
                    }
                }
            }
        }
        space(options.fontSize.toFloat())
    }
    return finishPrintDocument(width, height, pages, options, margin, ::measure)
}

private data class Part(val text: String, val x: Float = 0f, val y: Float = 0f, val style: PrintStyle)
private data class Row(val parts: List<Part>, val height: Float, val rules: List<PrintRule> = emptyList())

private class PageContent {
    val texts = mutableListOf<PrintText>()
    val rules = mutableListOf<PrintRule>()
}

private fun List<Row>.height() = sumOf { it.height.toDouble() }.toFloat()

private const val CHORUS_INDENT = 8f
private const val CHORUS_BAR_WIDTH = 1.5f

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
        is ChordProBlock.Section -> next.isContinuation || (this[index] as? ChordProBlock.Comment)?.placement == CommentPlacement.START_OF_SECTION
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

/**
 * The page number sits centred in the band the layout keeps free above the bottom margin (a 9 pt line is about 11 pt
 * tall), so it never reaches into the margin the user chose, where printers may not print.
 */
private fun finishPrintDocument(
    width: Float,
    height: Float,
    pages: List<PageContent>,
    settings: PrintSettings,
    margin: Float,
    measure: (text: String, style: PrintStyle) -> Float,
): PrintDocument {
    val nonempty = pages.filter { it.texts.isNotEmpty() }
    return PrintDocument(width, height, nonempty.mapIndexed { index, page ->
        val number = "${index + 1} / ${nonempty.size}"
        val style = PrintStyle(9, gray = 90)
        PrintPage(if (settings.showPageNumbers) page.texts + PrintText(number, (width - measure(number, style)) / 2, height - margin - 11f, style) else page.texts, page.rules)
    })
}

/** Preserve every character (including spaces used as chord anchors), splitting at words where there is room. */
internal fun wrapPrintText(text: String, width: Float, measure: (String) -> Float): List<String> {
    if (text.isEmpty()) return listOf("")
    if (measure(text) <= width) return listOf(text)
    val result = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        // Bound the search by a few screenfuls, rather than shaping the whole remaining line for every wrap.
        var upper = minOf(start + 32, text.length)
        while (upper < text.length && measure(text.substring(start, upper)) <= width) upper = minOf(text.length, start + (upper - start) * 2)
        var low = start + 1
        var high = upper
        var end = low
        while (low <= high) {
            val middle = (low + high) / 2
            if (measure(text.substring(start, middle)) <= width) { end = middle; low = middle + 1 } else high = middle - 1
        }
        if (end < text.length) {
            // Break after the last space or break opportunity, unless the word after it would not fit a line of its own
            // either, in which case the hard cut is the right one. The cut only ever moves back, so the fragments still
            // add up to the text and chord positions still map onto them by index.
            val space = maxOf(text.lastIndexOf(' ', end - 1), text.lastIndexOf('\u200B', end - 1))
            if (space > start) {
                val next = listOf(text.indexOf(' ', space + 1), text.indexOf('\u200B', space + 1)).filter { it >= 0 }.minOrNull() ?: text.length
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
    if (char.category == CharCategory.NON_SPACING_MARK || char.category == CharCategory.ENCLOSING_MARK || char.category == CharCategory.COMBINING_SPACING_MARK) return true
    val codePoint = codePointAt(index)
    if (codePoint in 0x1F3FB..0x1F3FF) return true
    // Regional indicators pair up into flags from the start of their run, so a cut after an odd number of them is inside one.
    if (codePoint !in REGIONAL_INDICATORS) return false
    var count = 0
    var position = index
    while (position >= 2 && codePointAt(position - 2) in REGIONAL_INDICATORS) { count++; position -= 2 }
    return count % 2 == 1
}

private fun String.codePointAt(index: Int): Int =
    if (this[index].isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()) {
        0x10000 + ((this[index].code - 0xD800) shl 10) + (this[index + 1].code - 0xDC00)
    } else this[index].code

private val REGIONAL_INDICATORS = 0x1F1E6..0x1F1FF
