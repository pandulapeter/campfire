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

import com.pandulapeter.campfire.data.model.library.LibraryFileSizes
import kotlinx.coroutines.flow.StateFlow

interface GetLibraryFileSizesUseCase {

    /**
     * What the library's pictures and attachments take up on disk, listed again whenever one of them is written or
     * deleted; null until first listed. See `ImageRepository.librarySizes`.
     */
    operator fun invoke(): StateFlow<LibraryFileSizes?>
}
