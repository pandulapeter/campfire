/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.pandulapeter.campfire.chordpro.model.ChordProLink
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_link
import com.pandulapeter.campfire.presentation.ui.components.scaled
import org.jetbrains.compose.resources.painterResource

/**
 * The song's links as [SongInfoLinkButton]s, in the order the file and the dialog that edits them give them, unlike the
 * tags and the languages, since that order is one the user chose. The rows have no gap between them: each button already
 * stands in the 48dp touch target Material keeps around it, which leaves room enough between two rows of them.
 */
@Composable
internal fun SongInfoLinkButtons(
    links: List<ChordProLink>,
    fontScale: Float,
    onOpenLink: (String) -> Unit,
) = FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp * fontScale),
) {
    links.forEach { link ->
        SongInfoLinkButton(
            link = link,
            fontScale = fontScale,
            onClick = { onOpenLink(link.url) },
        )
    }
}

/**
 * One link of the song's info, named the way [linkLabel] names it, as an outlined button rather than a chip: it opens
 * the page, inside a tile whose every other spot opens the links' editor, so it has to look like a control of its own
 * and be hit like one. It is taller than the tags' pills and keeps Material's 48dp touch target around it, but stays a
 * button rather than a row, so that the tile reads like every other group of the sheet.
 */
@Composable
private fun SongInfoLinkButton(
    link: ChordProLink,
    fontScale: Float,
    onClick: () -> Unit,
) = OutlinedButton(
    modifier = Modifier.heightIn(min = LINK_BUTTON_HEIGHT * fontScale).widthIn(max = LINK_BUTTON_MAX_WIDTH * fontScale),
    onClick = onClick,
    contentPadding = PaddingValues(start = 12.dp * fontScale, end = 16.dp * fontScale),
) {
    Icon(
        modifier = Modifier.padding(end = 8.dp * fontScale).size(LINK_ICON_SIZE * fontScale),
        painter = painterResource(Res.drawable.ic_link),
        contentDescription = null,
    )
    Text(
        text = linkLabel(link),
        style = MaterialTheme.typography.labelLarge.scaled(fontScale),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The height a row of [SongInfoLinkButtons] takes: the button's, or the touch target Material keeps around it where that
 * is taller, which the tiles of the sheet take for their own minimum.
 */
@Composable
internal fun songInfoLinkRowHeight(fontScale: Float): Dp = (LINK_BUTTON_HEIGHT * fontScale).let { buttonHeight ->
    val touchTarget = LocalMinimumInteractiveComponentSize.current
    if (touchTarget.isSpecified) maxOf(buttonHeight, touchTarget) else buttonHeight
}

private val LINK_BUTTON_HEIGHT = 36.dp
private val LINK_ICON_SIZE = 18.dp

/** As wide as a tag may grow and a little more, since a link's name is usually a few words rather than one. */
private val LINK_BUTTON_MAX_WIDTH = 280.dp
