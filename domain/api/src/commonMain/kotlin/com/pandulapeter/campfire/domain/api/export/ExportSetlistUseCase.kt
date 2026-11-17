/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.export

import com.pandulapeter.campfire.data.model.library.ExportedFile

interface ExportSetlistUseCase {

    /**
     * A zip of the setlist, its document unchanged, and the songs in it, so that importing it elsewhere restores both,
     * with the pictures those songs show under `images/`, each once.
     * A file the user saves has its zip's entries stamped with the time. A shared link's copy ([isSharedCopy]) is undated,
     * so the same files always make the same bytes, as a copy compared by its bytes needs, and carries only the attachments
     * that fit beside its songs (`ImportLimits.MAX_SHARED_ATTACHMENTS_SIZE`, in the order the songs name them), counting
     * the rest in `ExportedFile.leftOutAttachmentCount`.
     */
    suspend operator fun invoke(setlistFileName: String, isSharedCopy: Boolean): ExportedFile?
}
