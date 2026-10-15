/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_refresh
import org.jetbrains.compose.resources.painterResource

/**
 * The reset icon right after a [Stepper]'s value, there only while the value can be reset. It grows in beside the value
 * and the two stay centered together, inside room the value keeps for both whether the icon is there or not
 * ([StepperValueRoom]), so the pill never changes its length.
 *
 * @param onReset Makes the icon itself the reset where a tap on the value does something else (typing into a
 * [StepperField]), with a round ripple and a touch area in a box as wide as the stepper's buttons, since a miss on
 * either side of it would start typing or step the value; beside a plain value the icon only marks it, since the value and the icon take the tap together. It cannot take the focus, so a click on it
 * while typing does not end the typing on the press.
 */
@Composable
internal fun StepperResetMark(
    isShown: Boolean,
    color: Color,
    fontScale: Float,
    resetLabel: String? = null,
    onReset: (() -> Unit)? = null,
) = AnimatedVisibility(
    visible = isShown,
    enter = fadeIn() + expandHorizontally(),
    exit = fadeOut() + shrinkHorizontally(),
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .then(
                if (onReset == null) {
                    Modifier
                } else {
                    Modifier
                        .focusProperties { canFocus = false }
                        .clickable(
                            interactionSource = null,
                            indication = ripple(bounded = false, radius = STEPPER_RESET_MARK_SIZE * fontScale),
                            enabled = isShown,
                            role = Role.Button,
                            onClick = onReset,
                        )
                        .semantics { if (isShown && resetLabel != null) contentDescription = resetLabel }
                },
            )
            .width(stepperResetMarkWidth(fontScale = fontScale, isPressable = onReset != null)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier
                .size(STEPPER_RESET_MARK_SIZE * fontScale)
                // Mirrored, so that the arrow turns back the way a value is put back rather than on as a refresh does.
                .graphicsLayer { scaleX = -1f },
            painter = painterResource(Res.drawable.ic_refresh),
            contentDescription = null,
            tint = color,
        )
    }
}

private val STEPPER_RESET_MARK_SIZE = 16.dp
private val STEPPER_RESET_MARK_GAP = 6.dp

/**
 * How wide a [StepperResetMark] is: its icon, which grows with the song's text, and its gap. One that takes a press is at
 * least as long as the stepper's − and + ([STEPPER_BUTTON_LENGTH], unscaled, as their press areas are capped at it), so
 * that it is as easy to hit as they are; no wider, so that it is no larger a target than the buttons beside it.
 */
internal fun stepperResetMarkWidth(fontScale: Float, isPressable: Boolean): Dp {
    val markWidth = STEPPER_RESET_MARK_SIZE * fontScale + STEPPER_RESET_MARK_GAP
    return if (isPressable) maxOf(markWidth, STEPPER_BUTTON_LENGTH) else markWidth
}

/** The width of a [StepperResetMark] that takes no press, where the stepper is not scaled by a song's text size. */
internal val STEPPER_RESET_MARK_WIDTH = stepperResetMarkWidth(fontScale = 1f, isPressable = false)
