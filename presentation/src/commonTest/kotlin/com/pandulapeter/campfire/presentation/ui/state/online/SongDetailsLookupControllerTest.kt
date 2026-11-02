/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.online

import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields
import com.pandulapeter.campfire.data.model.catalogue.CatalogueSearchResults
import com.pandulapeter.campfire.data.model.catalogue.SongCreditsState
import com.pandulapeter.campfire.data.model.catalogue.SongDetailsCandidate
import com.pandulapeter.campfire.data.model.catalogue.SongDetailsQuery
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.domain.api.catalogue.LoadSongCreditsUseCase
import com.pandulapeter.campfire.domain.api.catalogue.SearchSongDetailsUseCase
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.SongEditTarget
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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

    @Test
    fun `closing the sheet cancels the search it started`() = runTest {
        val searches = mutableListOf<String>()
        val dialogHost = DialogHost(backgroundScope)
        SongDetailsLookupController(
            scope = backgroundScope,
            searchSongDetails = object : SearchSongDetailsUseCase {
                override fun invoke(query: SongDetailsQuery): Flow<CatalogueSearchResults<SongDetailsCandidate>> = flow {
                    searches += "started ${query.title}"
                    try {
                        awaitCancellation()
                    } finally {
                        searches += "cancelled ${query.title}"
                    }
                }
            },
            loadSongCredits = object : LoadSongCreditsUseCase {
                override fun invoke(candidate: SongDetailsCandidate) = emptyFlow<SongCreditsState>()
            },
            dialogHost = dialogHost,
        )
        val song = Song(
            fileName = "a.cho", title = "Wonderwall", artist = "Oasis", key = null, transpose = 0, tags = emptyList(),
            languages = emptyList(), coverArtUrl = null, hasChords = true, canUpdateFileName = false, lastModified = 0, size = 0,
        )
        val draft = mapOf(ChordProMetadataFields.Field.TITLE to "Wonderwall")
        dialogHost.showDialog(
            DialogType.SongDetailsLookup(song = song, target = SongEditTarget.File(song.fileName), values = draft, draft = draft, formFillCount = 0),
        )
        runCurrent()
        assertEquals(listOf("started Wonderwall"), searches)
        dialogHost.dismissDialog()
        runCurrent()
        assertEquals(listOf("started Wonderwall", "cancelled Wonderwall"), searches)
    }
}
