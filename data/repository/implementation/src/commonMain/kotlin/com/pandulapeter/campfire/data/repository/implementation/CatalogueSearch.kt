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
import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.recovering
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Asks every one of [catalogues] at once through [search], handed a catalogue and the callback it reports a wait
 * through, and emits where the search is: first with every catalogue pending, then again whenever one of them has to
 * wait or answers. Each is asked in a coroutine of the collector's own, so that cancelling the collection stops all of
 * them, and one that fails is recorded as failed — and logged by its class name alone, since a message may carry the
 * query — rather than ending the others. [what] names the search in that log.
 */
internal fun <T> searchCatalogues(
    catalogues: List<Catalogue>,
    logger: Logger,
    what: String,
    search: suspend (catalogue: Catalogue, onBusy: () -> Unit) -> List<T>,
): Flow<CatalogueSearchResults<T>> = channelFlow {
    val resultsMutex = Mutex()
    var results = CatalogueSearchResults<T>(
        candidates = emptyList(),
        pending = catalogues.toSet(),
        busy = emptySet(),
        failed = emptySet(),
    )
    val update: suspend (CatalogueSearchResults<T>.() -> CatalogueSearchResults<T>) -> Unit = { transform ->
        resultsMutex.withLock {
            results = results.transform()
            send(results)
        }
    }
    send(results)
    catalogues.forEach { catalogue ->
        launch {
            val candidates = logger.recovering(
                describe = { "The $what on $catalogue failed: ${it::class.simpleName}" },
                fallback = { null },
            ) {
                search(
                    catalogue,
                    // A wait reported by a catalogue that has answered since is not one anybody is still waiting on.
                    { launch { update { if (catalogue in pending) copy(busy = busy + catalogue) else this } } },
                )
            }
            update {
                copy(
                    candidates = this.candidates + candidates.orEmpty(),
                    pending = pending - catalogue,
                    busy = busy - catalogue,
                    failed = if (candidates == null) failed + catalogue else failed,
                )
            }
        }
    }
}
