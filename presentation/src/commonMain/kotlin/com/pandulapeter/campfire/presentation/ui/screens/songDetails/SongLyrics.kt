package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_section_bridge
import com.pandulapeter.campfire.presentation.resources.song_details_section_chorus
import com.pandulapeter.campfire.presentation.resources.song_details_section_intro
import com.pandulapeter.campfire.presentation.resources.song_details_section_outro
import com.pandulapeter.campfire.presentation.resources.song_details_section_pre_chorus
import com.pandulapeter.campfire.presentation.resources.song_details_section_solo
import com.pandulapeter.campfire.presentation.resources.song_details_section_verse
import com.pandulapeter.campfire.presentation.localization.stringResource
import kotlin.math.ceil
import kotlin.math.max

/**
 * Renders the raw song data with the chords displayed above the lyrics, aligned to the syllable they belong to.
 * When [shouldShowChords] is false, only the lyrics are rendered: the chords are dropped and lines that consisted of
 * nothing but chords (e.g. an intro) are skipped entirely.
 *
 * The song is split into sections (verse, chorus, ...) which are flowed into columns by [SongSectionsLayout].
 * Choruses are drawn on a raised card of their own so that they stand out from the surrounding sections.
 * A section is never split between columns, and sections animate to their new place when the column count
 * changes (e.g. when a window is resized).
 *
 * @param availableHeight The height the song can occupy without scrolling; the column count is picked so that it
 * fits into this if it can.
 * @param fontScale Multiplier applied to the text sizes (and to the column widths, so that larger text does not get
 * squeezed into narrow columns).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun SongLyrics(
    modifier: Modifier = Modifier,
    rawData: String,
    availableHeight: Dp = Dp.Unspecified,
    shouldShowChords: Boolean = true,
    fontScale: Float = 1f
) {
    val sections = remember(rawData, shouldShowChords) {
        parseSongLines(rawData).let { if (shouldShowChords) it else it.withoutChords() }.groupIntoSections()
    }
    val lyricsStyle = LocalTextStyle.current.merge(MaterialTheme.typography.bodyLarge).scaled(fontScale)
    val headerStyle = MaterialTheme.typography.titleSmall.scaled(fontScale)
    val chordStyle = lyricsStyle.copy(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
    LookaheadScope {
        SongSectionsLayout(
            modifier = modifier,
            minColumnWidth = MIN_COLUMN_WIDTH * fontScale,
            maxColumnWidth = MAX_COLUMN_WIDTH * fontScale,
            columnGap = COLUMN_GAP,
            sectionGap = SECTION_GAP,
            availableHeight = availableHeight
        ) {
            sections.forEach { section ->
                val sectionModifier = Modifier.animateBounds(this@LookaheadScope)
                if (section.header?.section == SongSection.CHORUS) {
                    Surface(
                        modifier = sectionModifier,
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = CARD_ELEVATION
                    ) {
                        SongSectionContent(
                            modifier = Modifier.padding(CARD_PADDING),
                            section = section,
                            isOnCard = true,
                            headerStyle = headerStyle,
                            lyricsStyle = lyricsStyle,
                            chordStyle = chordStyle
                        )
                    }
                } else {
                    // The same horizontal padding as inside a card, so that every section's text starts at the
                    // same x position whether it is carded or not.
                    SongSectionContent(
                        modifier = sectionModifier.padding(horizontal = CARD_PADDING),
                        section = section,
                        isOnCard = false,
                        headerStyle = headerStyle,
                        lyricsStyle = lyricsStyle,
                        chordStyle = chordStyle
                    )
                }
            }
        }
    }
}

/**
 * The optional header of a section followed by its lines, with the chords drawn above the lyrics.
 *
 * The header is the same raised pill the song list uses for its sticky headers, so that the sections are told apart
 * at a glance. A chorus already stands out through its card, where another raised surface would only add noise, so
 * there ([isOnCard]) the header stays a plain label. The pill hangs into the section's left padding, so that its
 * text starts on the same keyline as the lyrics below it.
 */
