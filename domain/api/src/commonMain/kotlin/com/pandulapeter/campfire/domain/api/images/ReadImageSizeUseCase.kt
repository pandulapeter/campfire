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

interface ReadImageSizeUseCase {

    /**
     * The size the picture [bytes] holds is shown at before scaling, from its header alone, or null where it is not a
     * picture every platform can show: for a picture no shelf keeps yet, one an import under review brings. See
     * `ImageRepository.readImageSize`.
     */
    suspend operator fun invoke(bytes: ByteArray): ImageSize?
}
