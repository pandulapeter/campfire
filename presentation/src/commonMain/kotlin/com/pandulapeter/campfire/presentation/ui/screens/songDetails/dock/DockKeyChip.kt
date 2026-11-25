/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_expand
import com.pandulapeter.campfire.presentation.resources.song_details_capo
import com.pandulapeter.campfire.presentation.resources.song_details_dock_playing
import com.pandulapeter.campfire.presentation.resources.song_details_key_change
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import org.jetbrains.compose.resources.painterResource

/**
 * The key chip that starts the dock: the key the page's chords are in, in the colour chords are written in, and a second,
 * orange half naming the capo wherever it is not zero, so that a capo is never out of sight. Either half opens the Playing
 * sheet ([onClick], with a chevron saying so), none in read only mode, whose chip only reads. A song naming no key that
 * the chip still opens the sheet for is named by the sheet. A new key crossfades, and the capo half slides in and out.
 */
@Composable
internal fun DockKeyChip(
    modifier: Modifier = Modifier,
    dockKey: DockKey,
    height: Dp,
    onClick: (() -> Unit)?,
) {
    val playing = stringResource(Res.string.song_details_dock_playing)
    // Kept while its half slides out, so that it leaves naming the capo it had.
    val lastCapo = remember { LastCapo() }
    dockKey.capo?.let { lastCapo.value = it }
    Row(
        modifier = modifier
            .height(height)
            .clip(FilterChipDefaults.shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, FilterChipDefaults.shape)
            .then(if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClickLabel = playing, onClick = onClick)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val key = dockKey.key ?: playing.takeIf { onClick != null }
        if (key != null) {
            val keyDescription = dockKey.key?.let { textResource(Res.string.song_details_key_change, it) } ?: playing
            AnimatedContent(
                modifier = Modifier
                    .padding(start = KEY_CHIP_PADDING, end = if (onClick == null) KEY_CHIP_PADDING else 0.dp)
                    .semantics { contentDescription = keyDescription },
                targetState = key,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentAlignment = Alignment.Center,
            ) { shownKey ->
                Text(
                    text = shownKey,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (dockKey.key == null) MaterialTheme.colorScheme.onSurfaceVariant else LocalSecondAccentColor.current,
                    maxLines = 1,
                )
            }
            if (onClick != null) {
                Icon(
                    modifier = Modifier.padding(horizontal = CHEVRON_PADDING).size(CHEVRON_SIZE),
                    painter = painterResource(Res.drawable.ic_expand),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(
            modifier = Modifier.fillMaxHeight(),
            visible = dockKey.capo != null,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = KEY_CHIP_PADDING),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.song_details_capo, lastCapo.value),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The capo [DockKeyChip] last named. Not a state: it is written and read in the same composition. */
private class LastCapo(var value: Int = 0)

private val KEY_CHIP_PADDING = 12.dp
private val CHEVRON_PADDING = 4.dp
private val CHEVRON_SIZE = 18.dp
