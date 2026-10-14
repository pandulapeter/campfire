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
import com.pandulapeter.campfire.data.model.domain.CatalogueSearchResults
import com.pandulapeter.campfire.data.model.domain.SongCreditsState
import com.pandulapeter.campfire.data.model.domain.SongDetailsCandidate
import com.pandulapeter.campfire.data.model.domain.SongDetailsQuery
import com.pandulapeter.campfire.data.model.domain.recovering
import com.pandulapeter.campfire.data.repository.api.SongDetailsRepository
import com.pandulapeter.campfire.data.repository.implementation.base.RepositoryEnvironment
import com.pandulapeter.campfire.data.source.remote.api.SongDetailsSearchRemoteSources
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.koin.core.annotation.Single

@Single
internal class SongDetailsRepositoryImpl(
    private val songDetailsSearchRemoteSources: SongDetailsSearchRemoteSources,
    private val environment: RepositoryEnvironment,
) : SongDetailsRepository {

    override fun searchSongDetails(query: SongDetailsQuery): Flow<CatalogueSearchResults<SongDetailsCandidate>> = searchCatalogues(
        catalogues = songDetailsSearchRemoteSources.all.map { it.catalogue },
        logger = environment.logger,
        what = "song details lookup",
    ) { catalogue, onBusy ->
        sourceOf(catalogue).searchSongDetails(query = query, onBusy = onBusy)
    }

    override fun loadSongCredits(candidate: SongDetailsCandidate): Flow<SongCreditsState> = channelFlow {
        send(SongCreditsState.Loading)
        val state = environment.logger.recovering(
            describe = { "The songwriters lookup on ${candidate.catalogue} failed: ${it::class.simpleName}" },
            fallback = { SongCreditsState.Failed },
        ) {
            sourceOf(candidate.catalogue).loadSongCredits(candidate = candidate, onBusy = { trySend(SongCreditsState.Busy) })
                ?.let(SongCreditsState::Loaded)
                ?: SongCreditsState.Unavailable
        }
        send(state)
    }

    private fun sourceOf(catalogue: Catalogue) = songDetailsSearchRemoteSources.all.first { it.catalogue == catalogue }
}
