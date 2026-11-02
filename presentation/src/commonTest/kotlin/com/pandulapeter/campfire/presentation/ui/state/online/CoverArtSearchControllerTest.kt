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

import com.pandulapeter.campfire.data.model.catalogue.CoverArtQuery
import com.pandulapeter.campfire.data.model.catalogue.CoverArtSearchResults
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.domain.api.catalogue.ClearCoverArtCacheUseCase
import com.pandulapeter.campfire.domain.api.catalogue.GetCoverArtCacheSizeUseCase
import com.pandulapeter.campfire.domain.api.catalogue.SearchCoverArtUseCase
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.SongEditTarget
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CoverArtSearchControllerTest {

    private val song = Song(
        fileName = "a.cho", title = "Wonderwall", artist = "Oasis", key = null, transpose = 0, tags = emptyList(),
        languages = emptyList(), coverArtUrl = null, hasChords = true, canUpdateFileName = false, lastModified = 0, size = 0,
    )
    private val searches = mutableListOf<String>()

    @Test
    fun `closing the sheet cancels the search it started`() = runTest {
        val dialogHost = DialogHost(backgroundScope)
        CoverArtSearchController(
            scope = backgroundScope,
            searchCoverArt = object : SearchCoverArtUseCase {
                override fun invoke(query: CoverArtQuery): Flow<CoverArtSearchResults> = flow {
                    searches += "started ${query.title}"
                    try {
                        awaitCancellation()
                    } finally {
                        searches += "cancelled ${query.title}"
                    }
                }
            },
            getCoverArtCacheSize = object : GetCoverArtCacheSizeUseCase {
                override fun invoke() = flowOf<Long?>(null)
            },
            clearCoverArtCache = object : ClearCoverArtCacheUseCase {
                override suspend fun invoke() = Unit
            },
            songTextOf = { null },
            dialogHost = dialogHost,
        )
        dialogHost.showDialog(DialogType.CoverArtSearch(song = song, target = SongEditTarget.File(song.fileName)))
        runCurrent()
        assertEquals(listOf("started Wonderwall"), searches)
        dialogHost.dismissDialog()
        runCurrent()
        assertEquals(listOf("started Wonderwall", "cancelled Wonderwall"), searches)
    }
}
