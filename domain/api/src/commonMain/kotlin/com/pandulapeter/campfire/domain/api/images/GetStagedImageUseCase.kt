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

import com.pandulapeter.campfire.data.model.importing.StagedFile

interface GetStagedImageUseCase {

    /**
     * The bytes of the picture an import under review staged as [file], or null where it is gone, cannot be read, or is
     * larger than a library picture may be: what its preview shows it from before Import moves it into the library. See
     * `ImageRepository.loadStagedImage`.
     */
    suspend operator fun invoke(file: StagedFile): ByteArray?
}
