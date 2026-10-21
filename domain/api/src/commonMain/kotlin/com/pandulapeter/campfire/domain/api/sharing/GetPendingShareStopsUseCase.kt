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

import kotlinx.coroutines.flow.Flow

interface GetPendingShareStopsUseCase {

    /**
     * Whether this device has deleted songs or setlists that the account's list has not been told of yet, any of which
     * may be shared: disconnecting forgets those stops, and leaves such a link working.
     */
    operator fun invoke(): Flow<Boolean>
}