@Composable
private fun SongSectionContent(
    modifier: Modifier = Modifier,
    section: LyricsSection,
    isOnCard: Boolean,
    headerStyle: TextStyle,
    lyricsStyle: TextStyle,
    chordStyle: TextStyle
) = Column(
    modifier = modifier
) {
    section.header?.let { header ->
        if (isOnCard) {
            Text(
                modifier = Modifier.fillMaxWidth().padding(bottom = HEADER_GAP),
                text = header.title(),
                style = headerStyle,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Surface(
                modifier = Modifier.offset(x = -HEADER_HORIZONTAL_PADDING).padding(bottom = HEADER_GAP),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = HEADER_ELEVATION
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = HEADER_HORIZONTAL_PADDING, vertical = HEADER_VERTICAL_PADDING),
                    text = header.title(),
                    style = headerStyle,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
    section.lines.forEach { line ->
        if (line.chords.isEmpty()) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = line.lyrics,
                style = lyricsStyle,
                color = MaterialTheme.colorScheme.onSurface
            )
        } else {
            SongLineWithChords(
                line = line,
                lyricsStyle = lyricsStyle,
                chordStyle = chordStyle
            )
        }
    }
}

private fun TextStyle.scaled(scale: Float) = copy(
    fontSize = if (fontSize.isSpecified) fontSize * scale else fontSize,
    lineHeight = if (lineHeight.isSpecified) lineHeight * scale else lineHeight
)

/**
 * Flows its children (the song sections) into columns.
 *
 * The columns are made as wide (and therefore as few) as possible while the whole song still fits into
 * [availableHeight], so that the lyrics wrap as little as they can and the vertical space is actually used: a song
 * that needs three columns is not squeezed into five just because the window is wide enough for five. Songs that do
 * not fit no matter what get as many columns as the width allows. Column widths stay between [minColumnWidth] and
 * [maxColumnWidth] and the whole block is centered, so a short song does not end up as one screen-wide column of
 * short lines.
 *
 * The candidate column counts are evaluated with the sections' intrinsic heights (they are only measured once, with
 * the width that won), starting from a single column and jumping straight to the smallest count that could possibly
 * fit whenever the current one does not.
 */
@Composable
private fun SongSectionsLayout(
    modifier: Modifier = Modifier,
    minColumnWidth: Dp,
    maxColumnWidth: Dp,
    columnGap: Dp,
    sectionGap: Dp,
    availableHeight: Dp,
    content: @Composable () -> Unit
) = Layout(
    modifier = modifier,
    content = content
) { measurables, constraints ->
    val width = constraints.maxWidth
    val columnGapPx = columnGap.roundToPx()
    val sectionGapPx = sectionGap.roundToPx()
    val maxColumnWidthPx = maxColumnWidth.roundToPx()
    val availableHeightPx = if (availableHeight.isSpecified) availableHeight.roundToPx() else 0
    val maxColumnCount = ((width + columnGapPx) / (minColumnWidth.roundToPx() + columnGapPx)).coerceIn(1, maxOf(1, measurables.size))
    fun columnWidthFor(columnCount: Int) = ((width - columnGapPx * (columnCount - 1)) / columnCount).coerceIn(0, maxColumnWidthPx)

    var columnCount = maxColumnCount
    if (availableHeightPx > 0) {
        var candidate = 1
        while (candidate < maxColumnCount) {
            val heights = measurables.map { it.maxIntrinsicHeight(columnWidthFor(candidate)) }
            if (heights.balanceIntoColumns(candidate, sectionGapPx).height <= availableHeightPx) break
            // Even a perfectly even split needs this many columns, so there is no point in trying the ones in between.
            val totalHeight = heights.sum() + sectionGapPx * (heights.size - 1).coerceAtLeast(0)
            candidate = maxOf(candidate + 1, ceil(totalHeight.toDouble() / availableHeightPx).toInt())
        }
        columnCount = candidate.coerceAtMost(maxColumnCount)
    }

    val columnWidth = columnWidthFor(columnCount)
    val placeables = measurables.map { it.measure(Constraints(minWidth = columnWidth, maxWidth = columnWidth, maxHeight = constraints.maxHeight)) }
    val columns = placeables.map { it.height }.balanceIntoColumns(columnCount, sectionGapPx)
    val contentWidth = columnWidth * columnCount + columnGapPx * (columnCount - 1)
    val startX = ((width - contentWidth) / 2).coerceAtLeast(0)
    var column = 0
    var columnHeight = 0
    var height = 0
    val positions = placeables.mapIndexed { index, placeable ->
        if (column + 1 < columnCount && index == columns.starts[column + 1]) {
            column++
            columnHeight = 0
        }
        val position = IntOffset(x = startX + column * (columnWidth + columnGapPx), y = columnHeight)
        columnHeight += placeable.height + sectionGapPx
        height = max(height, columnHeight - sectionGapPx)
        position
    }
    layout(width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
        placeables.forEachIndexed { index, placeable -> placeable.place(positions[index]) }
    }
}

/**
 * The index of the first section of every column, and the height of the tallest column.
 */
private class SongColumns(
    val starts: IntArray,
    val height: Int
)

/**
 * Distributes the sections (given by their heights, in order) into [columnCount] columns so that the columns end up
 * as close to equally tall as possible without ever splitting a section.
 *
 * A greedy fill would leave every column a little short of the ideal height and dump all of the accumulated slack on
 * the last one, so instead this is a dynamic program over the split points that minimizes the squared deviation of
 * the columns from the ideal height. Every column gets at least one section, so the columns always span the full
 * width of the layout.
 */
private fun List<Int>.balanceIntoColumns(columnCount: Int, sectionGap: Int): SongColumns {
    if (isEmpty()) return SongColumns(starts = IntArray(columnCount), height = 0)
    val prefixHeights = IntArray(size + 1)
    forEachIndexed { index, height -> prefixHeights[index + 1] = prefixHeights[index] + height + sectionGap }
    fun heightOf(from: Int, until: Int) = prefixHeights[until] - prefixHeights[from] - sectionGap
    val idealHeight = heightOf(0, size).toDouble() / columnCount
    val splits = Array(columnCount) { IntArray(size + 1) }
    // costs[i] holds the cost of the best distribution of the first i sections into the columns processed so far.
    var costs = DoubleArray(size + 1) { Double.MAX_VALUE }
    costs[0] = 0.0
    for (column in 0 until columnCount) {
        val nextCosts = DoubleArray(size + 1) { Double.MAX_VALUE }
        // The first `column` sections are taken by the previous columns and the last ones are still needed by the
        // remaining columns, since no column may be left empty.
        for (until in column + 1..size - (columnCount - column - 1)) {
            for (from in column until until) {
                if (costs[from] == Double.MAX_VALUE) continue
                val deviation = heightOf(from, until) - idealHeight
                val cost = costs[from] + deviation * deviation
                if (cost < nextCosts[until]) {
                    nextCosts[until] = cost
                    splits[column][until] = from
                }
            }
        }
        costs = nextCosts
    }
    val starts = IntArray(columnCount)
    var until = size
    for (column in columnCount - 1 downTo 0) {
        starts[column] = splits[column][until]
        until = starts[column]
    }
    var height = 0
    for (column in 0 until columnCount) {
        height = max(height, heightOf(starts[column], if (column == columnCount - 1) size else starts[column + 1]))
    }
    return SongColumns(starts = starts, height = height)
}

/**
 * A section of the song: an optional header followed by the lines that belong to it.
 */
private data class LyricsSection(
    val header: SongLine.SectionHeader?,
    val lines: List<SongLine.Lyrics>
)

private fun List<SongLine>.groupIntoSections(): List<LyricsSection> {
    val sections = mutableListOf<LyricsSection>()
    var header: SongLine.SectionHeader? = null
    var lines = mutableListOf<SongLine.Lyrics>()
    fun flush() {
        if (header != null || lines.any { it.lyrics.isNotBlank() || it.chords.isNotEmpty() }) {
            sections += LyricsSection(header, lines.dropLastWhile { it.lyrics.isBlank() && it.chords.isEmpty() })
        }
        header = null
        lines = mutableListOf()
    }
    forEach { line ->
        when (line) {
            is SongLine.SectionHeader -> {
                flush()
                header = line
            }

            is SongLine.Lyrics -> lines += line
        }
    }
    flush()
    return sections
}

private fun List<SongLine>.withoutChords() = mapNotNull { line ->
    when (line) {
        is SongLine.SectionHeader -> line
        is SongLine.Lyrics -> when {
            line.chords.isEmpty() -> line
            line.lyrics.isBlank() -> null
            else -> line.copy(chords = emptyList())
        }
    }
}

@Composable
private fun SongLine.SectionHeader.title(): String {
    val localizedName = when (section) {
        SongSection.INTRO -> stringResource(Res.string.song_details_section_intro)
        SongSection.VERSE -> stringResource(Res.string.song_details_section_verse)
        SongSection.PRE_CHORUS -> stringResource(Res.string.song_details_section_pre_chorus)
        SongSection.CHORUS -> stringResource(Res.string.song_details_section_chorus)
        SongSection.BRIDGE -> stringResource(Res.string.song_details_section_bridge)
        SongSection.SOLO -> stringResource(Res.string.song_details_section_solo)
        SongSection.OUTRO -> stringResource(Res.string.song_details_section_outro)
        null -> name
    }
    return if (suffix.isEmpty()) localizedName else "$localizedName $suffix"
}

/**
 * The lyrics are laid out with a line height that leaves room above every (wrapped) line for the chords,
 * which are then drawn at the horizontal position of the character they are attached to.
 * Whenever a chord is wider than the piece of lyrics beneath it, that piece is padded with non-breaking spaces so
 * that consecutive chords never overlap and the line wraps before the chords would run off the edge.
 */
@Composable
private fun SongLineWithChords(
    line: SongLine.Lyrics,
    lyricsStyle: TextStyle,
    chordStyle: TextStyle
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val chordLayouts = remember(line, chordStyle, textMeasurer) {
        line.chords.map { textMeasurer.measure(AnnotatedString(it.name), chordStyle) }
    }
    val paddedLine = remember(line, lyricsStyle, chordLayouts, textMeasurer, density) {
        line.padLyricsToFitChords(
            chordWidths = chordLayouts.map { it.size.width.toFloat() },
            gap = with(density) { CHORD_GAP.toPx() },
            measureWidth = { textMeasurer.measure(AnnotatedString(it), lyricsStyle).size.width.toFloat() }
        )
    }
    val lyricsLineHeight = remember(lyricsStyle, textMeasurer) {
        textMeasurer.measure(AnnotatedString(LINE_HEIGHT_SAMPLE), lyricsStyle).size.height
    }
    val chordLineHeight = chordLayouts.maxOf { it.size.height }
    // Lines without any lyrics (e.g. an intro) only need to be as tall as the chords themselves.
    val lineHeight = with(density) { (if (line.lyrics.isBlank()) chordLineHeight else chordLineHeight + lyricsLineHeight).toSp() }
    var lyricsLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val layout = lyricsLayout ?: return@drawBehind
                val textLength = layout.layoutInput.text.length
                val gap = CHORD_GAP.toPx()
                var previousLineIndex = -1
                var previousChordEnd = 0f
                paddedLine.chords.forEachIndexed { index, chord ->
                    val chordLayout = chordLayouts[index]
                    val offset = chord.position.coerceIn(0, textLength)
                    val lineIndex = layout.getLineForOffset(offset)
                    if (lineIndex != previousLineIndex) {
                        previousLineIndex = lineIndex
                        previousChordEnd = 0f
                    }
                    val maxX = max(0f, size.width - chordLayout.size.width)
                    val x = max(layout.getHorizontalPosition(offset, usePrimaryDirection = true), previousChordEnd).coerceIn(0f, maxX)
                    drawText(
                        textLayoutResult = chordLayout,
                        topLeft = Offset(x, layout.getLineTop(lineIndex))
                    )
                    previousChordEnd = x + chordLayout.size.width + gap
                }
            },
        text = paddedLine.lyrics,
        style = lyricsStyle.copy(
            lineHeight = lineHeight,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Bottom,
                trim = LineHeightStyle.Trim.None
            )
        ),
        color = MaterialTheme.colorScheme.onSurface,
        onTextLayout = { lyricsLayout = it }
    )
}

