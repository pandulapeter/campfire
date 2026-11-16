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

interface ClearDraftImagesUseCase {

    /**
     * Deletes every picture [AddDraftImageUseCase] kept, once the editor they were kept for has nothing unsaved left:
     * what its text named is in the library by then, or was discarded. Once started, it finishes even if the caller is
     * cancelled.
     */
    suspend operator fun invoke()
}
