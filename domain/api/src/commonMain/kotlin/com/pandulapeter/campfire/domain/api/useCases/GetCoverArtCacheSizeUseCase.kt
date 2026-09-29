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

import kotlinx.coroutines.flow.Flow

interface GetCoverArtCacheSizeUseCase {

    /**
     * The bytes the device's copies of the covers take up, or null while they cannot be listed, updated as copies are
     * written and pruned for as long as it is collected. See `CoverArtRepository.coverArtCacheSize`.
     */
    operator fun invoke(): Flow<Long?>
}
