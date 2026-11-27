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

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.library.LibraryCollections
import com.pandulapeter.campfire.data.source.remote.api.catalogue.CoverArtSearchRemoteSources
import com.pandulapeter.campfire.data.source.remote.api.catalogue.SongDetailsSearchRemoteSources
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncProviders
import com.pandulapeter.campfire.data.source.remote.implementation.auth.OAuthRedirectConfiguration
import com.pandulapeter.campfire.data.source.remote.implementation.auth.SyncCredentialsStore
import com.pandulapeter.campfire.data.source.remote.implementation.dropbox.DropboxConfiguration
import com.pandulapeter.campfire.data.source.remote.implementation.dropbox.DropboxSyncProvider
import com.pandulapeter.campfire.data.source.remote.implementation.iTunes.ITunesRemoteSource
import com.pandulapeter.campfire.data.source.remote.implementation.musicBrainz.MusicBrainzRemoteSource
import com.pandulapeter.campfire.data.source.remote.implementation.musicBrainz.MusicBrainzRateLimiter
import com.pandulapeter.campfire.data.source.remote.implementation.network.HttpClientHolder
import com.pandulapeter.campfire.data.source.remote.implementation.network.createHttpClient
import kotlinx.coroutines.Dispatchers
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

@Module
@ComponentScan
object DataRemoteSourceModule {

    @Single
    internal fun httpClientHolder(): HttpClientHolder = HttpClientHolder(
        dispatcher = Dispatchers.Default,
        create = { createHttpClient(userAgent = USER_AGENT) },
    )

    /**
     * Campfire's redirect, which the platforms' authenticators are built with (see [CAMPFIRE_OAUTH_REDIRECT]), on the
     * scheme of the build the shell says it is.
     */
    @Single
    internal fun oAuthRedirectConfiguration(@Provided scheme: SyncRedirectScheme): OAuthRedirectConfiguration = scheme.redirect

    /**
     * A list rather than a single binding: adding a provider is adding one line here, and nothing above this module
     * learns that there is more than one. Sync itself picks the one the stored credentials name.
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
                        configuration = DropboxConfiguration(
                            appKey = DROPBOX_APP_KEY,
                            collections = LibraryCollections.all,
                            maxDocumentSize = LibraryCollections.MAX_FOLDER_DOCUMENT_SIZE,
                        ),
                        logger = logger,
                    ),
                )
            }
        },
        loadStoredProviderId = credentialsStore::storedProviderId,
    )

    /**
     * Built here rather than declared on the class, since what it is built with — the clock its requests are spaced by
     * — is the one thing its tests replace. 1.1 s rather than the 1 s MusicBrainz allows, to stay under the average it
     * counts however the two clocks disagree. One instance for the cover search and the song details lookup alike,
     * and so one limiter: MusicBrainz counts the app's requests, whichever sheet is asking.
     */
    @Single
    internal fun musicBrainzRemoteSource(httpClientHolder: HttpClientHolder): MusicBrainzRemoteSource = MusicBrainzRemoteSource(
        httpClientHolder = httpClientHolder,
        rateLimiter = MusicBrainzRateLimiter(timeSource = TimeSource.Monotonic, interval = MUSIC_BRAINZ_REQUEST_INTERVAL),
    )

    @Single
    internal fun iTunesRemoteSource(httpClientHolder: HttpClientHolder): ITunesRemoteSource = ITunesRemoteSource(httpClientHolder = httpClientHolder)

    /**
     * MusicBrainz comes first, since where both answer at once its release groups, one for every edition of a record,
     * are the tidier list to start with.
     */
    @Single
    internal fun coverArtSearchRemoteSources(
        musicBrainzRemoteSource: MusicBrainzRemoteSource,
        iTunesRemoteSource: ITunesRemoteSource,
    ): CoverArtSearchRemoteSources = CoverArtSearchRemoteSources(all = listOf(musicBrainzRemoteSource, iTunesRemoteSource))

    @Single
    internal fun songDetailsSearchRemoteSources(
        musicBrainzRemoteSource: MusicBrainzRemoteSource,
        iTunesRemoteSource: ITunesRemoteSource,
    ): SongDetailsSearchRemoteSources = SongDetailsSearchRemoteSources(all = listOf(musicBrainzRemoteSource, iTunesRemoteSource))

    private val MUSIC_BRAINZ_REQUEST_INTERVAL = 1_100.milliseconds
}
