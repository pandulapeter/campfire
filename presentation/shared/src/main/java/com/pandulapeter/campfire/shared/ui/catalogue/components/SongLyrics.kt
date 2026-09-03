package com.pandulapeter.campfire.shared.ui.catalogue.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.pandulapeter.campfire.shared.ui.catalogue.theme.CampfireColors
import kotlin.math.ceil
import kotlin.math.max

/**
 * Renders the raw song data with the chords displayed above the lyrics, aligned to the syllable they belong to.
 */
@Composable
internal fun SongLyrics(
    modifier: Modifier = Modifier,
    rawData: String
) {
    val lines = remember(rawData) { parseSongLines(rawData) }
    val lyricsStyle = LocalTextStyle.current
    val chordStyle = lyricsStyle.copy(
        color = CampfireColors.colorCampfireOrange,
        fontWeight = FontWeight.Bold
    )
    Column(
        modifier = modifier
    ) {
        lines.forEach { line ->
            if (line.chords.isEmpty()) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = line.lyrics,
                    style = lyricsStyle,
                    color = MaterialTheme.colors.onSurface
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

/**
 * The lyrics are laid out with a line height that leaves room above every (wrapped) line for the chords,
 * which are then drawn at the horizontal position of the character they are attached to.
 * Whenever a chord is wider than the piece of lyrics beneath it, that piece is padded with non-breaking spaces so
 * that consecutive chords never overlap and the line wraps before the chords would run off the edge.
 */
@Composable
private fun SongLineWithChords(
    line: SongLine,
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
        color = MaterialTheme.colors.onSurface,
        onTextLayout = { lyricsLayout = it }
    )
}

/**
 * Returns a copy of the line where every piece of lyrics that sits under a chord is at least as wide as the chord
 * (plus [gap]), by appending non-breaking spaces to it. Chord positions are updated to point into the padded lyrics.
 */
private fun SongLine.padLyricsToFitChords(
    chordWidths: List<Float>,
    gap: Float,
    measureWidth: (String) -> Float
): SongLine {
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
    return SongLine(lyrics = paddedLyrics.toString(), chords = paddedChords)
}

internal data class SongLine(
    val lyrics: String,
    val chords: List<Chord>
) {
    data class Chord(
        val position: Int, // Index of the character in [lyrics] the chord is placed above.
        val name: String
    )
}

/**
 * Splits the raw song data into lines, removing the inline chord markers (e.g. "[Am]") from the lyrics
 * and remembering the position where each chord was.
 */
internal fun parseSongLines(rawData: String): List<SongLine> = rawData.lines().map { rawLine ->
    val lyrics = StringBuilder()
    val chords = mutableListOf<SongLine.Chord>()
    var consumedUntil = 0
    chordRegex.findAll(rawLine).forEach { match ->
        lyrics.append(rawLine, consumedUntil, match.range.first)
        match.groupValues[1].trim().takeIf { it.isNotEmpty() }?.let { chordName ->
            chords += SongLine.Chord(position = lyrics.length, name = chordName)
        }
        consumedUntil = match.range.last + 1
    }
    lyrics.append(rawLine, consumedUntil, rawLine.length)
    SongLine(lyrics = lyrics.toString(), chords = chords)
}

private val chordRegex = Regex("\\[(.*?)]")
private val CHORD_GAP = 8.dp
private const val LINE_HEIGHT_SAMPLE = "X"
private const val PADDING = '\u00A0' // Non-breaking space, so that the padding never gets trimmed or wrapped.
