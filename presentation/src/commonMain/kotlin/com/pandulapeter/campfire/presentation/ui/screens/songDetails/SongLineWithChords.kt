/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * The lyrics are laid out with a line height that leaves room above every (wrapped) line for the chords,
 * which are then drawn at the horizontal position of the character they are attached to.
 * Whenever a chord is wider than the piece of lyrics beneath it, that piece is padded with non-breaking spaces so
 * that consecutive chords never overlap and the line wraps before the chords would run off the edge.
 * The chords are kept apart in the direction the line runs: a line whose content is right to left is drawn from the
 * right edge leftwards, and a chord then hangs to the left of the character it belongs to.
 */
@Composable
internal fun SongLineWithChords(
    line: ChordProLine.Lyrics,
    lyricsStyle: TextStyle,
    textMeasurements: SongTextMeasurements,
) {
    val density = LocalDensity.current
    val chordColor = LocalSecondAccentColor.current
    val annotationColor = MaterialTheme.colorScheme.onSurfaceVariant
    val chordLayouts = remember(line, textMeasurements) { line.chords.map(textMeasurements::chordLayout) }
    val paddedLine = remember(line, textMeasurements, density) {
        line.padLyricsToFitChords(
            chordWidths = chordLayouts.map { it.size.width.toFloat() },
            gap = with(density) { CHORD_GAP.toPx() },
            paddingWidth = textMeasurements.paddingWidth,
            measureWidth = textMeasurements::fragmentWidth,
        )
    }
    val chordLineHeight = chordLayouts.maxOf { it.size.height }
    // Lines without any lyrics (e.g. an intro) only need to be as tall as the chords themselves. The height is in pixels
    // and is handed over as a multiple of the font size rather than in sp: under Android's non-linear font scaling a
    // line height in sp is scaled by the same factor as the font size rather than by its own, so a round trip from
    // pixels through sp comes back taller, and the lyrics, which sit at the bottom of the line, drift away from their
    // chords.
    val lineHeight = with(density) {
        (if (line.text.isBlank()) chordLineHeight else chordLineHeight + textMeasurements.lyricsLineHeight).toFloat() / lyricsStyle.fontSize.toPx()
    }.em
    // The chords are drawn with the leading of their style above and below them, and the lyrics start right under that,
    // so their own leading goes below them instead: a chord then sits as close to the words it is played over as the
    // leading of one line, and the next line's chords are three times that further down. Sat on the very bottom of the
    // line, the lyrics would be exactly the other way around, closer to the chords of the next line than to their own.
    val lyricsLeading = (textMeasurements.lyricsLineHeight - textMeasurements.lyricsTextHeight).coerceAtLeast(0)
    val lyricsAlignment = LineHeightStyle.Alignment(topRatio = chordLineHeight.toFloat() / (chordLineHeight + lyricsLeading))
    var lyricsLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // The chords are drawn rather than composed, so a screen reader would read the padded lyrics alone. It is given
    // the line the way ChordPro writes it instead, each chord in brackets where it falls.
    val description = remember(line) { line.withChordsInline() }
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description }
            .drawBehind {
                val layout = lyricsLayout ?: return@drawBehind
                val textLength = layout.layoutInput.text.length
                val gap = CHORD_GAP.toPx()
                // Each chord is drawn at its row's top, inside the line's height, so the clip only cuts what reaches past
                // the end edge: a chord or an annotation wider than the line, which is clipped rather than wrapped, since a
                // second line of it would land on the lyrics under it. The content description keeps its whole text.
                clipRect {
                    var previousLineIndex = -1
                    // The edge the next chord of this line must not cross: where it has to start on a line that runs to
                    // the right, where it has to end on one that runs to the left. One variable rather than two, since
                    // there is one rule - a chord never sits on the chord before it.
                    var previousChordEdge = 0f
                    paddedLine.chords.forEachIndexed { index, chord ->
                        val chordLayout = chordLayouts[index]
                        val offset = chord.position.coerceIn(0, textLength)
                        val lineIndex = layout.getLineForOffset(offset)
                        // A right to left paragraph is laid out from the right edge leftwards, so x falls as the offset
                        // grows and the chords have to be kept apart the other way. The paragraph's direction rather than
                        // that of the run the chord lands in, since "further along the line" is the paragraph's to say: two
                        // chords of one line answering differently would be drawn on top of each other.
                        val isRightToLeft = layout.getParagraphDirection(offset) == ResolvedTextDirection.Rtl
                        if (lineIndex != previousLineIndex) {
                            previousLineIndex = lineIndex
                            previousChordEdge = if (isRightToLeft) size.width else 0f
                        }
                        val chordWidth = chordLayout.size.width
                        val maxX = max(0f, size.width - chordWidth)
                        val position = layout.getHorizontalPosition(offset, usePrimaryDirection = true)
                        // Kept inside the line where that leaves the chord before it alone; where it cannot, the chord stays
                        // where it belongs and what reaches past the edge is clipped, since a chord drawn over another one
                        // cannot be read at all.
                        val x = if (isRightToLeft) {
                            // The chord hangs to the left of its character, the way it hangs to the right of it in a line
                            // that runs the other way, so the position is its right edge.
                            min(previousChordEdge - chordWidth, max(min(position, previousChordEdge) - chordWidth, 0f))
                        } else {
                            max(previousChordEdge, min(max(position, previousChordEdge), maxX))
                        }
                        drawText(
                            textLayoutResult = chordLayout,
                            color = if (chord.isAnnotation) annotationColor else chordColor,
                            topLeft = Offset(x, layout.getLineTop(lineIndex)),
                        )
                        previousChordEdge = if (isRightToLeft) x - gap else x + chordWidth + gap
                    }
                }
            },
        text = paddedLine.text,
        style = lyricsStyle.copy(
            lineHeight = lineHeight,
            lineHeightStyle = LineHeightStyle(
                alignment = lyricsAlignment,
                trim = LineHeightStyle.Trim.None,
            ),
        ),
        color = MaterialTheme.colorScheme.onSurface,
        onTextLayout = { lyricsLayout = it },
    )
}

