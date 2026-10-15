/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.labels

import com.pandulapeter.campfire.data.model.preferences.UserPreferences.LabelSortingMode
import com.pandulapeter.campfire.data.model.song.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TagsSheetDecisionsTest {

    private val library = listOf(Tag(name = "rock", songCount = 3), Tag(name = "Blues", songCount = 1))

    @Test
    fun `the library's tags lead in their order, spelled as the songs spell them, and each tag is offered once`() {
        assertEquals(
            listOf("Rock", "blues", "Folk"),
            offeredTagsOf(libraryTags = library, sortingMode = LabelSortingMode.BY_USAGE, ownTags = listOf("Rock", "Folk", "FOLK", "blues")),
        )
    }

    @Test
    fun `alphabetical sorting puts a new tag among the library's`() {
        assertEquals(
            listOf("Blues", "Folk", "rock"),
            offeredTagsOf(libraryTags = library, sortingMode = LabelSortingMode.ALPHABETICAL, ownTags = listOf("Folk")),
        )
    }

    @Test
    fun `a typed tag ticks the offered one it spells whatever its case`() {
        assertEquals("Blues", spelledTagOf("BLUES", listOf("Rock", "Blues")))
        assertNull(spelledTagOf("Jazz", listOf("Rock", "Blues")))
    }

    @Test
    fun `save commits a tag still typed, in the offered spelling where there is one`() {
        val draft = BulkLabelDraft.of(listOf(listOf("rock")))
        val (toSave, spellings) = tagsToSave(draft = draft, offeredTags = listOf("Rock", "Blues"), typedTag = "BLUES")
        assertEquals(setOf("rock", "blues"), toSave.onKeys)
        assertEquals("Blues", spellings["blues"])
        assertEquals(mapOf("rock" to "Rock", "blues" to "Blues"), spellings)
    }

    @Test
    fun `save writes a new typed tag as it was typed`() {
        val (toSave, spellings) = tagsToSave(draft = BulkLabelDraft.of(listOf(emptyList())), offeredTags = listOf("Rock"), typedTag = "Jazz Fusion")
        assertTrue(toSave.hasChanges)
        assertEquals(setOf("jazz fusion"), toSave.added)
        assertEquals("Jazz Fusion", spellings["jazz fusion"])
    }

    @Test
    fun `an empty field leaves the draft as it is`() {
        val draft = BulkLabelDraft.of(listOf(listOf("rock")))
        val (toSave, spellings) = tagsToSave(draft = draft, offeredTags = listOf("Rock"), typedTag = "")
        assertFalse(toSave.hasChanges)
        assertEquals(mapOf("rock" to "Rock"), spellings)
    }
}
