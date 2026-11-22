/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackStackQueriesTest {

    private val editor = CampfireDestination.SongEditor(fileName = "a.cho")

    @Test
    fun `an editor with unsaved text on top is open`() =
        assertTrue(isUnsavedEditorOpen(listOf(CampfireDestination.Songs, editor)) { true })

    @Test
    fun `an editor with unsaved text under another screen is open`() =
        assertTrue(isUnsavedEditorOpen(listOf(CampfireDestination.Songs, editor, CampfireDestination.Settings)) { true })

    @Test
    fun `an editor with nothing unsaved is not`() = assertFalse(isUnsavedEditorOpen(listOf(CampfireDestination.Songs, editor)) { false })

    @Test
    fun `unsaved text with no editor on the stack is not`() = assertFalse(isUnsavedEditorOpen(listOf(CampfireDestination.Songs)) { true })
}
