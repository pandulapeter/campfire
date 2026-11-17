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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.library.AttachmentKind
import com.pandulapeter.campfire.presentation.ui.platform.rememberFileOpener
import com.pandulapeter.campfire.presentation.ui.state.songText.SongAttachmentEditing

/**
 * What the About the song sheet shows of a song's attachments: which this device holds, and opening one with the app the
 * system picks, under the name its row shows (`SongAttachmentEditing.open`).
 */
@Composable
internal fun rememberSongInfoAttachments(songAttachmentEditing: SongAttachmentEditing): SongInfoAttachments {
    val fileSizes by songAttachmentEditing.fileSizes.collectAsStateWithLifecycle()
    val opener = rememberFileOpener()
    val kindLabels = AttachmentKind.entries.associateWith { attachmentKindLabel(it) }
    return remember(songAttachmentEditing, fileSizes, opener, kindLabels) {
        SongInfoAttachments(fileSizes = fileSizes) { attachment ->
            val name = attachment.name?.takeIf { it.isNotBlank() } ?: kindLabels.getValue(AttachmentKind.of(attachment.source))
            songAttachmentEditing.open(attachment = attachment, name = name, opener = opener)
        }
    }
}