/** The line with each chord written into it in brackets, where it sits: "[Am]There is a [C]house". */
private fun ChordProLine.Lyrics.withChordsInline() = buildString {
    var start = 0
    chords.sortedBy { it.position }.forEach { chord ->
        val position = chord.position.coerceIn(start, text.length)
        append(text, start, position)
        append('[').append(chord.name).append(']')
        start = position
    }
    append(text, start, text.length)
}

/**
 * Returns a copy of the line where every piece of lyrics that sits under a chord is at least as wide as the chord
 * (plus [gap]), by appending non-breaking spaces, each [paddingWidth] wide, to it. Chord positions are updated to point
 * into the padded lyrics.
 *
 * The padded line may only wrap between two chords, and only where the original text has a word boundary: the
 * whitespace at either end of a padded piece is made non-breaking too, so that a chord, the space it sits on and the
 * padding that makes room for it never end up on two rows, and a [BREAK_OPPORTUNITY] follows every piece that ends at a
 * word boundary. Without it a chord-only line would be one unbreakable run the layout can only break at an arbitrary
 * character, leaving the chord at the end of a row with no room for it; with it every chord starts whatever row it is
 * sent to. The inner spaces of a piece stay ordinary, so a long piece of lyrics under one chord still wraps between its
 * words.
 */
internal fun ChordProLine.Lyrics.padLyricsToFitChords(
    chordWidths: List<Float>,
    gap: Float,
    paddingWidth: Float,
    measureWidth: (String) -> Float,
): ChordProLine.Lyrics {
    val paddedLyrics = StringBuilder(text.substring(0, chords.first().position))
    val paddedChords = chords.mapIndexed { index, chord ->
        val end = chords.getOrNull(index + 1)?.position ?: text.length
        val fragment = text.substring(chord.position, end)
        val paddedChord = chord.copy(position = paddedLyrics.length)
        val missingWidth = chordWidths[index] + gap - measureWidth(fragment)
        if (missingWidth > 0 && paddingWidth > 0) {
            val innerStart = fragment.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) fragment.length else it }
            val innerEnd = fragment.indexOfLast { !it.isWhitespace() } + 1
            repeat(innerStart) { paddedLyrics.append(PADDING) }
            if (innerEnd > innerStart) paddedLyrics.append(fragment, innerStart, innerEnd)
            repeat(fragment.length - maxOf(innerStart, innerEnd)) { paddedLyrics.append(PADDING) }
            repeat(ceil(missingWidth / paddingWidth).toInt()) { paddedLyrics.append(PADDING) }
        } else {
            paddedLyrics.append(fragment)
        }
        val isWordBoundary = end == 0 || text.getOrNull(end - 1)?.isWhitespace() == true || text.getOrNull(end)?.isWhitespace() == true
        if (index < chords.lastIndex && isWordBoundary) paddedLyrics.append(BREAK_OPPORTUNITY)
        paddedChord
    }
    return ChordProLine.Lyrics(text = paddedLyrics.toString(), chords = paddedChords)
}

private val CHORD_GAP = 4.dp

/**
 * A zero-width space, which is where a line padded to fit its chords may wrap: default-ignorable, so no font draws
 * anything for it, and a break opportunity after it to every line breaker (class ZW).
 */
private const val BREAK_OPPORTUNITY = '\u200B'
