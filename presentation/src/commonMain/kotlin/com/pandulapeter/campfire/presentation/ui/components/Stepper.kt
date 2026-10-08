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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.HEADER_VERTICAL_PADDING
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.SongPlayingControls
import kotlinx.coroutines.delay

/**
 * A decrease button, the current value (highlighted when it differs from the default, tapping it resets it) and an
 * increase button, in a tonal pill that keeps the three of them together: two of these sit one under the other in
 * the song details menu, the editor's sits among its pane controls, and the text size one sits alone in the app bar in
 * performance mode, where loose icon buttons would blend into the row of controls around them.
 *
 * The pill is shorter and its buttons narrower than Material's, since full height buttons look oversized in a menu row
 * and in the app bars. On a touch screen the buttons still take
 * a touch 48dp across, since Compose extends a small target's touch area to the minimum touch target size; only their
 * drawn size shrinks.
 *
 * @param fontScale Grows what is written in the pill - the value and the icons - with the text it sits among, for the
 * steppers that are part of a song rather than of a bar or a menu (see [SongPlayingControls]). The padding around them
 * does not grow with it, exactly as a section's header pill keeps its own while its title scales.
 * @param height The pill's height, which the caller that scales its content gives as [songControlHeight].
 * @param valueKey What decides whether a new [value] cross-fades in or replaces the old one in place: only a change
 * of the key is animated.
 * @param repeatsOnHold Whether a button held down keeps stepping, faster the longer it is held, for a value that is a
 * long way from where it starts: fine for a tempo, while a semitone or a text size is a few taps away at most.
 * @param trailing A second way to the very same value, drawn inside the pill after the increase button and behind a
 * divider: the tempo's Tap button, which sets the number the stepper steps rather than a value of its own, and so
 * belongs to this control instead of standing next to it.
 */
