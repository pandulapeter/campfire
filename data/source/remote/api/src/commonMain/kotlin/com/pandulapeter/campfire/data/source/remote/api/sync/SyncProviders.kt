/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.api.sync

import com.pandulapeter.campfire.data.model.sync.SyncProviderId

/**
 * Every provider the build has credentials for, as one dependency. A provider the build cannot use is not in [all]
 * at all, so an empty list is how a build without sync credentials looks from above.
 *
 * This is a type of its own rather than a `List<SyncProvider>` because the Koin compiler plugin resolves a `List<T>`
 * parameter as `getAll<T>()`, every definition bound to `T`, and never as a definition whose type is the list: a
 * `List<SyncProvider>` parameter compiles, passes the plugin's graph check, and is always empty.
 *
 * @param loadStoredProviderId Which provider the stored credentials name, read without any provider; see [stored].
 */
class SyncProviders(
    val all: List<SyncProvider>,
    private val loadStoredProviderId: suspend () -> SyncProviderId?,
) {

    fun byId(id: SyncProviderId) = all.firstOrNull { it.id == id }

    /**
     * The provider the stored credentials belong to, read from what is stored rather than by asking each provider,
     * so that no provider has to tell its own credentials from another's to keep from looking connected. Null when
     * nothing is stored, or when it belongs to a provider this build does not have. Whether the credentials still
     * hold a connection is [SyncConnection.isConnected]'s to say.
     *
     * Throws what reading the credentials throws: credentials that cannot be read right now are not "none".
     */
    suspend fun stored(): SyncProvider? = if (all.isEmpty()) null else loadStoredProviderId()?.let(::byId)

    /** [stored], when its credentials hold a connection: the provider that is connected, the one runs go to. */
    suspend fun connected() = stored()?.takeIf { it.isConnected() }
}
