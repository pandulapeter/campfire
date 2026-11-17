/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songInfo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.pandulapeter.campfire.chordpro.model.ChordProAttachment
import com.pandulapeter.campfire.data.model.library.AttachmentKind
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_attachment_audio
import com.pandulapeter.campfire.presentation.resources.ic_attachment_document
import com.pandulapeter.campfire.presentation.resources.ic_attachment_file
import com.pandulapeter.campfire.presentation.resources.ic_attachment_video
import com.pandulapeter.campfire.presentation.resources.ic_image
import com.pandulapeter.campfire.presentation.resources.ic_scale_notes
import com.pandulapeter.campfire.presentation.resources.song_details_attachment_audio
import com.pandulapeter.campfire.presentation.resources.song_details_attachment_document
import com.pandulapeter.campfire.presentation.resources.song_details_attachment_music_notation
import com.pandulapeter.campfire.presentation.resources.song_details_attachment_other
import com.pandulapeter.campfire.presentation.resources.song_details_attachment_picture
import com.pandulapeter.campfire.presentation.resources.song_details_attachment_video
import org.jetbrains.compose.resources.painterResource

/**
 * What [SongInfoBody] shows of a song's attachments besides the file: [fileSizes] (`LibraryFileSizes.fileSizes`), which
 * tells one on this device, with its size, from one that is not; and [onOpen], which opens one that is, null where the
 * rows only show (the editor preview's card, whose tile edits them).
 */
@Immutable
internal class SongInfoAttachments(
    val fileSizes: Map<String, Long>,
    val onOpen: ((ChordProAttachment) -> Unit)?,
)

/** The name an attachment is shown by: its own, else what its file is ("Document"). */
@Composable
internal fun attachmentLabel(attachment: ChordProAttachment): String =
    attachment.name?.takeIf { it.isNotBlank() } ?: attachmentKindLabel(AttachmentKind.of(attachment.source))

/** What a file of [kind] is called where its attachment has no name of its own. */
@Composable
internal fun attachmentKindLabel(kind: AttachmentKind): String =
    stringResource(
        when (kind) {
            AttachmentKind.DOCUMENT -> Res.string.song_details_attachment_document
            AttachmentKind.AUDIO -> Res.string.song_details_attachment_audio
            AttachmentKind.VIDEO -> Res.string.song_details_attachment_video
            AttachmentKind.PICTURE -> Res.string.song_details_attachment_picture
            AttachmentKind.MUSIC_NOTATION -> Res.string.song_details_attachment_music_notation
            AttachmentKind.OTHER -> Res.string.song_details_attachment_other
        }
    )

/** The icon of what the file of [attachment] holds. */
@Composable
internal fun attachmentIcon(attachment: ChordProAttachment) = painterResource(
    when (AttachmentKind.of(attachment.source)) {
        AttachmentKind.DOCUMENT -> Res.drawable.ic_attachment_document
        AttachmentKind.AUDIO -> Res.drawable.ic_attachment_audio
        AttachmentKind.VIDEO -> Res.drawable.ic_attachment_video
        AttachmentKind.PICTURE -> Res.drawable.ic_image
        AttachmentKind.MUSIC_NOTATION -> Res.drawable.ic_scale_notes
        AttachmentKind.OTHER -> Res.drawable.ic_attachment_file
    }
)
