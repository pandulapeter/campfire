/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songs

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import com.pandulapeter.campfire.screenshots.APP_LANGUAGES
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.RISING_SUN_TITLE
import com.pandulapeter.campfire.screenshots.TestAppearance
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlin.test.Test
import kotlin.test.fail

/**
 * Cards whose title, artist, key, and tags cut each other off at the smallest phone's width: every text a demo song's
 * card draws (its title, its artist where no header names it, its key, tempo, and labels) lies inside the card and over
 * none of the others, in every language at the default and a large text size. A long title may still end in an
 * ellipsis; two pieces drawn over each other, or past the card's edge, are the bug.
 */
@OptIn(ExperimentalTestApi::class)
internal class SongCardFitTest {

    @Test
    fun `a song card's texts stay inside it and apart in every language at the default and a large text size`() {
        val failures = mutableListOf<String>()
        APP_LANGUAGES.forEach { language ->
            listOf(1f, 1.3f).forEach { textScale ->
                runAppTest(window = TestWindow.PHONE, appearance = TestAppearance(language = language, textScale = textScale)) {
                    val case = "${language.id} at $textScale"
                    listOf(RISING_SUN_TITLE, HOME_ON_THE_RANGE_TITLE).forEach { title ->
                        val card = card(title)
                        val texts = card.textsWithin().filter { it.second.width > 0f && it.second.height > 0f }
                        if (texts.isEmpty()) failures += "$case: the card of $title shows no text."
                        texts.forEach { (text, bounds) ->
                            if (!card.boundsInRoot.containsWithinRounding(bounds)) {
                                failures += "$case: \"$text\" ($bounds) reaches out of the card of $title (${card.boundsInRoot})."
                            }
                        }
                        texts.forEachIndexed { index, (text, bounds) ->
                            texts.drop(index + 1).filter { it.second.overlapsBeyondRounding(bounds) }.forEach { (other, otherBounds) ->
                                failures += "$case: on the card of $title, \"$text\" ($bounds) and \"$other\" ($otherBounds) overlap."
                            }
                        }
                    }
                }
            }
        }
        if (failures.isNotEmpty()) fail(failures.joinToString(separator = "\n"))
    }
}

/** The card of the song titled [title], as the unmerged tree has it, so that its texts are nodes of their own. */
@OptIn(ExperimentalTestApi::class)
private fun AppTestScope.card(title: String): SemanticsNode {
    val id = onNode(hasText(title, substring = true) and hasClickAction()).fetchSemanticsNode().id
    return onNode(SemanticsMatcher("is the card of $title") { it.id == id }, useUnmergedTree = true).fetchSemanticsNode()
}

/** Every text drawn inside this node, with its bounds. */
private fun SemanticsNode.textsWithin(): List<Pair<String, Rect>> = children.flatMap { child ->
    val text = child.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
    listOfNotNull(text?.takeIf { it.isNotBlank() }?.let { it to child.boundsInRoot }) + child.textsWithin()
}

/** Whether [other] is inside this, allowing the half a dp a layout rounds by. */
private fun Rect.containsWithinRounding(other: Rect) = other.left >= left - TOLERANCE && other.top >= top - TOLERANCE &&
    other.right <= right + TOLERANCE && other.bottom <= bottom + TOLERANCE

/** Whether this and [other] share more than the half a dp a layout rounds by in both directions. */
private fun Rect.overlapsBeyondRounding(other: Rect) = minOf(right, other.right) - maxOf(left, other.left) > TOLERANCE &&
    minOf(bottom, other.bottom) - maxOf(top, other.top) > TOLERANCE

private const val TOLERANCE = 0.5f
private const val HOME_ON_THE_RANGE_TITLE = "Home on the Range"
