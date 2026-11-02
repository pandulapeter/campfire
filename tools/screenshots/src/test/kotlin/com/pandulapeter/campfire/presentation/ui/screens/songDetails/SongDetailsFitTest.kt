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

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.chords
import com.pandulapeter.campfire.presentation.resources.instrument_choose
import com.pandulapeter.campfire.presentation.resources.instrument_tuning_choose
import com.pandulapeter.campfire.presentation.resources.song_details_chord_diagram
import com.pandulapeter.campfire.presentation.resources.song_details_metronome_show
import com.pandulapeter.campfire.presentation.resources.song_details_next_page
import com.pandulapeter.campfire.presentation.resources.song_details_transpose_down
import com.pandulapeter.campfire.screenshots.APP_LANGUAGES
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.HOUSE_OF_THE_RISING_SUN
import com.pandulapeter.campfire.screenshots.TestAppearance
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.breaksAWord
import com.pandulapeter.campfire.screenshots.demoSongText
import com.pandulapeter.campfire.screenshots.isWhole
import com.pandulapeter.campfire.screenshots.openSong
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.textLayout
import kotlin.test.Test
import kotlin.test.fail

/**
 * The first screen of a song on the smallest phone, which holds the song's controls (instrument, transposition, capo,
 * tempo, and time signature) and its Chords section before any lyric: the controls must leave the diagrams room there,
 * and their labels must stay whole at a large text size.
 */
@OptIn(ExperimentalTestApi::class)
internal class SongDetailsFitTest {

    /**
     * The song's controls and chords crowding a phone's first screen of the song: every chord the song plays has its
     * diagram on that screen, inside the song's viewport and clear of the page button, and no control, the Chords
     * section's title, or a row of diagrams is drawn over another.
     */
    @Test
    fun `a phone's first screen of a song holds its controls and every chord's diagram apart`() = runAppTest(window = TestWindow.PHONE) {
        openSong()
        val failures = mutableListOf<String>()
        val viewport = checkNotNull(songViewport()) { "The song has no viewport." }.boundsInRoot
        val pageButton = node(hasContentDescription(string(Res.string.song_details_next_page))).boundsInRoot
        val diagramRows = songChords().map { chord ->
            val prefix = text(Res.string.song_details_chord_diagram, chord, PLACEHOLDER).substringBefore(PLACEHOLDER)
            val row = onAllNodes(hasContentDescription(prefix, substring = true), useUnmergedTree = true).fetchSemanticsNodes()
                .firstOrNull { it.boundsInRoot.isVisible }
            if (row == null) failures += "$chord has no diagram on the first screen."
            row
        }.filterNotNull().distinctBy { it.id }
        diagramRows.filter { it.boundsInRoot.overlapsBeyondRounding(pageButton) }.forEach { row ->
            failures += "A row of diagrams (${row.boundsInRoot}) is under the page button ($pageButton)."
        }
        val pieces = controls().leaves().map { it.label() to it.boundsInRoot } +
            (string(Res.string.chords) to node(hasText(string(Res.string.chords))).boundsInRoot) +
            diagramRows.map { "a row of diagrams" to it.boundsInRoot }
        pieces.forEach { (label, bounds) ->
            if (!viewport.containsWithinRounding(bounds)) failures += "\"$label\" ($bounds) is not on the first screen ($viewport)."
        }
        pieces.forEachIndexed { index, (label, bounds) ->
            pieces.drop(index + 1).filter { it.second.overlapsBeyondRounding(bounds) }.forEach { (other, otherBounds) ->
                failures += "\"$label\" ($bounds) and \"$other\" ($otherBounds) overlap."
            }
        }
        if (failures.isNotEmpty()) fail(failures.joinToString(separator = "\n"))
    }

    /**
     * Labels broken in the middle of a word at a large text size, as the chips and the steppers were, and steppers that
     * no longer fit their labels at the largest one: in every language, no text of the controls or the Chords section's
     * title breaks a word, and every one is whole but the instrument pill's names, which it cuts short by design where
     * the line is too narrow for both (`InstrumentPill`).
     */
    @Test
    fun `the song's controls keep their labels whole at large text sizes in every language`() {
        val failures = mutableListOf<String>()
        APP_LANGUAGES.forEach { language ->
            listOf(1.3f, 2f).forEach { textScale ->
                runAppTest(window = TestWindow.PHONE, appearance = TestAppearance(language = language, textScale = textScale)) {
                    openSong()
                    val case = "${language.id} at $textScale"
                    val pillHalves = listOf(Res.string.instrument_choose, Res.string.instrument_tuning_choose).map { opening(string(it)).id }
                    val texts = controls().leaves().filter { it.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null }
                    if (texts.isEmpty()) failures += "$case: the controls show no text."
                    val chordsTitle = node(hasText(string(Res.string.chords)))
                    (texts + chordsTitle).forEach { text ->
                        val layout = text.textLayout
                        val mayBeCutShort = text.ancestry().any { it.id in pillHalves }
                        if (!mayBeCutShort && !layout.fitsItsRoom) failures += "$case: \"${text.label()}\" is cut short."
                        if (layout.breaksAWord) failures += "$case: \"${text.label()}\" is broken in the middle of a word."
                    }
                }
            }
        }
        if (failures.isNotEmpty()) fail(failures.joinToString(separator = "\n"))
    }
}

