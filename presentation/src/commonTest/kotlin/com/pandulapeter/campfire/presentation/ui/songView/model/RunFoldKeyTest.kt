/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.model

import com.pandulapeter.campfire.chordpro.model.CommentStyle
import com.pandulapeter.campfire.presentation.ui.songLayout.FoldableKind
import kotlin.test.Test
import kotlin.test.assertEquals

/** The keys of folded runs are saved in the preferences, so a change of their form would unfold every run a reader folded. */
class RunFoldKeyTest {

    @Test
    fun `runs are keyed by their section, their name and how many of that name came before`() {
        val counts = mutableMapOf<String, Int>()
        assertEquals(
            listOf("intro#1/tab#1", "intro#1/picking pattern#1", "intro#1/tab#2", "intro#1/grid#1"),
            listOf(
                counts.nextRunFoldKey(sectionFold = "intro#1", label = null, kind = FoldableKind.TAB),
                counts.nextRunFoldKey(sectionFold = "intro#1", label = "picking pattern", kind = FoldableKind.TAB),
                counts.nextRunFoldKey(sectionFold = "intro#1", label = null, kind = FoldableKind.TAB),
                counts.nextRunFoldKey(sectionFold = "intro#1", label = null, kind = FoldableKind.GRID),
            ),
        )
    }

    @Test
    fun `a section's folds are its own key and the keys of its runs`() {
        val folded = setOf("intro#1", "intro#1/tab#1", "verse#1", "intro#10")
        val intro = RenderSection.Lines(header = "Intro", foldKey = "intro#1", parts = emptyList(), isOnCard = false)
        assertEquals(setOf("intro#1", "intro#1/tab#1"), foldedKeysOf(intro, folded))
        assertEquals(emptySet(), foldedKeysOf(RenderSection.Comment(text = "Slower", style = CommentStyle.ITALIC), folded))
    }
}
