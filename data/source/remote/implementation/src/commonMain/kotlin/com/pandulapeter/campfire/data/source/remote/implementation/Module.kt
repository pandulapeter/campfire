/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation

import com.pandulapeter.campfire.data.source.remote.api.PendingAuthorizationStore
import com.pandulapeter.campfire.data.source.remote.api.SyncAuthenticator
import com.pandulapeter.campfire.data.source.remote.api.SyncProvider
import com.pandulapeter.campfire.data.source.remote.implementation.auth.PendingAuthorizationStoreImpl
import com.pandulapeter.campfire.data.source.remote.implementation.auth.SyncCredentialsStore
import com.pandulapeter.campfire.data.source.remote.implementation.auth.createSyncAuthenticator
import com.pandulapeter.campfire.data.source.remote.implementation.dropbox.DropboxSyncProvider
import com.pandulapeter.campfire.data.source.remote.implementation.network.createHttpClient
import io.ktor.client.HttpClient
import org.koin.dsl.module

val dataRemoteSourceModule = module {
    single<HttpClient> { createHttpClient() }
    single { SyncCredentialsStore(get()) }
    single<PendingAuthorizationStore> { PendingAuthorizationStoreImpl(get()) }
    single<SyncAuthenticator> { createSyncAuthenticator() }
    // A list rather than a single binding: adding a provider is adding one line here, and nothing above this file
    // learns that there is more than one. Sync itself picks by SyncProviderId, see SyncRepository.
    //
    // A provider the build has no credentials for is left out entirely rather than offered and then failing: the
    // settings screen shows what is in this list, so an unconfigured build says so instead of inviting the user to
    // press a button that cannot work.
    single<List<SyncProvider>> {
        buildList {
            if (DROPBOX_APP_KEY.isNotEmpty()) {
                add(DropboxSyncProvider(httpClient = get(), credentialsStore = get(), appKey = DROPBOX_APP_KEY))
            }
        }
    }
}
