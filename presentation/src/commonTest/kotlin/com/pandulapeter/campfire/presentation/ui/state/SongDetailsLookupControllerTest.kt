/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields
import com.pandulapeter.campfire.data.model.domain.SongDetailsQuery
import kotlin.test.Test
import kotlin.test.assertEquals

class SongDetailsLookupControllerTest {

    @Test
    fun `the lookup is first asked the title, artist and album typed into the form, trimmed`() {
        val draft = mapOf(
            ChordProMetadataFields.Field.TITLE to "  Wonderwall ",
            ChordProMetadataFields.Field.ARTIST to "Oasis ",
            ChordProMetadataFields.Field.ALBUM to " Morning Glory",
            ChordProMetadataFields.Field.YEAR to "1995",
        )

        assertEquals(SongDetailsQuery(title = "Wonderwall", artist = "Oasis", album = "Morning Glory"), songDetailsQueryOf(draft))
    }

    @Test
    fun `a field the form leaves out is asked as empty`() {
        val draft = mapOf(ChordProMetadataFields.Field.TITLE to "Wonderwall")

        assertEquals(SongDetailsQuery(title = "Wonderwall", artist = "", album = ""), songDetailsQueryOf(draft))
    }
}
