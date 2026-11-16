/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.images

import com.pandulapeter.campfire.data.model.image.ImageSize

interface GetImageSizeUseCase {

    /**
     * The size the library's picture called [name] is shown at before scaling, read from its header and cached, or null
     * where the library holds no picture under that name that every platform can show. See `ImageRepository.loadImageSize`.
     */
    suspend operator fun invoke(name: String): ImageSize?
}
