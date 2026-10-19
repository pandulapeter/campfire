/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.lookup

import com.pandulapeter.campfire.data.model.catalogue.Catalogue
import com.pandulapeter.campfire.data.model.catalogue.CoverArtCandidate
import com.pandulapeter.campfire.data.model.catalogue.CoverArtSearchResults
import kotlin.test.Test
import kotlin.test.assertEquals

class CoverArtResultsContentTest {

    @Test
    fun `a search whose every thumbnail could not be reached has failed`() {
        assertEquals(ResultsContent.FAILED, resultsContentOf(complete(A, B), shownCandidates = emptyList(), unreachableKeys = setOf(A.key, B.key)))
    }

    @Test
    fun `a search whose every record has no cover found nothing`() {
        assertEquals(ResultsContent.NO_RESULTS, resultsContentOf(complete(A, B), shownCandidates = emptyList(), unreachableKeys = emptySet()))
    }

    @Test
    fun `a search with one unreachable thumbnail among missing ones has failed`() {
        assertEquals(ResultsContent.FAILED, resultsContentOf(complete(A, B), shownCandidates = emptyList(), unreachableKeys = setOf(B.key)))
    }

    @Test
    fun `a search a catalogue failed with nothing to show has failed`() {
        val results = CoverArtSearchResults(candidates = emptyList(), pending = emptySet(), busy = emptySet(), failed = setOf(Catalogue.ITUNES))

        assertEquals(ResultsContent.FAILED, resultsContentOf(results, shownCandidates = emptyList(), unreachableKeys = emptySet()))
    }

    @Test
    fun `an unfinished search with nothing to show is loading, or busy once only waiting catalogues are left`() {
        val loading = CoverArtSearchResults(
            candidates = emptyList(),
            pending = setOf(Catalogue.MUSIC_BRAINZ, Catalogue.ITUNES),
            busy = setOf(Catalogue.MUSIC_BRAINZ),
            failed = emptySet(),
        )
        val busy = loading.copy(pending = setOf(Catalogue.MUSIC_BRAINZ))

        assertEquals(ResultsContent.LOADING, resultsContentOf(loading, shownCandidates = emptyList(), unreachableKeys = setOf(A.key)))
        assertEquals(ResultsContent.BUSY, resultsContentOf(busy, shownCandidates = emptyList(), unreachableKeys = setOf(A.key)))
    }

    @Test
    fun `a search with a tile left shows the grid, and no search shows the hint`() {
        assertEquals(ResultsContent.GRID, resultsContentOf(complete(A, B), shownCandidates = listOf(A), unreachableKeys = setOf(B.key)))
        assertEquals(ResultsContent.HINT, resultsContentOf(null, shownCandidates = emptyList(), unreachableKeys = emptySet()))
    }

    private fun complete(vararg candidates: CoverArtCandidate) =
        CoverArtSearchResults(candidates = candidates.toList(), pending = emptySet(), busy = emptySet(), failed = emptySet())

    private companion object {
        val A = candidate("a")
        val B = candidate("b")

        fun candidate(id: String) = CoverArtCandidate(
            catalogue = Catalogue.MUSIC_BRAINZ,
            id = id,
            title = "",
            artist = "",
            year = null,
            type = null,
            coverArtUrl = "https://example.com/$id",
        )
    }
}
