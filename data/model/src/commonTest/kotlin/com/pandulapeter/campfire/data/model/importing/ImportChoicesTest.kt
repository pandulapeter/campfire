/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.importing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportChoicesTest {

    private val first = ImportEntryKey(ImportEntryKey.Kind.SONG, 0)
    private val second = ImportEntryKey(ImportEntryKey.Kind.SONG, 1)

    @Test
    fun `by default every entry is chosen and every conflict keeps both`() {
        val choices = ImportChoices()
        assertTrue(choices.isChosen(first))
        assertTrue(choices.isChosen(ImportEntryKey(ImportEntryKey.Kind.SETLIST, 0)))
        assertEquals(ImportConflictResolution.KEEP_BOTH, choices.resolutionOf(first))
    }

    @Test
    fun `an unticked entry is not chosen and the others still are`() {
        val choices = ImportChoices(leftOut = setOf(first))
        assertFalse(choices.isChosen(first))
        assertTrue(choices.isChosen(second))
    }

    @Test
    fun `a choice made on a row survives a change of the choice for all`() {
        val choices = ImportChoices(conflictResolutions = mapOf(first to ImportConflictResolution.KEEP_BOTH))
            .copy(conflictResolution = ImportConflictResolution.REPLACE)
        assertEquals(ImportConflictResolution.KEEP_BOTH, choices.resolutionOf(first))
        assertEquals(ImportConflictResolution.REPLACE, choices.resolutionOf(second))
    }

    @Test
    fun `a row's choice equal to the new choice for all is still its own`() {
        val choices = ImportChoices(conflictResolutions = mapOf(first to ImportConflictResolution.REPLACE))
            .copy(conflictResolution = ImportConflictResolution.REPLACE)
            .copy(conflictResolution = ImportConflictResolution.KEEP_BOTH)
        assertEquals(ImportConflictResolution.REPLACE, choices.resolutionOf(first))
        assertEquals(ImportConflictResolution.KEEP_BOTH, choices.resolutionOf(second))
    }
}