/**
 * Returns a copy of the line where every piece of lyrics that sits under a chord is at least as wide as the chord
 * (plus [gap]), by appending non-breaking spaces to it. Chord positions are updated to point into the padded lyrics.
 */
private fun SongLine.Lyrics.padLyricsToFitChords(
    chordWidths: List<Float>,
    gap: Float,
    measureWidth: (String) -> Float
): SongLine.Lyrics {
    val paddingWidth = measureWidth(PADDING.toString())
    val paddedLyrics = StringBuilder(lyrics.substring(0, chords.first().position))
    val paddedChords = chords.mapIndexed { index, chord ->
        val fragment = lyrics.substring(chord.position, chords.getOrNull(index + 1)?.position ?: lyrics.length)
        val paddedChord = chord.copy(position = paddedLyrics.length)
        paddedLyrics.append(fragment)
        val missingWidth = chordWidths[index] + gap - measureWidth(fragment)
        if (missingWidth > 0 && paddingWidth > 0) {
            repeat(ceil(missingWidth / paddingWidth).toInt()) { paddedLyrics.append(PADDING) }
        }
        paddedChord
    }
    return SongLine.Lyrics(lyrics = paddedLyrics.toString(), chords = paddedChords)
}

internal sealed class SongLine {

    /**
     * A "{c: Chorus 2}" style section marker, split into the [name] of the section ("Chorus"), the [section] it was
     * recognized as (null for unknown names) and whatever followed the name ("2").
     */
    data class SectionHeader(
        val name: String,
        val suffix: String,
        val section: SongSection?
    ) : SongLine()

