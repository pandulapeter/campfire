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

import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.instrument_choose
import com.pandulapeter.campfire.presentation.resources.instrument_tuning_choose
import com.pandulapeter.campfire.presentation.resources.song_details_dock_open_tempo
import com.pandulapeter.campfire.presentation.resources.song_details_dock_open_playing
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
 * The song's dock on the smallest phone, which holds the tempo chip and a diagram for each of its chords under the song:
 * they must stay apart and off the song, and their labels must stay whole at a large text size.
 */
@OptIn(ExperimentalTestApi::class)
internal class SongDetailsFitTest {

    /**
     * The dock crowding the song or itself: every chord the song plays is named in it, it stands under the song's
     * viewport rather than over a line, and none of what it shows is drawn over another.
     */
    @Test
    fun `a phone's dock holds every chord of the song and its controls apart, under the song`() = runAppTest(window = TestWindow.PHONE) {
        openSong()
        val failures = mutableListOf<String>()
        val viewport = checkNotNull(songViewport()) { "The song has no viewport." }.boundsInRoot
        val dock = controls()
        if (dock.boundsInRoot.top < viewport.bottom - TOLERANCE) failures += "The dock (${dock.boundsInRoot}) reaches over the song ($viewport)."
        songChords().forEach { chord ->
            val chips = onAllNodes(hasText(chord), useUnmergedTree = true).fetchSemanticsNodes().filter { node -> node.ancestry().any { it.id == dock.id } }
            if (chips.isEmpty()) failures += "$chord is not named in the dock."
        }
        val pieces = dock.leaves().map { it.label() to it.boundsInRoot }
        pieces.forEachIndexed { index, (label, bounds) ->
            pieces.drop(index + 1).filter { it.second.overlapsBeyondRounding(bounds) }.forEach { (other, otherBounds) ->
                failures += "\"$label\" ($bounds) and \"$other\" ($otherBounds) overlap."
            }
        }
        if (failures.isNotEmpty()) fail(failures.joinToString(separator = "\n"))
    }

    /**
     * Labels broken in the middle of a word at a large text size, as the chips and the steppers were, and steppers that
     * no longer fit their labels at the largest one: in every language, no text of the controls breaks a word, and every
     * one is whole but the instrument pill's names, which it cuts short by design where
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
                    val pillHalves = listOf(Res.string.instrument_choose, Res.string.instrument_tuning_choose).mapNotNull { openingOrNull(string(it))?.id }
                    val texts = controls().leaves().filter { it.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null }
                    if (texts.isEmpty()) failures += "$case: the controls show no text."
                    texts.forEach { text ->
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
 * The song's controls: the smallest part of the screen that holds the tempo chip and a diagram's name outside the song
 * and the app bar's key chip (whose key may be a chord's name too), which is the dock.
 */
@OptIn(ExperimentalTestApi::class)
private fun AppTestScope.controls(): SemanticsNode {
    val song = checkNotNull(songViewport()) { "The song has no viewport." }
    val chip = opening(string(Res.string.song_details_dock_open_playing))
    val diagramName = onAllNodes(hasText(songChords().last()), useUnmergedTree = true).fetchSemanticsNodes()
        .first { node -> node.ancestry().none { it.id == song.id || it.id == chip.id } }
    val anchors = listOf(
        opening(string(Res.string.song_details_dock_open_tempo)),
        diagramName,
    )
    return anchors.map { it.ancestry() }.reduce { common, ancestry -> common.filter { node -> ancestry.any { it.id == node.id } } }.first()
}

/** The button whose press is said as [label], such as an instrument pill's half. */
@OptIn(ExperimentalTestApi::class)
private fun AppTestScope.opening(label: String) = node(isPressedTo(label))

/** [opening], or null where nothing on screen is pressed to [label]. */
@OptIn(ExperimentalTestApi::class)
private fun AppTestScope.openingOrNull(label: String) = onAllNodes(isPressedTo(label), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()

private fun isPressedTo(label: String) = SemanticsMatcher("is pressed to $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

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
private fun TextLayoutResult.lettersHeight() = TextMeasurer(layoutInput.fontFamilyResolver, LocaleList("en"), layoutInput.density, layoutInput.layoutDirection)
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

/** Whether this and [other] share more than the half a dp a layout rounds by in both directions. */
private fun Rect.overlapsBeyondRounding(other: Rect) = minOf(right, other.right) - maxOf(left, other.left) > TOLERANCE &&
    minOf(bottom, other.bottom) - maxOf(top, other.top) > TOLERANCE

private const val TOLERANCE = 0.5f