/** The chords House of the Rising Sun plays, as its file writes them in its lyrics. */
private fun songChords() = Regex("\\[([^\\]]+)]").findAll(demoSongText(HOUSE_OF_THE_RISING_SUN)).map { it.groupValues[1] }.distinct().toList()

@OptIn(ExperimentalTestApi::class)
private fun AppTestScope.node(matcher: SemanticsMatcher) = onNode(matcher, useUnmergedTree = true).fetchSemanticsNode()

/**
 * The song's controls: the smallest part of the screen that holds the instrument pill, the transposition, and the
 * metronome button, the first and the last of them.
 */
@OptIn(ExperimentalTestApi::class)
private fun AppTestScope.controls(): SemanticsNode {
    val anchors = listOf(
        opening(string(Res.string.instrument_choose)),
        node(hasContentDescription(string(Res.string.song_details_transpose_down))),
        node(hasContentDescription(string(Res.string.song_details_metronome_show), substring = true)),
    )
    return anchors.map { it.ancestry() }.reduce { common, ancestry -> common.filter { node -> ancestry.any { it.id == node.id } } }.first()
}

/** The button whose press is said as [label], such as an instrument pill's half. */
@OptIn(ExperimentalTestApi::class)
private fun AppTestScope.opening(label: String) = node(SemanticsMatcher("is pressed to $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label })

/** This node and its parents, nearest first. */
private fun SemanticsNode.ancestry() = generateSequence(this) { it.parent }.toList()

/**
 * The texts and icons inside this node that have a size and say something, each the innermost node saying it. A part
 * cleared of semantics is left out with what it holds: a stepper's room for its widest value is measured and never
 * drawn, and its drawn value is silent over the field that says the same.
 */
private fun SemanticsNode.leaves(): List<SemanticsNode> = children.filterNot { it.config.isClearingSemantics }.flatMap { child ->
    child.leaves().ifEmpty { listOfNotNull(child.takeIf { it.boundsInRoot.isVisible && it.label().isNotEmpty() }) }
}

/**
 * Whether the text is whole as `isWhole` asks, except that a line's leading may reach past a control's fixed height:
 * a stepper is exactly as tall as the song's controls, and at a large system text size its value's line height outgrows
 * it by a pixel above and below its letters, which is not drawn. Only the letters themselves, laid out without that
 * leading, must fit the height.
 */
private val TextLayoutResult.fitsItsRoom
    get() = isWhole ||
        !multiParagraph.didExceedMaxLines &&
        (0 until lineCount).none { getLineRight(it) - getLineLeft(it) > size.width + TOLERANCE } &&
        lettersHeight() <= size.height + TOLERANCE

/** How tall this text's lines are without the leading their line height adds. */
private fun TextLayoutResult.lettersHeight() = TextMeasurer(layoutInput.fontFamilyResolver, layoutInput.density, layoutInput.layoutDirection)
    .measure(
        text = layoutInput.text,
        style = layoutInput.style.copy(lineHeight = TextUnit.Unspecified),
        maxLines = layoutInput.maxLines,
        constraints = Constraints(maxWidth = size.width),
    ).size.height

/** What this node says, its text or else its description. */
private fun SemanticsNode.label() = config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
    ?: config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()

private val Rect.isVisible get() = width > 0f && height > 0f

/** Whether [other] is inside this, allowing the half a dp a layout rounds by. */
private fun Rect.containsWithinRounding(other: Rect) = other.left >= left - TOLERANCE && other.top >= top - TOLERANCE &&
    other.right <= right + TOLERANCE && other.bottom <= bottom + TOLERANCE

/** Whether this and [other] share more than the half a dp a layout rounds by in both directions. */
private fun Rect.overlapsBeyondRounding(other: Rect) = minOf(right, other.right) - maxOf(left, other.left) > TOLERANCE &&
    minOf(bottom, other.bottom) - maxOf(top, other.top) > TOLERANCE

private const val TOLERANCE = 0.5f

/** Stands in for a diagram's shape, so that the text before it is how a chord's description starts. */
private const val PLACEHOLDER = "\u0000"
