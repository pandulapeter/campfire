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

interface GetCoverArtUseCase {

    /**
     * The bytes of the cover image at [url], from the device's own copy or downloaded and kept, or null where there is
     * none to show. See `CoverArtRepository.getCoverArt` for how failures are remembered.
     */
    suspend operator fun invoke(url: String): ByteArray?
}
