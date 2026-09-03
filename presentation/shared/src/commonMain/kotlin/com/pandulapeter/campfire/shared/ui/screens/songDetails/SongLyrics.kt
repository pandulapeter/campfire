package com.pandulapeter.campfire.shared.ui.screens.songDetails

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
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
import com.pandulapeter.campfire.shared.resources.Res
import com.pandulapeter.campfire.shared.resources.song_details_section_bridge
import com.pandulapeter.campfire.shared.resources.song_details_section_chorus
import com.pandulapeter.campfire.shared.resources.song_details_section_intro
import com.pandulapeter.campfire.shared.resources.song_details_section_outro
import com.pandulapeter.campfire.shared.resources.song_details_section_pre_chorus
import com.pandulapeter.campfire.shared.resources.song_details_section_solo
import com.pandulapeter.campfire.shared.resources.song_details_section_verse
import com.pandulapeter.campfire.shared.localization.stringResource
import kotlin.math.ceil
import kotlin.math.max

/**
 * Renders the raw song data with the chords displayed above the lyrics, aligned to the syllable they belong to.
 * When [shouldShowChords] is false, only the lyrics are rendered: the chords are dropped and lines that consisted of
 * nothing but chords (e.g. an intro) are skipped entirely.
 *
 * The song is split into sections (verse, chorus, ...) which are flowed into as many columns as the available width
 * allows. A section is never split between columns, and sections animate to their new place when the column count
 * changes (e.g. when a window is resized).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun SongLyrics(
    modifier: Modifier = Modifier,
    rawData: String,
    shouldShowChords: Boolean = true
) {
    val sections = remember(rawData, shouldShowChords) {
        parseSongLines(rawData).let { if (shouldShowChords) it else it.withoutChords() }.groupIntoSections()
    }
    val lyricsStyle = LocalTextStyle.current.merge(MaterialTheme.typography.bodyLarge)
    val chordStyle = lyricsStyle.copy(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
    LookaheadScope {
        SongSectionsLayout(
            modifier = modifier,
            minColumnWidth = MIN_COLUMN_WIDTH,
            columnGap = COLUMN_GAP,
            sectionGap = SECTION_GAP
        ) {
            sections.forEach { section ->
                Column(
                    modifier = Modifier.animateBounds(this@LookaheadScope)
                ) {
                    section.header?.let { header ->
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = header.title(),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
            }
        }
    }
}

/**
 * Flows its children (the song sections) into columns. The column count is derived from the available width and
 * [minColumnWidth]; the sections are then distributed in order so that the columns end up roughly equally tall
 * without ever splitting a section.
 */
@Composable
private fun SongSectionsLayout(
    modifier: Modifier = Modifier,
    minColumnWidth: Dp,
    columnGap: Dp,
    sectionGap: Dp,
    content: @Composable () -> Unit
) = Layout(
    modifier = modifier,
    content = content
) { measurables, constraints ->
    val width = constraints.maxWidth
    val columnGapPx = columnGap.roundToPx()
    val sectionGapPx = sectionGap.roundToPx()
    val columnCount = ((width + columnGapPx) / (minColumnWidth.roundToPx() + columnGapPx)).coerceIn(1, maxOf(1, measurables.size))
    val columnWidth = ((width - columnGapPx * (columnCount - 1)) / columnCount).coerceAtLeast(0)
    val placeables = measurables.map { it.measure(Constraints(minWidth = columnWidth, maxWidth = columnWidth, maxHeight = constraints.maxHeight)) }

    // Fill the columns in order, starting a new one whenever the current column would grow past the balanced target.
    val totalHeight = placeables.sumOf { it.height } + sectionGapPx * (placeables.size - 1).coerceAtLeast(0)
    val targetHeight = (totalHeight + columnCount - 1) / columnCount
    val columnHeights = IntArray(columnCount)
    var column = 0
    val positions = placeables.map { placeable ->
        val currentHeight = columnHeights[column]
        if (currentHeight > 0 && column < columnCount - 1 && currentHeight + sectionGapPx + placeable.height > targetHeight) {
            column++
        }
        val y = if (columnHeights[column] == 0) 0 else columnHeights[column] + sectionGapPx
        columnHeights[column] = y + placeable.height
        IntOffset(x = column * (columnWidth + columnGapPx), y = y)
    }
    val height = (columnHeights.maxOrNull() ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(width, height) {
        placeables.forEachIndexed { index, placeable -> placeable.place(positions[index]) }
    }
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
private val MIN_COLUMN_WIDTH = 360.dp
private val COLUMN_GAP = 32.dp
private val SECTION_GAP = 16.dp
private const val LINE_HEIGHT_SAMPLE = "X"
private const val PADDING = '\u00A0' // Non-breaking space, so that the padding never gets trimmed or wrapped.
