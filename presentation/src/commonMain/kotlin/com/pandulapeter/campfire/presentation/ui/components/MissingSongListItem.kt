/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.setlists_missing_song

/**
 * A setlist entry whose file is no longer in the library: it cannot be opened, but it can still be removed, so it is
 * shown greyed out rather than silently dropped - a setlist that quietly loses a song would look like the app lost it.
 *
 * @param onLongClick A shortcut to the row's overflow menu, as on a present song's row ([SongListItem]). It is a long
 *   press alone rather than a clickable, since a tap has nothing to open and a clickable would offer one to a screen
 *   reader all the same.
 */
@Composable
internal fun MissingSongListItem(
    modifier: Modifier = Modifier,
    index: Int,
    songFileName: String,
    cardPadding: PaddingValues = PaddingValues(horizontal = SONG_CARD_OUTER_PADDING, vertical = SONG_CARD_VERTICAL_PADDING),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    shadowElevation: Dp = 0.dp,
    onLongClick: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) = Surface(
    modifier = modifier.fillMaxWidth().padding(cardPadding).alpha(0.5f),
    shape = MaterialTheme.shapes.medium,
    color = containerColor,
    shadowElevation = shadowElevation,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    CenteredSongCardContent(
        modifier = if (onLongClick == null) {
            Modifier
        } else {
            Modifier.pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnLongClick?.invoke()
                    },
                )
            }
        },
        actions = actions,
        headlineContent = {
            ListItemHeadline(text = songCardTitle(songFileName, index))
        },
        supportingContent = {
            Text(
                text = stringResource(Res.string.setlists_missing_song),
                fontStyle = FontStyle.Italic,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}
