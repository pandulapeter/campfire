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

import com.pandulapeter.campfire.data.model.image.ImageIntake

interface AddDraftImageUseCase {

    /**
     * Keeps [bytes] for the editor's unsaved text, outside the library, and answers the name its `{image}` directive is to
     * name it by, or why it was refused (too large, a format some platform cannot show, or no picture). Nothing reaches
     * the library before [SaveWithDraftImagesUseCase] runs at Save; the picture is deleted with the editor's other draft
     * pictures by [ClearDraftImagesUseCase], or on a launch whose stored draft does not name it. Once started, it finishes
     * even if the caller is cancelled. See `ImageRepository.addDraftImage`.
     */
    suspend operator fun invoke(bytes: ByteArray): ImageIntake
}
