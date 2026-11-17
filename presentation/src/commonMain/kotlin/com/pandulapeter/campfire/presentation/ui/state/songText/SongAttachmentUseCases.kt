/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.songText

import com.pandulapeter.campfire.domain.api.export.ExportAttachmentUseCase
import com.pandulapeter.campfire.domain.api.images.AddDraftAttachmentUseCase
import com.pandulapeter.campfire.domain.api.images.HoldPickedAttachmentsUseCase
import com.pandulapeter.campfire.domain.api.images.SaveSongAttachmentsUseCase
import org.koin.core.annotation.Factory

/** The use cases [SongAttachmentEditing] keeps, writes, and opens a song's attachments with, part of [SongTextUseCases]. */
@Factory
class SongAttachmentUseCases(
    internal val addDraftAttachment: AddDraftAttachmentUseCase,
    internal val saveSongAttachments: SaveSongAttachmentsUseCase,
    internal val exportAttachment: ExportAttachmentUseCase,
    internal val holdPickedAttachments: HoldPickedAttachmentsUseCase,
)
