/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.screenshots

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.text.TextLayoutResult
import com.pandulapeter.campfire.data.model.preferences.AppLanguage

/** The app's languages, every one a user can pick. */
internal val APP_LANGUAGES = AppLanguage.entries - AppLanguage.SYSTEM_DEFAULT

/**
 * The layout of the text the node [matcher] finds, its own `Text` rather than the merged button around it, so the
 * matcher is applied to the unmerged tree.
 */
@OptIn(ExperimentalTestApi::class)
internal fun AppTestScope.textLayout(matcher: SemanticsMatcher) = onNode(matcher, useUnmergedTree = true).fetchSemanticsNode().textLayout

/** The layout of this node's text, as its `Text` reports it. */
internal val SemanticsNode.textLayout: TextLayoutResult
    get() = mutableListOf<TextLayoutResult>().also { config[SemanticsActions.GetTextLayoutResult].action?.invoke(it) }.single()

/**
 * Whether the whole text is shown: no line dropped past the maximum (where an ellipsis would cut the last one shown),
 * nothing taller than the room it was given, and no line wider than the text's own width. Not read from
 * `isLineEllipsized`, which Skiko's paragraph, the one the desktop JVM these tests run on draws with, answers false for
 * every line, cut or not; nor from `didOverflowWidth`, which compares the text's width with the paragraph's, laid out
 * as wide as the room offered, and so is true of any text shorter than its room.
 */
internal val TextLayoutResult.isWhole
    get() = !multiParagraph.didExceedMaxLines && !didOverflowHeight &&
        (0 until lineCount).none { getLineRight(it) - getLineLeft(it) > size.width + 0.5f }

/** Whether a line ends between two letters of one word, which reads as two words. */
internal val TextLayoutResult.breaksAWord: Boolean
    get() {
        val text = layoutInput.text.text
        return (0 until lineCount - 1).any { line ->
            val end = getLineEnd(line, visibleEnd = true)
            end in 1 until text.length && text[end - 1].isLetter() && text[end].isLetter()
        }
    }
