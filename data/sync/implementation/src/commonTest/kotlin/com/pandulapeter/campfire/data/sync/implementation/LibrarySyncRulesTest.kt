/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation

import com.pandulapeter.campfire.chordpro.edit.libraryFilesNamedBy
import com.pandulapeter.campfire.data.model.library.LibraryCollections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibrarySyncRulesTest {

    @Test
    fun `a song references the pictures and attachments the library keeps for it`() {
        val text = "{title: Song}\n{image: src=0123456789abcdef.png}\n{meta: attachment fedcba9876543210.pdf Chart.pdf}\n[G]La"

        val referenced = LibrarySyncRules.references.getValue(LibraryCollections.SONGS).of(text.encodeToByteArray())

        assertEquals(setOf("0123456789abcdef.png", "fedcba9876543210.pdf"), referenced)
        assertEquals(libraryFilesNamedBy(text), referenced)
    }

    @Test
    fun `only songs reference library files`() =
        assertEquals(setOf(LibraryCollections.SONGS), LibrarySyncRules.references.keys)

    @Test
    fun `a setlist's conflict policy answers as the setlist comparison does`() {
        val policy = LibrarySyncRules.conflictPolicies(DayBlindSetlistComparison).getValue(LibraryCollections.SETLISTS)
        val here = "Gig;date:2026-10-07;songs:a".encodeToByteArray()

        assertTrue(policy.isSameApartFromVolatileParts(here, "Gig;date:2026-10-05;songs:a".encodeToByteArray()))
        assertFalse(policy.isSameApartFromVolatileParts(here, "Gig;date:2026-10-07;songs:b".encodeToByteArray()))
        assertEquals("Gig;songs:a", policy.withoutVolatileParts(here)?.decodeToString())
        assertFalse(LibrarySyncRules.conflictPolicies(NoSetlistComparison).getValue(LibraryCollections.SETLISTS).isSameApartFromVolatileParts(here, here))
        assertNull(LibrarySyncRules.conflictPolicies(NoSetlistComparison).getValue(LibraryCollections.SETLISTS).withoutVolatileParts(here))
    }

    @Test
    fun `only setlists have a conflict policy`() =
        assertEquals(setOf(LibraryCollections.SETLISTS), LibrarySyncRules.conflictPolicies(NoSetlistComparison).keys)
}
