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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_tuner
import com.pandulapeter.campfire.presentation.resources.tuner
import com.pandulapeter.campfire.presentation.ui.components.CampfireIconButton
import com.pandulapeter.campfire.presentation.ui.components.controls.STEPPER_HEIGHT
import com.pandulapeter.campfire.presentation.ui.components.menu.ACTION_BUTTON_OVERLAP
import com.pandulapeter.campfire.presentation.ui.components.menu.overlappingAction
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock.DockContent
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock.DockKeyChip
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock.dockKeyOf
import org.jetbrains.compose.resources.painterResource

/**
 * The dock's key chip and its tuner in the app bar, where the dock would hold nothing else ([isShown], `DockRows.isKeyOnly`):
 * the same chip ([DockKeyChip]) for the page the pager is on or heading for ([targetPage], [contents]), crossfading as a
 * page turns as the subtitle does, opening the Playing sheet (nothing in read only mode) and left out with neither key
 * nor capo; then, where the dock's tuner is on ([onTunerClicked]), the bar's own icon button for it. The instrument pill
 * stays behind: the Playing sheet has the instrument. Both come and go with the dock's rows, fading and growing, so a
 * switch flipped or a window resized moves nothing abruptly.
 *
 * In the bar the chip is a sibling of the text size stepper it may stand after ([isAfterStepper]): as tall as the
 * stepper ([STEPPER_HEIGHT]) and, like it, a tonal pill with no outline, which the dock's chips have only to stand out
 * from the dock's own surface. Everything in the bar's group of actions is [APP_BAR_PILL_GAP] apart: an icon button's
 * glyph keeps that much of its touch target clear on either side, so a pill needs no gap before a button, and only a pill
 * after the stepper is given it. The tuner reaches into the button after it by [ACTION_BUTTON_OVERLAP], as the bar's
 * other buttons do into theirs, so the tuner, the star or the lock, and the overflow button read as one row.
 */
@Composable
internal fun AppBarPlayingTools(
    isShown: Boolean,
    isAfterStepper: Boolean,
    contents: Map<Int, DockContent>,
    targetPage: () -> Int,
    onTunerClicked: (() -> Unit)?,
) {
    val gapBefore by animateDpAsState(if (isShown && isAfterStepper) APP_BAR_PILL_GAP else 0.dp)
    AnimatedVisibility(
        modifier = Modifier.padding(start = gapBefore),
        visible = isShown,
        enter = fadeIn() + expandHorizontally(),
        exit = fadeOut() + shrinkHorizontally(),
    ) {
        AnimatedContent(
            targetState = targetPage(),
            contentAlignment = Alignment.CenterEnd,
            transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
        ) { page ->
            val content = contents[page]
            val dockKey = remember(content?.playingControls, content?.model) { content?.let { dockKeyOf(it.playingControls, it.model) } }
            if (content != null && dockKey != null) {
                DockKeyChip(
                    dockKey = dockKey,
                    height = STEPPER_HEIGHT,
                    onClick = content.playingControls?.onPlayingClicked?.takeIf { dockKey.opensPlaying },
                    isOutlined = false,
                )
            }
        }
    }
    val shownTuner = onTunerClicked?.takeIf { isShown }
    val lastTuner = remember { LastTunerClick() }
    shownTuner?.let { lastTuner.value = it }
    AnimatedVisibility(
        modifier = Modifier.overlappingAction(start = 0.dp, end = ACTION_BUTTON_OVERLAP),
        visible = shownTuner != null,
        enter = fadeIn() + expandHorizontally(),
        exit = fadeOut() + shrinkHorizontally(),
    ) {
        CampfireIconButton(
            icon = painterResource(Res.drawable.ic_tuner),
            label = stringResource(Res.string.tuner),
            onClick = {
                println("App bar: tuner")
                lastTuner.value?.invoke()
            },
        )
    }
}

/** What the bar's tuner button last opened, kept while it leaves. Not a state: it is written and read in the same composition. */
private class LastTunerClick(var value: (() -> Unit)? = null)

/**
 * What parts two of the bar's actions: the room an icon button leaves on either side of its 24 dp glyph within its 48 dp
 * touch target, given to a pill after a pill too.
 */
internal val APP_BAR_PILL_GAP = 12.dp
