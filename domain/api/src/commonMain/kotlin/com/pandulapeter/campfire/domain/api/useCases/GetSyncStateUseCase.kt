/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.domain.SyncProviderId
import com.pandulapeter.campfire.data.model.domain.SyncState
import kotlinx.coroutines.flow.Flow

interface GetSyncStateUseCase {

    /**
     * Separate from [GetScreenDataUseCase] for the same reason the preferences are: sync has its own pace, and a
     * settings screen must not wait for a scan of the whole library to say whether an account is connected.
     */
    operator fun invoke(): Flow<SyncState>
}

interface GetSyncProvidersUseCase {

    /** The services this build can connect to, empty when it was not given the credentials for any of them. */
    operator fun invoke(): List<SyncProviderId>
}
