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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
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
import com.pandulapeter.campfire.presentation.resources.ic_tuner
import com.pandulapeter.campfire.presentation.resources.tuner
import com.pandulapeter.campfire.presentation.resources.song_details_capo
import com.pandulapeter.campfire.presentation.resources.song_details_dock_playing
import com.pandulapeter.campfire.presentation.resources.song_details_key_change
import com.pandulapeter.campfire.presentation.ui.components.CampfireTooltip
import com.pandulapeter.campfire.presentation.ui.components.controls.StepperDivider
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import org.jetbrains.compose.resources.painterResource

/**
 * The key chip that starts the dock: one tonal pill ([DockControlPill], the dock's way of saying "this opens a sheet",
 * set apart from the outlined chord and scale chips that are the song's) naming the key the page's chords are in, in the
 * colour chords are written in, and the capo wherever it is not zero, so that a capo is never out of sight. The whole
 * pill opens the Playing sheet ([onClick]), none in read only mode, whose chip only reads. A song naming no key that the
 * chip still opens the sheet for is named by the sheet. A new key crossfades, and the capo slides in and out.
 *
 * [isTall] is the diagram row's, mirroring the tall tempo chip at the row's other end ([TallKeyContent]): as tall as
 * the diagrams, the key set large over the capo, and the tuner ([onTunerClicked], a round button as the tempo chip's
 * play is) beside them where the dock has no tuner button of its own; its sheet has the instrument and tuning pill. Otherwise it is as tall as a chip, the capo after the key beyond a stepper's divider.
 */
@Composable
internal fun DockKeyChip(
    modifier: Modifier = Modifier,
    dockKey: DockKey,
    height: Dp,
    isTall: Boolean = false,
    onTunerClicked: (() -> Unit)? = null,
    onClick: (() -> Unit)?,
) {
    val playing = stringResource(Res.string.song_details_dock_playing)
    // Kept while it slides out, so that it leaves naming the capo it had.
    val lastCapo = remember { LastCapo() }
    dockKey.capo?.let { lastCapo.value = it }
    val key = dockKey.key ?: playing.takeIf { onClick != null }
    val capo = stringResource(Res.string.song_details_capo, lastCapo.value)
    DockControlPill(
        modifier = modifier.height(height),
        shape = if (isTall) RoundedCornerShape(DOCK_TALL_CORNER) else CircleShape,
        onClickLabel = playing,
        onClick = onClick,
    ) {
        val keyText = @Composable { style: TextStyle, keyModifier: Modifier ->
            if (key != null) {
                val keyDescription = dockKey.key?.let { textResource(Res.string.song_details_key_change, it) } ?: playing
                AnimatedContent(
                    modifier = keyModifier.semantics { contentDescription = keyDescription },
                    targetState = key,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    contentAlignment = Alignment.Center,
                ) { shownKey ->
                    Text(
                        text = shownKey,
                        style = style,
                        color = if (dockKey.key == null) MaterialTheme.colorScheme.onSurfaceVariant else LocalSecondAccentColor.current,
                        maxLines = 1,
                    )
                }
            }
        }
        if (isTall) {
            TallKeyContent(
                keyText = { keyText(if (dockKey.key == null) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleLarge, Modifier) },
                capo = capo,
                hasCapo = dockKey.capo != null,
                onTunerClicked = onTunerClicked,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                keyText(MaterialTheme.typography.labelLarge, Modifier.padding(horizontal = KEY_CHIP_PADDING))
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
                            text = capo,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The tall [DockKeyChip]'s content: [keyText] over the [capo] while there is one ([hasCapo]), and the round tuner button
 * after them where there is [onTunerClicked], the two never narrower than [TALL_KEY_MIN_WIDTH] without it, so that a
 * key of one letter still reads as the control it is.
 */
@Composable
private fun TallKeyContent(
    keyText: @Composable () -> Unit,
    capo: String,
    hasCapo: Boolean,
    onTunerClicked: (() -> Unit)?,
) = Row(
    modifier = Modifier.padding(start = KEY_CHIP_PADDING, end = if (onTunerClicked == null) KEY_CHIP_PADDING else TALL_BUTTON_PADDING),
    horizontalArrangement = Arrangement.spacedBy(KEY_CHIP_PADDING),
    verticalAlignment = Alignment.CenterVertically,
) {
    Column(
        modifier = if (onTunerClicked == null) Modifier.widthIn(min = TALL_KEY_MIN_WIDTH - KEY_CHIP_PADDING * 2) else Modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        keyText()
        AnimatedVisibility(
            visible = hasCapo,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Text(text = capo, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
    if (onTunerClicked != null) {
        val label = stringResource(Res.string.tuner)
        CampfireTooltip(label = label) {
            FilledTonalIconButton(
                modifier = Modifier
                    .size(DOCK_ROUND_BUTTON_SIZE)
                    .semantics { contentDescription = label },
                onClick = onTunerClicked,
            ) {
                Icon(
                    modifier = Modifier.size(TUNER_ICON_SIZE),
                    painter = painterResource(Res.drawable.ic_tuner),
                    contentDescription = null,
                )
            }
        }
    }
}

/** The capo [DockKeyChip] last named. Not a state: it is written and read in the same composition. */
private class LastCapo(var value: Int = 0)

private val KEY_CHIP_PADDING = 12.dp

/** As wide as the tall tempo chip's round button and its padding, so that one letter still fills a control's room. */
private val TALL_KEY_MIN_WIDTH = 64.dp
private val TUNER_ICON_SIZE = 20.dp
