/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.screenshots

import com.pandulapeter.campfire.data.model.catalogue.CoverArtQuery
import com.pandulapeter.campfire.data.model.catalogue.CoverArtSearchResults
import com.pandulapeter.campfire.data.repository.api.CoverArtRepository
import kotlinx.coroutines.flow.flowOf
import org.koin.dsl.module

/** What the UI tests start the app with on top of `Fakes.kt`'s, in place of what would reach outside. */
internal val testFakes = module {
    single<CoverArtRepository> { OfflineCoverArtRepository() }
}

/**
 * Covers as a device without a network sees them: no song's image can be had, and every catalogue a search asks has
 * already answered it with nothing, so the search's first frame is its last.
 */
private class OfflineCoverArtRepository : CoverArtRepository {
    override suspend fun getCoverArt(url: String): ByteArray? = null

    override fun searchCoverArt(query: CoverArtQuery) = flowOf(
        CoverArtSearchResults(candidates = emptyList(), pending = emptySet(), busy = emptySet(), failed = emptySet()),
    )

    override val coverArtCacheSize = flowOf<Long?>(0L)

    override suspend fun clearCoverArtCache() = Unit
}
