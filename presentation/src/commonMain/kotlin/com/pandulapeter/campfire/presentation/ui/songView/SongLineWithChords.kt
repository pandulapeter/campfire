/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import com.pandulapeter.campfire.presentation.ui.songView.model.isBlank
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
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.pandulapeter.campfire.chordpro.language.WritingDirection
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.presentation.ui.songLayout.padLyricsToFitChords
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor

/**
 * The lyrics are laid out with a line height that leaves room above every (wrapped) line for the chords,
 * which are then drawn at the horizontal position of the character they are attached to.
 * Whenever a chord is wider than the piece of lyrics beneath it, that piece is padded with non-breaking spaces so
 * that consecutive chords never overlap and the line wraps before the chords would run off the edge.
 * The chords are kept apart in the direction the line runs: a line whose content is right to left is drawn from the
 * right edge leftwards. Each chord hangs from its character in the direction of the run it lands in ([chordLeft]), so in
 * a right-to-left line a chord hangs to the left of a Hebrew letter and to the right of a Latin one.
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
    val change = LocalSongChange.current
    val chordLayouts = remember(line, textMeasurements) { line.chords.map(textMeasurements::chordLayout) }
    val paragraphDirection = if (lyricsStyle.textDirection == TextDirection.Rtl) WritingDirection.RIGHT_TO_LEFT else WritingDirection.LEFT_TO_RIGHT
    val paddedLine = remember(line, textMeasurements, density, paragraphDirection) {
        line.padLyricsToFitChords(
            chordWidths = chordLayouts.map { it.size.width.toFloat() },
            gap = with(density) { CHORD_GAP.toPx() },
            paddingWidth = textMeasurements.paddingWidth,
            measureWidth = textMeasurements::fragmentWidth,
            paragraphDirection = paragraphDirection,
        )
    }
    val paddedText = remember(paddedLine) { paddedLine.formattedText() }
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
                // Read here rather than in composition, so that the chords fading with a change draw this line again
                // rather than compose it.
                val chordAlpha = change.chordAlpha()
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
                        // grows and the chords have to be kept apart the other way.
                        val isRightToLeft = layout.getParagraphDirection(offset) == ResolvedTextDirection.Rtl
                        if (lineIndex != previousLineIndex) {
                            previousLineIndex = lineIndex
                            previousChordEdge = if (isRightToLeft) size.width else 0f
                        }
                        val chordWidth = chordLayout.size.width
                        val isRunRightToLeft = if (offset < textLength) {
                            layout.getBidiRunDirection(offset) == ResolvedTextDirection.Rtl
                        } else {
                            isRightToLeft
                        }
                        // Where the run reads the paragraph's way, the caret position is the character's starting edge. Where
                        // it reads the other way, the caret at a run boundary stands with the run before, so the character's
                        // own box says where it starts.
                        val anchor = if (isRunRightToLeft == isRightToLeft) {
                            layout.getHorizontalPosition(offset, usePrimaryDirection = true)
                        } else {
                            layout.getBoundingBox(offset).let { if (isRunRightToLeft) it.right else it.left }
                        }
                        val x = chordLeft(
                            anchor = anchor,
                            chordWidth = chordWidth.toFloat(),
                            rowWidth = size.width,
                            previousChordEdge = previousChordEdge,
                            isRunRightToLeft = isRunRightToLeft,
                            isLineRightToLeft = isRightToLeft,
                        )
                        drawText(
                            textLayoutResult = chordLayout,
                            color = if (chord.isAnnotation) annotationColor else chordColor,
                            topLeft = Offset(x, layout.getLineTop(lineIndex)),
                            alpha = chordAlpha,
                        )
                        previousChordEdge = if (isRightToLeft) x - gap else x + chordWidth + gap
                    }
                }
            },
        text = paddedText,
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

/**
 * The line with each chord written into it in brackets, where it sits: "[Am]There is a [C]house". A chord row's mark is
 * written as the file has it, over the blank it left in the text: "[G] [C] x4".
 */
private fun ChordProLine.Lyrics.withChordsInline() = buildString {
    var start = 0
    chords.sortedBy { it.position }.forEach { chord ->
        val position = chord.position.coerceIn(start, text.length)
        append(text, start, position)
        if (chord.isMark) append(chord.name) else append('[').append(chord.name).append(']')
        start = if (chord.isMark) minOf(position + chord.name.length, text.length) else position
    }
    append(text, start, text.length)
}

private val CHORD_GAP = 4.dp
