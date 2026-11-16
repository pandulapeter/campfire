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

import kotlinx.coroutines.flow.StateFlow

interface GetImageChangesUseCase {

    /**
     * Changes whenever one of the library's pictures is written or deleted, by sync too: whoever shows a picture as
     * missing asks for it again. Names no picture, and may skip values in between.
     */
    operator fun invoke(): StateFlow<Long>
}
