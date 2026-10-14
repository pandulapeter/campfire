/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.domain.Catalogue
import com.pandulapeter.campfire.data.model.domain.SongCredits
import com.pandulapeter.campfire.data.model.domain.SongCreditsState
import com.pandulapeter.campfire.data.model.domain.SongDetailsCandidate
import com.pandulapeter.campfire.data.model.domain.SongDetailsQuery
import com.pandulapeter.campfire.data.repository.implementation.base.testEnvironment
import com.pandulapeter.campfire.data.source.remote.api.catalogue.CatalogueSearchException
import com.pandulapeter.campfire.data.source.remote.api.catalogue.SongDetailsSearchRemoteSource
import com.pandulapeter.campfire.data.source.remote.api.catalogue.SongDetailsSearchRemoteSources
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SongDetailsRepositoryImplTest {

    @Test
    fun `a search reports every catalogue as it answers`() = runTest {
        val musicBrainz = FakeSongDetailsSearchRemoteSource(Catalogue.MUSIC_BRAINZ, search = {
            delay(2_000)
            listOf(candidate(Catalogue.MUSIC_BRAINZ))
        })
        val iTunes = FakeSongDetailsSearchRemoteSource(Catalogue.ITUNES, search = { listOf(candidate(Catalogue.ITUNES)) })

        val last = repository(musicBrainz, iTunes).searchSongDetails(QUERY).toList().last()

        assertEquals(listOf(candidate(Catalogue.ITUNES), candidate(Catalogue.MUSIC_BRAINZ)), last.candidates)
        assertEquals(emptySet(), last.failed)
    }

    @Test
    fun `the songwriters are asked of the catalogue that found the candidate`() = runTest {
        val credits = SongCredits(composer = "Noel Gallagher", lyricist = "Noel Gallagher")
        val musicBrainz = FakeSongDetailsSearchRemoteSource(Catalogue.MUSIC_BRAINZ, credits = { onBusy ->
            onBusy()
            credits
        })
        val iTunes = FakeSongDetailsSearchRemoteSource(Catalogue.ITUNES, credits = { null })
        val repository = repository(musicBrainz, iTunes)

        assertEquals(
            listOf(SongCreditsState.Loading, SongCreditsState.Busy, SongCreditsState.Loaded(credits)),
            repository.loadSongCredits(candidate(Catalogue.MUSIC_BRAINZ)).toList(),
        )
        assertEquals(
            listOf(SongCreditsState.Loading, SongCreditsState.Unavailable),
            repository.loadSongCredits(candidate(Catalogue.ITUNES)).toList(),
        )
    }

    @Test
    fun `a songwriters lookup that fails says so rather than throwing`() = runTest {
        val musicBrainz = FakeSongDetailsSearchRemoteSource(Catalogue.MUSIC_BRAINZ, credits = { throw CatalogueSearchException("No network.") })

        assertEquals(
            listOf(SongCreditsState.Loading, SongCreditsState.Failed),
            repository(musicBrainz).loadSongCredits(candidate(Catalogue.MUSIC_BRAINZ)).toList(),
        )
    }

    private fun TestScope.repository(vararg sources: SongDetailsSearchRemoteSource) = SongDetailsRepositoryImpl(
        songDetailsSearchRemoteSources = SongDetailsSearchRemoteSources(all = sources.toList()),
        environment = testEnvironment(),
    )

    private class FakeSongDetailsSearchRemoteSource(
        override val catalogue: Catalogue,
        private val search: suspend () -> List<SongDetailsCandidate> = { error("Not expected.") },
        private val credits: suspend (onBusy: () -> Unit) -> SongCredits? = { error("Not expected.") },
    ) : SongDetailsSearchRemoteSource {
        override suspend fun searchSongDetails(query: SongDetailsQuery, onBusy: () -> Unit) = search()

        override suspend fun loadSongCredits(candidate: SongDetailsCandidate, onBusy: () -> Unit) = credits(onBusy)
    }

    private companion object {
        val QUERY = SongDetailsQuery(title = "Wonderwall", artist = "Oasis", album = "")

        fun candidate(catalogue: Catalogue) = SongDetailsCandidate(
            catalogue = catalogue,
            id = catalogue.name,
            title = "Wonderwall",
            artist = "Oasis",
            album = null,
            year = null,
            duration = null,
        )
    }
}
