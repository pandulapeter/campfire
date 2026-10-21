/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.sharing

import com.pandulapeter.campfire.data.model.share.Share
import kotlinx.coroutines.flow.Flow

interface GetSharedLinksUseCase {

    /**
     * Everything shared from the connected account, on any device, as this device last saw it, most recently shared
     * first; empty once disconnected. Never asks the service, so it is right at once and offline:
     * [LoadSharedLinksUseCase] reads the account's list again.
     */
    operator fun invoke(): Flow<List<Share>>
}
