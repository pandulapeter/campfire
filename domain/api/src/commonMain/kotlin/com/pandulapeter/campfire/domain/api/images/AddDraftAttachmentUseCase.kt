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

interface AddDraftAttachmentUseCase {

    /**
     * Keeps [bytes], a file picked to attach to a song, on the draft shelf, outside the library, and answers the name its
     * attachment line is to name it by, or why it was refused: larger than `ImportLimits.MAX_ATTACHMENT_FILE_SIZE`, or an
     * [extension] (the picked file's) not among `LibraryFiles.ATTACHMENT_EXTENSIONS`. Nothing reaches the library before
     * [SaveWithDraftImagesUseCase] or [SaveSongAttachmentsUseCase] runs; until then it goes as the draft's pictures go
     * ([ClearDraftImagesUseCase]). Once started, it finishes even if the caller is cancelled. See
     * `ImageRepository.addDraftAttachment`.
     */
    suspend operator fun invoke(bytes: ByteArray, extension: String): ImageIntake
}
