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

/** Coordinates and sizes are PDF points (1/72 inch), independent of screen density and accessibility text size. */
internal data class PrintText(val text: String, val x: Float, val y: Float, val size: Int, val bold: Boolean = false)
internal data class PrintPage(val texts: List<PrintText>)
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
    measureText: (text: String, size: Int, bold: Boolean) -> Float,
): PrintDocument {
    val options = settings.normalized()
    val width = if (options.isLandscape) options.paper.height else options.paper.width
    val height = if (options.isLandscape) options.paper.width else options.paper.height
    val margin = options.marginMm * 72f / 25.4f
    val gutter = 18f
    val columnWidth = (width - 2 * margin - gutter * (options.columns - 1)) / options.columns
    val bottom = height - margin - if (options.showPageNumbers) 18f else 0f
    val capacity = bottom - margin
    val headingGap = options.fontSize / 2f
    // Chord names, spaces and the other short strings are measured over and over; only they are kept, so that the
    // substrings of lyrics the wrapping tries are not held for the life of a large setlist.
    val shortMeasurements = mutableMapOf<Triple<String, Int, Boolean>, Float>()
    fun measure(text: String, size: Int, bold: Boolean): Float =
        if (text.length <= 8) shortMeasurements.getOrPut(Triple(text, size, bold)) { measureText(text, size, bold) } else measureText(text, size, bold)
    val pages = mutableListOf<MutableList<PrintText>>()
    var column = 0
    var y = margin
    fun page() { pages.add(mutableListOf()); column = 0; y = margin }
    fun nextColumn() {
        if (column + 1 < options.columns) { column++; y = margin } else page()
    }
    fun newPage() { if (pages.isNotEmpty() && pages.last().isNotEmpty()) page() }
    fun space(amount: Float) { y = (y + amount).coerceAtMost(bottom) }
    fun place(rows: List<Row>, keepWhole: Boolean = true) {
        val total = rows.height()
        val firstRows = rows.take(2).height()
        val fitsWhole = keepWhole && total <= capacity
        if ((fitsWhole && y + total > bottom) || (!fitsWhole && firstRows <= capacity && y + firstRows > bottom)) nextColumn()
        rows.forEach { row ->
            if (y + row.height > bottom) nextColumn()
            val x = margin + column * (columnWidth + gutter)
            row.parts.forEach { part -> pages.last().add(PrintText(part.text, x + part.x, y + part.y, part.size, part.bold)) }
            y += row.height
        }
    }
    // A line break inside a text (a setlist description has up to three lines) starts a row of its own, since a row is
    // one line tall and the renderer would draw the rest over whatever comes under it.
    fun wrapped(text: String, size: Int = options.fontSize, bold: Boolean = false): List<Row> = text.lines().flatMap { line ->
        wrapPrintText(line, columnWidth) { measure(it, size, bold) }.map { Row(listOf(Part(it, size = size, bold = bold)), size * 1.45f) }
    }
    fun metadata(song: ChordProSong): String = listOfNotNull(
        song.metadata.key?.takeIf { options.showChords }?.let { "${labels.key}: $it" },
        song.metadata.capo?.takeIf { options.showChords }?.let { "${labels.capo}: $it" },
        song.metadata.tempo?.let { "${labels.tempo}: $it" },
        song.metadata.time?.let { "${labels.time}: $it" },
    ).joinToString("   ")
    fun rowsFor(block: ChordProBlock, labelOverride: String? = null): List<Row> = when (block) {
        is ChordProBlock.Section -> {
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
                            // to a column at most rather than to its full width.
                            val lyrics = if (visible.chords.isNotEmpty()) visible.padLyricsToFitChords(
                                chordWidths = visible.chords.map { minOf(measure(it.name, options.fontSize, true), columnWidth) },
                                gap = measure(" ", options.fontSize, false),
                                paddingWidth = measure("\u00A0", options.fontSize, false),
                                measureWidth = { measure(it, options.fontSize, false) },
                            ) else visible
                            val fragments = wrapPrintText(lyrics.text, columnWidth) { measure(it, options.fontSize, false) }
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
                                    val anchor = measure(fragment.take((chord.position - start).coerceIn(0, fragment.length)), options.fontSize, false)
                                    var x = maxOf(anchor, chordX)
                                    val chordWidth = measure(chord.name, options.fontSize, true)
                                    if (x > 0f && x + chordWidth > columnWidth) { chordY += options.fontSize * 1.3f; x = 0f }
                                    val pieces = wrapPrintText(chord.name, columnWidth) { measure(it, options.fontSize, true) }
                                    pieces.forEachIndexed { i, name ->
                                        if (i > 0) { chordY += options.fontSize * 1.3f; x = 0f }
                                        parts += Part(name, x = x, y = chordY, size = options.fontSize, bold = true)
                                    }
                                    val lastWidth = if (pieces.size == 1) chordWidth else measure(pieces.last(), options.fontSize, true)
                                    chordX = x + lastWidth + measure(" ", options.fontSize, false)
                                }
                                // A fragment of chords over nothing but padding is one row of chords, not chords over an empty
                                // lyric row; a blank fragment without chords is an empty line the song asked for.
                                val isChordsOnly = parts.isNotEmpty() && fragment.isPrintBlank()
                                val lyricY = if (parts.isEmpty()) 0f else chordY + options.fontSize * 1.3f
                                if (!isChordsOnly) parts += Part(fragment, y = lyricY, size = options.fontSize)
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
                            line.label?.takeUnless { it == sectionLabel }?.let { addAll(wrapped(it, bold = true)) }
                            val characters = (columnWidth / measure("M", options.fontSize, false)).toInt().coerceAtLeast(1)
                            val isTablature = ChordProTabWrapper.isTablature(run)
                            val systems = if (isTablature) ChordProTabWrapper.wrap(run, characters) else ChordProTabWrapper.wrapPreformatted(run, characters)
                            systems.forEachIndexed { systemIndex, system ->
                                // Staves without a gap between them read as one staff of twice the strings; a preformatted
                                // run is chord names over lyrics, read like wrapped text, so it gets none.
                                if (isTablature && systemIndex > 0) add(Row(emptyList(), options.fontSize * 0.7f))
                                val rows = system.flatMap { wrapped(it) }
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
                            val space = measure(" ", options.fontSize, true)
                            var row = ""
                            var rowWidth = 0f
                            line.tokens.bars().forEach { bar ->
                                val text = bar.joinToString(" ") { it.printText() }
                                val barWidth = measure(text, options.fontSize, true)
                                if (row.isNotEmpty() && rowWidth + space + barWidth <= columnWidth) { row += " $text"; rowWidth += space + barWidth } else {
                                    if (row.isNotEmpty()) addAll(wrapped(row, bold = true))
                                    if (barWidth <= columnWidth) { row = text; rowWidth = barWidth } else { addAll(wrapped(text, bold = true)); row = ""; rowWidth = 0f }
                                }
                            }
                            if (row.isNotEmpty()) addAll(wrapped(row, bold = true))
                        }
                        ChordProLine.Blank -> add(Row(emptyList(), options.fontSize * 0.7f))
                    }
                }
            }
            // A section whose every line the options hide leaves out its label too, rather than printing a heading with
            // nothing under it; one written with no lines at all keeps it, since there the label is the cue.
            if (block.lines.isNotEmpty() && lines.none { row -> row.parts.any { !it.text.isPrintBlank() } }) emptyList()
            else (labelOverride ?: sectionLabel?.takeUnless { block.isContinuation })?.let { wrapped(it, bold = true) }.orEmpty() + lines
        }
        is ChordProBlock.Comment -> if (options.showComments && (!block.isInTabOrGrid || options.showChords)) wrapped(block.text) else emptyList()
        is ChordProBlock.ChorusRecall -> {
            // As in the viewer, the recall's own heading goes on the first recalled piece that prints anything, and is
            // printed on its own when nothing is: a recall says where the chorus is sung, even with nothing under it.
            var header: String? = block.label ?: (block.blocks.firstOrNull() as? ChordProBlock.Section)?.label ?: labels.chorus
            block.blocks.flatMap { piece ->
                if (piece is ChordProBlock.Section && header != null) rowsFor(piece, header).also { if (it.isNotEmpty()) header = null } else rowsFor(piece)
            }.ifEmpty { header?.let { wrapped(it, bold = true) }.orEmpty() }
        }
        is ChordProBlock.Transpose, ChordProBlock.Break -> emptyList()
    }

    page()
    if (source.isSetlist && (options.includeSetlistOverview || options.setlistMode == PrintSettings.SetlistMode.RUNNING_ORDER)) {
        place(wrapped(source.title, options.fontSize + 6, true))
        if (options.showMetadata) {
            source.date?.let { place(wrapped(it)) }
            if (source.description.isNotBlank()) place(wrapped(source.description))
        }
        space(options.fontSize.toFloat())
        source.songs.forEach { entry ->
            val detail = entry.song?.metadata?.key?.takeIf { options.showChords }?.let { " (${labels.key}: $it)" }.orEmpty()
            val missing = if (entry.song == null) " [${labels.missing}]" else ""
            place(wrapped("${entry.index}. ${entry.title}$detail$missing", bold = true) +
                if (options.showMetadata && !entry.artist.isNullOrBlank()) wrapped(entry.artist) else emptyList())
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
        val heading = wrapped(title, options.fontSize + 2, true).toMutableList()
        if (options.showMetadata) {
            entry.artist?.takeIf { it.isNotBlank() }?.let { heading += wrapped(it) }
            entry.song?.let { metadata(it).takeIf(String::isNotBlank)?.let { heading += wrapped(it) } }
        }
        // The heading travels with the first block that prints anything (a missing song's notice standing in for it):
        // that block whole where heading and block fit a column together, otherwise its first two rows, after which the
        // block flows on from under its heading. A break before that block is ignored, since the heading has just
        // started the song where it is.
        val blocks = entry.song?.blocks.orEmpty()
        var firstIndex = -1
        var firstRows = if (entry.song == null) wrapped(labels.missing) else emptyList()
        blocks.forEachIndexed { index, block ->
            if (firstIndex < 0 && block != ChordProBlock.Break) rowsFor(block).takeIf { it.isNotEmpty() }?.let { firstIndex = index; firstRows = it }
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
                    index == firstIndex -> { place(firstRows, keepWhole = keepFirstWhole); space(options.fontSize * 0.65f) }
                    block == ChordProBlock.Break -> if (y > margin) nextColumn()
                    else -> {
                        val rows = rowsFor(block)
                        if (rows.isNotEmpty()) { place(rows); space(options.fontSize * 0.65f) }
                    }
                }
            }
        }
        space(options.fontSize.toFloat())
    }
    return finishPrintDocument(width, height, pages, options, margin, ::measure)
}

private data class Part(val text: String, val x: Float = 0f, val y: Float = 0f, val size: Int, val bold: Boolean = false)
private data class Row(val parts: List<Part>, val height: Float)

private fun List<Row>.height() = sumOf { it.height.toDouble() }.toFloat()

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
    pages: List<List<PrintText>>,
    settings: PrintSettings,
    margin: Float,
    measure: (text: String, size: Int, bold: Boolean) -> Float,
): PrintDocument {
    val nonempty = pages.filter { it.isNotEmpty() }
    return PrintDocument(width, height, nonempty.mapIndexed { index, texts ->
        val number = "${index + 1} / ${nonempty.size}"
        PrintPage(if (settings.showPageNumbers) texts + PrintText(number, (width - measure(number, 9, false)) / 2, height - margin - 11f, 9) else texts)
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
