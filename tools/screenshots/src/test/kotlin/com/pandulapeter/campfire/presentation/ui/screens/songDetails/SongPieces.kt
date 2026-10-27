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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import com.pandulapeter.campfire.screenshots.AppTestScope

/** Opens the song titled [title] from the songs list, scrolling the list to it, and waits for its page to be laid out. */
@OptIn(ExperimentalTestApi::class)
internal fun AppTestScope.openSong(title: String) {
    onNode(hasScrollToNodeAction()).performScrollToNode(hasText(title))
    onNode(hasText(title) and hasClickAction()).performClick()
    waitUntil(timeoutMillis = OPEN_TIMEOUT_MILLIS) { songViewport() != null }
    waitForIdle()
}

/** A line of text, or a block drawn rather than written (tablature, chord diagrams), where it is in the song. */
internal data class Piece(val text: String, val occurrence: Int, val bounds: Rect) {
    val key get() = text to occurrence
}

private val isSongViewport = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

@OptIn(ExperimentalTestApi::class)
internal fun AppTestScope.songViewport() = onAllNodes(isSongViewport, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull { node ->
    node.children.isNotEmpty() && songPieces(node, 0f).isNotEmpty()
}

internal fun SemanticsNode.scroll() = config[SemanticsProperties.VerticalScrollAxisRange].value()

/**
 * The pieces of the song in [viewport], [songTop] being where the top of the song is on screen, in the order they are
 * read: every line of every text, and every block drawn rather than written. A card drawn behind a chorus has a shape
 * and nothing to read, and an icon has a role. A line reaches from its top, where chords are drawn over it, to as far
 * as its letters can reach below it, not over the leading its line height adds, where a row's divider may be drawn.
 */
internal fun songPieces(viewport: SemanticsNode, songTop: Float): List<Piece> {
    val pieces = mutableListOf<Pair<String, Rect>>()
    fun visit(node: SemanticsNode) {
        val bounds = node.unclippedBounds().translate(0f, -songTop)
        val layout = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.let { action ->
            mutableListOf<TextLayoutResult>().also { action(it) }.firstOrNull()
        }
        when {
            layout != null -> {
                val descent = layout.descent()
                (0 until layout.lineCount).forEach { line ->
                    pieces += layout.layoutInput.text.text.substring(layout.getLineStart(line), layout.getLineEnd(line)) to Rect(
                        left = bounds.left,
                        top = bounds.top + layout.getLineTop(line).coerceAtLeast(0f),
                        right = bounds.right,
                        bottom = bounds.top + minOf(layout.getLineBottom(line), layout.getLineBaseline(line) + descent, bounds.height),
                    )
                }
            }
            node.children.isEmpty() && bounds.height > 0f && node.config.getOrNull(SemanticsProperties.Role) == null &&
                node.config.getOrNull(SemanticsProperties.Shape) == null ->
                pieces += node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty() to bounds
            else -> node.children.forEach(::visit)
        }
    }
    viewport.children.forEach(::visit)
    val occurrences = mutableMapOf<String, Int>()
    return pieces.map { (text, bounds) -> Piece(text = text, occurrence = occurrences.merge(text, 1, Int::plus)!!, bounds = bounds) }
}

/** How far the letters of this text's style can reach below their baseline, without the leading its line height adds. */
private fun TextLayoutResult.descent() = descents.getOrPut(layoutInput.style) {
    TextMeasurer(layoutInput.fontFamilyResolver, layoutInput.density, layoutInput.layoutDirection)
        .measure(DESCENT_SAMPLE, layoutInput.style.copy(lineHeight = TextUnit.Unspecified))
        .let { it.size.height - it.firstBaseline }
}

private val descents = mutableMapOf<TextStyle, Float>()

internal fun SemanticsNode.unclippedBounds() = Rect(offset = positionInRoot, size = Size(size.width.toFloat(), size.height.toFloat()))

private const val OPEN_TIMEOUT_MILLIS = 10_000L
private const val DESCENT_SAMPLE = "gjpqy"