    data class Lyrics(
        val lyrics: String,
        val chords: List<Chord>
    ) : SongLine() {

        data class Chord(
            val position: Int, // Index of the character in [lyrics] the chord is placed above.
            val name: String
        )
    }
}

internal enum class SongSection(val rawName: String) {
    INTRO("Intro"),
    VERSE("Verse"),
    PRE_CHORUS("Pre-Chorus"),
    CHORUS("Chorus"),
    BRIDGE("Bridge"),
    SOLO("Solo"),
    OUTRO("Outro")
}

/**
 * Splits the raw song data into lines. Section markers (e.g. "{c: Verse 1}") become [SongLine.SectionHeader]s, every
 * other line becomes [SongLine.Lyrics] with the inline chord markers (e.g. "[Am]") removed from the lyrics and their
 * positions remembered.
 */
internal fun parseSongLines(rawData: String): List<SongLine> = rawData.lines().map { rawLine ->
    sectionHeaderRegex.matchEntire(rawLine.trim())?.let { match -> parseSectionHeader(match.groupValues[1].trim()) } ?: parseLyrics(rawLine)
}

private fun parseSectionHeader(title: String): SongLine.SectionHeader {
    val name = title.substringBefore(' ')
    return SongLine.SectionHeader(
        name = name,
        suffix = title.substringAfter(' ', missingDelimiterValue = "").trim(),
        section = SongSection.entries.firstOrNull { it.rawName.equals(name, ignoreCase = true) }
    )
}

