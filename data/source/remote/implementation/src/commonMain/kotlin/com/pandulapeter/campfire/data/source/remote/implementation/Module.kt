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

import com.pandulapeter.campfire.data.model.domain.Logger
import com.pandulapeter.campfire.data.source.remote.api.CoverArtSearchRemoteSources
import com.pandulapeter.campfire.data.source.remote.api.SyncProviders
import com.pandulapeter.campfire.data.source.remote.implementation.auth.SyncCredentialsStore
import com.pandulapeter.campfire.data.source.remote.implementation.dropbox.DropboxSyncProvider
import com.pandulapeter.campfire.data.source.remote.implementation.iTunes.ITunesCoverArtSearchRemoteSource
import com.pandulapeter.campfire.data.source.remote.implementation.musicBrainz.MusicBrainzCoverArtSearchRemoteSource
import com.pandulapeter.campfire.data.source.remote.implementation.musicBrainz.MusicBrainzRateLimiter
import com.pandulapeter.campfire.data.source.remote.implementation.network.HttpClientHolder
import com.pandulapeter.campfire.data.source.remote.implementation.network.createHttpClient
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

@Module
@ComponentScan
object DataRemoteSourceModule {

    @Single
    internal fun httpClientHolder(): HttpClientHolder = HttpClientHolder(create = ::createHttpClient)

    /**
     * A list rather than a single binding: adding a provider is adding one line here, and nothing above this module
     * learns that there is more than one. Sync itself picks by SyncProviderId, see SyncRepository.
     *
     * A provider the build has no credentials for is left out entirely rather than offered and then failing: the
     * settings screen shows what is in this list, so an unconfigured build says so instead of inviting the user to
     * press a button that cannot work.
     */
    @Single
    internal fun syncProviders(
        httpClientHolder: HttpClientHolder,
        credentialsStore: SyncCredentialsStore,
        logger: Logger,
    ): SyncProviders = SyncProviders(
        all = buildList {
            if (DROPBOX_APP_KEY.isNotEmpty()) {
                add(
                    DropboxSyncProvider(
                        httpClientHolder = httpClientHolder,
                        credentialsStore = credentialsStore,
                        appKey = DROPBOX_APP_KEY,
                        logger = logger,
                    ),
                )
            }
        },
    )

    /**
     * Built here rather than declared on the classes, since what the MusicBrainz source is built with — the clock its
     * requests are spaced by — is the one thing its tests replace. 1.1 s rather than the 1 s MusicBrainz allows, to stay
     * under the average it counts however the two clocks disagree. MusicBrainz comes first, since where both answer
     * at once its release groups, one for every edition of a record, are the tidier list to start with.
     */
    @Single
    internal fun coverArtSearchRemoteSources(httpClientHolder: HttpClientHolder): CoverArtSearchRemoteSources = CoverArtSearchRemoteSources(
        all = listOf(
            MusicBrainzCoverArtSearchRemoteSource(
                httpClientHolder = httpClientHolder,
                rateLimiter = MusicBrainzRateLimiter(timeSource = TimeSource.Monotonic, interval = MUSIC_BRAINZ_REQUEST_INTERVAL),
            ),
            ITunesCoverArtSearchRemoteSource(httpClientHolder = httpClientHolder),
        ),
    )

    private val MUSIC_BRAINZ_REQUEST_INTERVAL = 1_100.milliseconds
}
