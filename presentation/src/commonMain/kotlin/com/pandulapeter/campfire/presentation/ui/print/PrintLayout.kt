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
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.padLyricsToFitChords
import com.pandulapeter.campfire.data.model.domain.PrintSettings

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
internal data class PrintLabels(val key: String, val capo: String, val tempo: String, val time: String, val missing: String, val verse: String = "Verse", val chorus: String = "Chorus", val bridge: String = "Bridge")

/**
 * A single layout for the preview and export. Measuring is supplied by the same Compose text renderer that draws
 * both. A lyric/chord pair is indivisible; a section that fits in a column is kept together. Longer sections flow
 * through columns and pages without reducing the requested size or clipping the remainder.
 */
internal fun layoutPrintDocument(
    source: PrintSource,
    settings: PrintSettings,
    labels: PrintLabels,
    measure: (text: String, size: Int, bold: Boolean) -> Float,
): PrintDocument {
    val options = settings.normalized()
    val width = if (options.isLandscape) options.paper.height else options.paper.width
    val height = if (options.isLandscape) options.paper.width else options.paper.height
    val margin = options.marginMm * 72f / 25.4f
    val gutter = 18f
    val columnWidth = (width - 2 * margin - gutter * (options.columns - 1)) / options.columns
    val bottom = height - margin - if (options.showPageNumbers) 18f else 0f
    val capacity = bottom - margin
    val pages = mutableListOf<MutableList<PrintText>>()
    var column = 0
    var y = margin
    fun page() { pages.add(mutableListOf()); column = 0; y = margin }
    fun nextColumn() {
        if (column + 1 < options.columns) { column++; y = margin } else page()
    }
    fun newPage() { if (pages.isNotEmpty() && pages.last().isNotEmpty()) page() }
    fun space(amount: Float) { y = (y + amount).coerceAtMost(bottom) }
    fun place(rows: List<Row>) {
        val total = rows.sumOf { it.height.toDouble() }.toFloat()
        val firstRows = rows.take(2).sumOf { it.height.toDouble() }.toFloat()
        if ((total <= capacity && y + total > bottom) || (total > capacity && firstRows <= capacity && y + firstRows > bottom)) nextColumn()
        rows.forEach { row ->
            if (y + row.height > bottom) nextColumn()
            val x = margin + column * (columnWidth + gutter)
            row.parts.forEach { part -> pages.last().add(PrintText(part.text, x + part.x, y + part.y, part.size, part.bold)) }
            y += row.height
        }
    }
    fun wrapped(text: String, size: Int = options.fontSize, bold: Boolean = false): List<Row> =
        wrapPrintText(text, columnWidth) { measure(it, size, bold) }.map { Row(listOf(Part(it, size = size, bold = bold)), size * 1.45f) }
    fun metadata(song: ChordProSong): String = listOfNotNull(
        song.metadata.key?.takeIf { options.showChords }?.let { "${labels.key}: $it" },
        song.metadata.capo?.takeIf { options.showChords }?.let { "${labels.capo}: $it" },
        song.metadata.tempo?.let { "${labels.tempo}: $it" },
        song.metadata.time?.let { "${labels.time}: $it" },
    ).joinToString("   ")

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
        if (options.setlistMode == PrintSettings.SetlistMode.RUNNING_ORDER) return finishPrintDocument(width, height, pages, options, margin)
        // Song sheets follow the running order on a fresh page, including in the compact layout.
        newPage()
    }
    source.songs.forEachIndexed { index, entry ->
        if (index > 0 && options.startSongsOnNewPage) newPage()
        val title = entry.index?.let { "$it. ${entry.title}" } ?: entry.title
        val heading = wrapped(title, options.fontSize + 2, true).toMutableList()
        if (options.showMetadata) {
            entry.artist?.takeIf { it.isNotBlank() }?.let { heading += wrapped(it) }
            entry.song?.let { metadata(it).takeIf(String::isNotBlank)?.let { heading += wrapped(it) } }
        }
        // Keep the title and its metadata with at least one body row.
        val headingHeight = heading.sumOf { it.height.toDouble() }.toFloat()
        if (headingHeight + options.fontSize * 3 < capacity && y + headingHeight + options.fontSize * 3 > bottom) nextColumn()
        place(heading)
        space(options.fontSize / 2f)
        val song = entry.song
        if (song == null) place(wrapped(labels.missing)) else {
            fun rowsFor(block: ChordProBlock): List<Row> = when (block) {
                is ChordProBlock.Section -> buildList {
                    val sectionLabel = block.label ?: when (val type = block.type) {
                        SectionType.Verse -> labels.verse
                        SectionType.Chorus -> labels.chorus
                        SectionType.Bridge -> labels.bridge
                        is SectionType.Custom -> type.name.replace('_', ' ').replaceFirstChar { it.uppercase() }
                        SectionType.Paragraph -> null
                    }
                    sectionLabel?.takeUnless { block.isContinuation }?.let { addAll(wrapped(it, bold = true)) }
                    block.lines.forEachIndexed lineLoop@ { lineIndex, line ->
                        when (line) {
                            is ChordProLine.Lyrics -> {
                                val visible = line.copy(chords = line.chords.filter { if (it.isAnnotation) options.showComments else options.showChords })
                                // Hiding a chord-only line must also remove the empty lyric row beneath it.
                                if (line.chords.isNotEmpty() && visible.chords.isEmpty() && visible.text.isBlank()) return@lineLoop
                                val lyrics = if (visible.chords.isNotEmpty()) visible.padLyricsToFitChords(
                                    chordWidths = visible.chords.map { measure(it.name, options.fontSize, true) },
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
                                        if (x + chordWidth > columnWidth) { chordY += options.fontSize * 1.3f; x = 0f }
                                        wrapPrintText(chord.name, columnWidth) { measure(it, options.fontSize, true) }.forEachIndexed { i, name ->
                                            if (i > 0) { chordY += options.fontSize * 1.3f; x = 0f }
                                            parts += Part(name, x = x, y = chordY, size = options.fontSize, bold = true)
                                        }
                                        chordX = x + chordWidth + measure(" ", options.fontSize, false)
                                    }
                                    val lyricY = if (parts.isEmpty()) 0f else chordY + options.fontSize * 1.3f
                                    parts += Part(fragment, y = lyricY, size = options.fontSize)
                                    val rowHeight = lyricY + options.fontSize * 1.45f
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
                                val systems = if (ChordProTabWrapper.isTablature(run)) ChordProTabWrapper.wrap(run, characters) else ChordProTabWrapper.wrapPreformatted(run, characters)
                                systems.forEach { system ->
                                    val rows = system.flatMap { wrapped(it) }
                                    val systemHeight = rows.sumOf { it.height.toDouble() }.toFloat()
                                    if (systemHeight <= capacity) {
                                        var rowY = 0f
                                        add(Row(rows.flatMap { row -> row.parts.map { it.copy(y = rowY) }.also { rowY += row.height } }, systemHeight))
                                    } else addAll(rows)
                                }
                            }
                            is ChordProLine.Grid -> if (options.showChords) addAll(wrapped(line.tokens.joinToString(" ") { token ->
                                when (token) {
                                    is GridToken.Bar -> token.text
                                    is GridToken.Chord -> token.name
                                    is GridToken.Text -> token.text
                                    is GridToken.Repeat -> token.text
                                    GridToken.Beat -> "."
                                }
                            }, bold = true))
                            ChordProLine.Blank -> add(Row(emptyList(), options.fontSize * 0.7f))
                        }
                    }
                }
                is ChordProBlock.Comment -> if (options.showComments && (!block.isInTabOrGrid || options.showChords)) wrapped(block.text) else emptyList()
                is ChordProBlock.ChorusRecall -> block.blocks.flatMap(::rowsFor).ifEmpty { block.label?.let { wrapped(it, bold = true) }.orEmpty() }
                is ChordProBlock.Transpose, ChordProBlock.Break -> emptyList()
            }
            song.blocks.forEach { block ->
                if (block == ChordProBlock.Break) { if (y > margin) nextColumn() } else {
                    val rows = rowsFor(block)
                    if (rows.isNotEmpty()) { place(rows); space(options.fontSize * 0.65f) }
                }
            }
        }
        space(options.fontSize.toFloat())
    }
    return finishPrintDocument(width, height, pages, options, margin)
}

private data class Part(val text: String, val x: Float = 0f, val y: Float = 0f, val size: Int, val bold: Boolean = false)
private data class Row(val parts: List<Part>, val height: Float)

private fun finishPrintDocument(width: Float, height: Float, pages: List<List<PrintText>>, settings: PrintSettings, margin: Float): PrintDocument {
    val nonempty = pages.filter { it.isNotEmpty() }
    return PrintDocument(width, height, nonempty.mapIndexed { index, texts ->
        PrintPage(if (settings.showPageNumbers) texts + PrintText("${index + 1} / ${nonempty.size}", margin, height - margin, 9) else texts)
    })
}

/** Preserve every character (including spaces used as chord anchors), splitting at words where there is room. */
internal fun wrapPrintText(text: String, width: Float, measure: (String) -> Float): List<String> {
    if (text.isEmpty()) return listOf("")
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
        // Never cut a surrogate pair in two.
        if (end < text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end = if (end - start > 1) end - 1 else end + 1
        if (end < text.length) {
            val space = maxOf(text.lastIndexOf(' ', end - 1), text.lastIndexOf('\u200B', end - 1))
            if (space >= start && space - start > (end - start) / 2) end = space + 1
        }
        result += text.substring(start, end)
        start = end
    }
    return result
}
