/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Find and Find and replace come out of the editor's menu only where the title keeps its room. */
internal class EditorAppBarLayoutTest {

    @Test
    fun `the smallest phone keeps only the overflow button`() =
        assertEquals(48.dp, editorMenuMaxWidth(appBarWidth = 360.dp, isToolbarToggleShown = false, hasCover = true))

    @Test
    fun `a tablet has room for both search buttons next to the overflow button`() =
        assertTrue(editorMenuMaxWidth(appBarWidth = 600.dp, isToolbarToggleShown = false, hasCover = true) >= 48.dp + 36.dp * 2)

    @Test
    fun `the cover and the shortcuts chevron each take a button's room from the menu`() {
        val bare = editorMenuMaxWidth(appBarWidth = 800.dp, isToolbarToggleShown = false, hasCover = false)
        assertEquals(bare - 52.dp, editorMenuMaxWidth(appBarWidth = 800.dp, isToolbarToggleShown = false, hasCover = true))
        assertEquals(bare - 36.dp, editorMenuMaxWidth(appBarWidth = 800.dp, isToolbarToggleShown = true, hasCover = false))
    }
}