@Composable
internal fun Stepper(
    modifier: Modifier = Modifier,
    value: String,
    fontScale: Float = 1f,
    height: Dp = STEPPER_HEIGHT,
    valueKey: Any = value,
    isDefault: Boolean,
    decreaseIcon: Painter,
    decreaseLabel: String,
    canDecrease: Boolean,
    onDecrease: () -> Unit,
    increaseIcon: Painter,
    increaseLabel: String,
    canIncrease: Boolean,
    onIncrease: () -> Unit,
    resetLabel: String?,
    onReset: (() -> Unit)?,
    repeatsOnHold: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) = Surface(
    modifier = modifier.height(height),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val buttonLength = stepperButtonLength(fontScale)
    // The buttons are laid out at the size of the pill, so the touch target enforcement of the icon buttons has
    // to be lowered to match, or it would grow them back to 48dp and the pill would no longer fit them.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides minOf(height, buttonLength)) {
        val decreaseButton = @Composable {
            StepperButton(
                icon = decreaseIcon,
                label = decreaseLabel,
                isEnabled = canDecrease,
                repeatsOnHold = repeatsOnHold,
                fontScale = fontScale,
                height = height,
                buttonLength = buttonLength,
                onClick = onDecrease,
            )
        }
        val stepperValue = @Composable {
            StepperValue(
                value = value,
                valueKey = valueKey,
                isDefault = isDefault,
                resetLabel = resetLabel,
                onReset = onReset,
                fontScale = fontScale,
            )
        }
        val increaseButton = @Composable {
            StepperButton(
                icon = increaseIcon,
                label = increaseLabel,
                isEnabled = canIncrease,
                repeatsOnHold = repeatsOnHold,
                fontScale = fontScale,
                height = height,
                buttonLength = buttonLength,
                onClick = onIncrease,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            decreaseButton()
            stepperValue()
            increaseButton()
            if (trailing != null) {
                VerticalDivider(
                    modifier = Modifier.padding(vertical = STEPPER_DIVIDER_PADDING),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                trailing()
            }
        }
    }
}

/**
 * One end of a [Stepper]. Held down with [repeatsOnHold] it steps on its own after a moment, faster and faster, and the
 * release that ends a hold is not one more step: a tap still steps once, on its release, like any button, which is
 * also what a keyboard or a screen reader activating it does. Each step of a hold is felt as a tick, since the value
 * runs too fast to be read and the finger holding the button is what tells it to stop; a tap is not, like any button.
 */
@Composable
private fun StepperButton(
    icon: Painter,
    label: String,
    isEnabled: Boolean,
    repeatsOnHold: Boolean,
    fontScale: Float,
    height: Dp,
    buttonLength: Dp,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val holdState = remember { HoldState() }
    if (repeatsOnHold) {
        val isPressed by interactionSource.collectIsPressedAsState()
        val currentOnClick by rememberUpdatedState(onClick)
        val currentIsEnabled by rememberUpdatedState(isEnabled)
        val hapticFeedback = LocalHapticFeedback.current
        LaunchedEffect(isPressed) {
            if (!isPressed) return@LaunchedEffect
            holdState.hasRepeated = false
            delay(HOLD_REPEAT_DELAY_MILLIS)
            var interval = HOLD_REPEAT_FIRST_INTERVAL_MILLIS.toFloat()
            // Ends at the end of the range rather than ticking on against it for as long as the button is held.
            while (currentIsEnabled) {
                holdState.hasRepeated = true
                currentOnClick()
                hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                delay(interval.toLong())
                interval = maxOf(HOLD_REPEAT_FASTEST_INTERVAL_MILLIS.toFloat(), interval * HOLD_REPEAT_ACCELERATION)
            }
        }
    }
    IconButton(
        modifier = Modifier.size(width = buttonLength, height = height),
        enabled = isEnabled,
        interactionSource = interactionSource,
        onClick = { if (holdState.hasRepeated) holdState.hasRepeated = false else onClick() },
    ) {
        Icon(
            modifier = Modifier.size(ICON_SIZE * fontScale),
            painter = icon,
            contentDescription = label,
        )
    }
}

/** Whether the press being released was a hold that already stepped. Not a state: nothing is drawn from it. */
private class HoldState(var hasRepeated: Boolean = false)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StepperValue(
    value: String,
    valueKey: Any,
    isDefault: Boolean,
    resetLabel: String?,
    onReset: (() -> Unit)?,
    fontScale: Float,
) {
    // A progress value instead of an animated color, so that the label follows the color scheme immediately while it
    // is animating between the light and the dark theme (a color animation would chase it and trail behind).
    val changedProgress by animateFloatAsState(
        if (isDefault) 0f else 1f,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    val color = lerp(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.primary, changedProgress)
    AnimatedContent(
        modifier = Modifier.fillMaxHeight(),
        targetState = StepperLabel(value = value, key = valueKey),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentKey = { it.key },
    ) { label ->
        Text(
            modifier = Modifier.fillMaxHeight()
                .clickable(enabled = !isDefault && onReset != null, onClickLabel = resetLabel) { onReset?.invoke() }
                .widthIn(min = VALUE_MIN_WIDTH * fontScale)
                .wrapContentHeight(),
            text = label.value,
            style = MaterialTheme.typography.labelLarge.scaled(fontScale),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

/** A stepper's value with the key that decides whether a change of it is animated, see [Stepper]. */
private data class StepperLabel(
    val value: String,
    val key: Any,
)

private const val HOLD_REPEAT_DELAY_MILLIS = 400L
private const val HOLD_REPEAT_FIRST_INTERVAL_MILLIS = 150L
private const val HOLD_REPEAT_FASTEST_INTERVAL_MILLIS = 30L
private const val HOLD_REPEAT_ACCELERATION = 0.85f

/**
 * How tall a stepper and the Tap button beside them are drawn where they belong to a bar or to a menu
 * rather than to a song: the app bar's text size stepper, the editor's transposition, the Metronome tab's steppers and
 * the rows of the overflow menu, none of which is scaled by anything.
 */
internal val STEPPER_HEIGHT = 40.dp

/**
 * How tall those same controls are drawn in a song's own first section, where they stand among its sections: exactly a
 * section's header pill, which is built the other way round from a bar's button — the [titleStyle] of its name grows
 * with the song while the padding around it stays as it is (`SectionHeaderPill` in `SectionHeaderPill.kt`). Scaling a whole
 * 40dp pill instead left the four controls towering over the page they are part of at a large text size.
 */
@Composable
internal fun songControlHeight(titleStyle: TextStyle) = with(LocalDensity.current) { titleStyle.lineHeight.toDp() } +
    HEADER_VERTICAL_PADDING * 2

/**
 * How far along the pill one of a stepper's two buttons reaches: its icon, which grows with the song's text, and a
 * padding on either side of it that does not, the way the pills around the controls keep theirs.
 */
private fun stepperButtonLength(fontScale: Float) = ICON_SIZE * fontScale + BUTTON_ICON_PADDING * 2

private val ICON_SIZE = 20.dp
private val BUTTON_ICON_PADDING = 8.dp
private val VALUE_MIN_WIDTH = 44.dp

/** How far short of the pill's own edges the divider in front of a [Stepper]'s trailing control stops. */
private val STEPPER_DIVIDER_PADDING = 8.dp

/** How wide a stepper is drawn, at least, for the app bar that has to leave its title room beside one. */
internal val STEPPER_WIDTH = stepperButtonLength(fontScale = 1f) * 2 + VALUE_MIN_WIDTH
