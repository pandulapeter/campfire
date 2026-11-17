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

interface HoldPickedAttachmentsUseCase {

    /**
     * Keeps [names], the files an open Attachments sheet picked onto the draft shelf and has not saved yet, from the shelf's
     * tidying, which an editor closing elsewhere starts: Save would otherwise name a file that is no longer there. An empty
     * set once no sheet is open. Each call replaces the last, and it returns once the hold is in place. See
     * `ImageRepository.holdPickedAttachments`.
     */
    suspend operator fun invoke(names: Set<String>)
}
