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

interface ExamineImageUseCase {

    /**
     * What keeping [bytes] would come to now, with nothing written: the name the library would keep it under, or why it
     * would be refused. What a picture the user adds is judged by before it is shrunk or converted. See
     * `ImageRepository.libraryNameOf`.
     */
    suspend operator fun invoke(bytes: ByteArray): ImageIntake
}
