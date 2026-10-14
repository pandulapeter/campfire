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

import kotlin.test.Test
import kotlin.test.assertEquals

class EditorPanesTest {

    @Test
    fun `split shows as edit where two panes do not fit`() =
        assertEquals(EditorPanes.EDIT, shownPanes(selected = EditorPanes.SPLIT, hasRoomForSplitPanes = false))

    @Test
    fun `split comes back where they fit again`() =
        assertEquals(EditorPanes.SPLIT, shownPanes(selected = EditorPanes.SPLIT, hasRoomForSplitPanes = true))

    @Test
    fun `a single pane is shown whatever the room`() =
        assertEquals(EditorPanes.PREVIEW, shownPanes(selected = EditorPanes.PREVIEW, hasRoomForSplitPanes = false))
}