private fun parseLyrics(rawLine: String): SongLine.Lyrics {
    val lyrics = StringBuilder()
    val chords = mutableListOf<SongLine.Lyrics.Chord>()
    var consumedUntil = 0
    chordRegex.findAll(rawLine).forEach { match ->
        lyrics.append(rawLine, consumedUntil, match.range.first)
        match.groupValues[1].trim().takeIf { it.isNotEmpty() }?.let { chordName ->
            chords += SongLine.Lyrics.Chord(position = lyrics.length, name = chordName)
        }
        consumedUntil = match.range.last + 1
    }
    lyrics.append(rawLine, consumedUntil, rawLine.length)
    return SongLine.Lyrics(lyrics = lyrics.toString(), chords = chords)
}

private val sectionHeaderRegex = Regex("\\{c:(.*)\\}")
private val chordRegex = Regex("\\[(.*?)\\]")
private val CHORD_GAP = 4.dp
private val CARD_PADDING = 12.dp
private val CARD_ELEVATION = 1.dp
private val HEADER_GAP = 8.dp
private val HEADER_ELEVATION = 2.dp
private val HEADER_HORIZONTAL_PADDING = 12.dp
private val HEADER_VERTICAL_PADDING = 6.dp
private val MIN_COLUMN_WIDTH = 384.dp
private val MAX_COLUMN_WIDTH = 560.dp
private val COLUMN_GAP = 32.dp
private val SECTION_GAP = 20.dp
private const val LINE_HEIGHT_SAMPLE = "X"
private const val PADDING = '\u00A0' // Non-breaking space, so that the padding never gets trimmed or wrapped.
