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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_capo
import com.pandulapeter.campfire.presentation.resources.song_details_dock_playing
import com.pandulapeter.campfire.presentation.resources.song_details_key_change
import com.pandulapeter.campfire.presentation.ui.components.controls.StepperDivider
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor

/**
 * The key chip that starts the dock: one tonal pill ([DockControlPill], the dock's way of saying "this opens a sheet",
 * set apart from the outlined chord and scale chips that are the song's) naming the key the page's chords are in, in the
 * colour chords are written in, and after a stepper's divider the capo wherever it is not zero, so that a capo
 * is never out of sight. The whole pill opens the Playing sheet ([onClick]), none in read only mode, whose chip only
 * reads. A song naming no key that the chip still opens the sheet for is named by the sheet. A new key crossfades, and
 * the capo slides in and out with its divider.
 */
@Composable
internal fun DockKeyChip(
    modifier: Modifier = Modifier,
    dockKey: DockKey,
    height: Dp,
    onClick: (() -> Unit)?,
) {
    val playing = stringResource(Res.string.song_details_dock_playing)
    // Kept while it slides out, so that it leaves naming the capo it had.
    val lastCapo = remember { LastCapo() }
    dockKey.capo?.let { lastCapo.value = it }
    DockControlPill(
        modifier = modifier.height(height),
        onClickLabel = playing,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val key = dockKey.key ?: playing.takeIf { onClick != null }
            if (key != null) {
                val keyDescription = dockKey.key?.let { textResource(Res.string.song_details_key_change, it) } ?: playing
                AnimatedContent(
                    modifier = Modifier
                        .padding(horizontal = KEY_CHIP_PADDING)
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
            }
            AnimatedVisibility(
                modifier = Modifier.fillMaxHeight(),
                visible = dockKey.capo != null,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (key != null) StepperDivider()
                    Text(
                        modifier = Modifier.padding(horizontal = KEY_CHIP_PADDING),
                        text = stringResource(Res.string.song_details_capo, lastCapo.value),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** The capo [DockKeyChip] last named. Not a state: it is written and read in the same composition. */
private class LastCapo(var value: Int = 0)

private val KEY_CHIP_PADDING = 12.dp
