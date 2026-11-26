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

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * The container of the player's two controls, the tempo chip in the dock and the key chip in the app bar: a tonal pill
 * as the instrument pill is, outlined as well, so that it reads as a control of the player's beside the chord and scale
 * chips, which are the song's, without a chevron spelling it out, and stands out from the dock's surface. [onClick]
 * (null in read only mode, whose chips only read) takes the press over the whole of it but what [content] gives a press
 * of its own (play and stop). One that is not [isFramed] (the key chip where it opens nothing) draws neither its fill nor
 * its outline, fading them, as it then reads like the title beside it rather than inviting a press that does nothing.
 */
@Composable
internal fun DockControlPill(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    onClickLabel: String,
    onClick: (() -> Unit)?,
    isFramed: Boolean = true,
    content: @Composable () -> Unit,
) = Surface(
    modifier = modifier,
    shape = shape,
    color = animateColorAsState(if (isFramed) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent).value,
    contentColor = MaterialTheme.colorScheme.onSurface,
    // The fill alone is a step too close to the dock's own surface to stand out from it.
    border = BorderStroke(1.dp, animateColorAsState(if (isFramed) MaterialTheme.colorScheme.outlineVariant else Color.Transparent).value),
) {
    if (onClick == null) {
        content()
    } else {
        // A Surface's own onClick would also give it the minimum touch target, which would make a 32 dp pill 48 dp tall.
        Box(
            modifier = Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick),
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}
