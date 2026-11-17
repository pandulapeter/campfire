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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.model.ChordProAttachment
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_attachment_missing
import com.pandulapeter.campfire.presentation.ui.components.formattedSize
import com.pandulapeter.campfire.presentation.ui.components.scaled

/**
 * The song's attachments, one full-width row each in the file's order, drawn like [SongInfoLinkButtons] (an outlined
 * button inside a tile whose every other spot opens the attachments' editor) and as tall as one: the file's icon, its
 * name, and its size, a press opening it ([SongInfoAttachments.onOpen]). One whose file is not on this device is the
 * same row disabled, saying so in place of the size, since there is nothing to open until sync brings it.
 */
@Composable
internal fun SongInfoAttachmentRows(
    attachments: List<ChordProAttachment>,
    info: SongInfoAttachments,
    fontScale: Float,
) = Column(modifier = Modifier.fillMaxWidth().withoutTouchTargetSlack(fontScale)) {
    attachments.forEach { attachment ->
        val size = info.fileSizes[attachment.source]
        SongInfoAttachmentRow(
            attachment = attachment,
            detail = if (size == null) stringResource(Res.string.song_details_attachment_missing) else formattedSize(size),
            fontScale = fontScale,
            onClick = info.onOpen?.takeIf { size != null }?.let { onOpen -> { onOpen(attachment) } },
        )
    }
}

@Composable
private fun SongInfoAttachmentRow(
    attachment: ChordProAttachment,
    detail: String,
    fontScale: Float,
    onClick: (() -> Unit)?,
) = OutlinedButton(
    modifier = Modifier.fillMaxWidth().heightIn(min = songInfoLinkRowHeight(fontScale)),
    enabled = onClick != null,
    onClick = { onClick?.invoke() },
    contentPadding = PaddingValues(start = 12.dp * fontScale, end = 16.dp * fontScale),
) {
    Icon(
        modifier = Modifier.padding(end = 8.dp * fontScale).size(ATTACHMENT_ICON_SIZE * fontScale),
        painter = attachmentIcon(attachment),
        contentDescription = null,
    )
    Text(
        modifier = Modifier.weight(1f),
        text = attachmentLabel(attachment),
        style = MaterialTheme.typography.labelLarge.scaled(fontScale),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        modifier = Modifier.padding(start = 8.dp * fontScale),
        text = detail,
        style = MaterialTheme.typography.labelMedium.scaled(fontScale),
        maxLines = 1,
    )
}

private val ATTACHMENT_ICON_SIZE = 18.dp
