/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import com.pandulapeter.campfire.chordpro.model.ChordProLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SongLinksDraftTest {

    private val linkA = ChordProLink(url = "https://example.com/a", name = "A")
    private val emptyRow = ChordProLink(url = "")

    @Test
    fun `an empty row is left out rather than blocking Save`() {
        assertEquals(listOf(linkA), songLinksToSave(draft = listOf(linkA, emptyRow), offered = emptyList()))
    }

    @Test
    fun `a draft of nothing but an empty row writes nothing`() {
        assertNull(songLinksToSave(draft = listOf(emptyRow), offered = emptyList()))
    }

    @Test
    fun `a draft that is unchanged once its empty row is ignored writes nothing`() {
        assertNull(songLinksToSave(draft = listOf(linkA, emptyRow), offered = listOf(linkA)))
    }

    @Test
    fun `removing every link is a valid save`() {
        assertEquals(emptyList(), songLinksToSave(draft = emptyList(), offered = listOf(linkA)))
    }

    @Test
    fun `a named row without an address blocks Save`() {
        assertNull(songLinksToSave(draft = listOf(ChordProLink(url = "", name = "Tab")), offered = emptyList()))
    }

    @Test
    fun `duplicate and unusable addresses block Save`() {
        assertNull(songLinksToSave(draft = listOf(linkA, linkA), offered = emptyList()))
        assertNull(songLinksToSave(draft = listOf(ChordProLink(url = "not a url")), offered = emptyList()))
    }

    @Test
    fun `an address without its scheme is kept as typed`() {
        val link = ChordProLink(url = "example.com/tab")
        assertEquals(listOf(link), songLinksToSave(draft = listOf(link), offered = emptyList()))
    }
}
