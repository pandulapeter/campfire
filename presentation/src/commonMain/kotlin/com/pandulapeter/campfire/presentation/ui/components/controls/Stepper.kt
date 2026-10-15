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

import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.scaled
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * A decrease button, the current value (highlighted when it differs from the default, and followed by a reset icon
 * while it can be reset, which a tap on the value or the icon does) and an increase button, in a tonal pill that keeps the three of them together: two of these sit one under the other in
 * the song details menu, the editor's sits among its pane controls, and the text size one sits alone in the app bar in
 * performance mode, where loose icon buttons would blend into the row of controls around them.
 *
 * The pill is shorter and its buttons narrower than Material's, since full height buttons look oversized in a menu row
 * and in the app bars. On a touch screen the buttons still take
 * a touch 48dp across, since Compose extends a small target's touch area to the minimum touch target size; only their
 * drawn size shrinks.
 *
 * @param fontScale Grows what is written in the pill - the value and the icons - with the text it sits among, for the
 * steppers that are part of a song rather than of a bar or a menu (the song's playing controls). The padding around them
 * does not grow with it, exactly as a section's header pill keeps its own while its title scales.
 * @param height The pill's height, which the caller that scales its content gives as `songControlHeight`.
 * @param valueDescription How a screen reader says [value], where its written form reads badly aloud — `1 / 24`, a bare
 * number of BPM; the value itself when left out.
 * @param valueKey What decides whether a new [value] cross-fades in or replaces the old one in place: only a change
 * of the key is animated.
 * @param widestValues The widest values the stepper can show - the ends of a numeric range, every value where they are
 * words - which its value takes the room of whatever it shows, so the pill keeps one length while it is used; where left
 * empty, the pill is as long as its value.
 * @param repeatsOnHold Whether a button held down keeps stepping, faster the longer it is held, for a value that is a
 * long way from where it starts: fine for a tempo, while a semitone or a text size is a few taps away at most.
 * @param entry Makes the value a field its number can also be typed into ([StepperField]), for the few values with a
 * range too wide to step across; a tap on it then starts typing, and only the reset icon after it resets it.
 * @param trailing A second way to the very same value, drawn inside the pill after the increase button and behind a
 * divider: the tempo's Tap button, which sets the number the stepper steps rather than a value of its own, and so
 * belongs to this control instead of standing next to it.
 */
@Composable
internal fun Stepper(
    modifier: Modifier = Modifier,
    value: String,
    valueDescription: String? = null,
    fontScale: Float = 1f,
    height: Dp = STEPPER_HEIGHT,
    valueKey: Any = value,
    widestValues: List<String> = emptyList(),
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
    entry: StepperEntry? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    // Where the pill is, for the press outside it that ends typing into its value (StepperField).
    val entryBounds = remember { StepperBounds() }
    Surface(
        modifier = modifier
            .height(height)
            .then(if (entry != null) Modifier.onGloballyPositioned { entryBounds.rect = it.boundsInRoot() } else Modifier),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        val buttonLength = stepperButtonLength(fontScale)
        val spokenValue = valueDescription ?: value
        // The buttons are laid out at the size of the pill, so the touch target enforcement of the icon buttons has
        // to be lowered to match, or it would grow them back to 48dp and the pill would no longer fit them.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides minOf(height, buttonLength)) {
            val decreaseButton = @Composable {
                StepperButton(
                    icon = decreaseIcon,
                    label = decreaseLabel,
                    stateDescription = spokenValue,
                    isEnabled = canDecrease,
                    repeatsOnHold = repeatsOnHold,
                    fontScale = fontScale,
                    height = height,
                    buttonLength = buttonLength,
                    onClick = onDecrease,
                )
            }
            val stepperValue = @Composable {
                if (entry != null) {
                    StepperField(
                        value = value,
                        valueDescription = valueDescription,
                        valueKey = valueKey,
                        widestValues = widestValues,
                        isDefault = isDefault,
                        fontScale = fontScale,
                        entry = entry,
                        pillBounds = { entryBounds.rect },
                        resetLabel = resetLabel,
                        onReset = onReset,
                    )
                } else {
                    StepperValue(
                        value = value,
                        valueDescription = valueDescription,
                        valueKey = valueKey,
                        widestValues = widestValues,
                        isDefault = isDefault,
                        resetLabel = resetLabel,
                        onReset = onReset,
                        fontScale = fontScale,
                    )
                }
            }
            val increaseButton = @Composable {
                StepperButton(
                    icon = increaseIcon,
                    label = increaseLabel,
                    stateDescription = spokenValue,
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
                    StepperDivider()
                    trailing()
                }
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
    stateDescription: String,
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
        modifier = Modifier
            .size(width = buttonLength, height = height)
            // The value is what a step changes, so the button says it: a screen reader re-reads the state of the node
            // it is on, and the value is a node of its own the focus is not on.
            .semantics { this.stateDescription = stateDescription },
        enabled = isEnabled,
        interactionSource = interactionSource,
        onClick = { if (holdState.hasRepeated) holdState.hasRepeated = false else onClick() },
    ) {
        Icon(
            modifier = Modifier.size(STEPPER_ICON_SIZE * fontScale),
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
    valueDescription: String?,
    valueKey: Any,
    widestValues: List<String>,
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
    val canReset = !isDefault && onReset != null
    val style = stepperValueStyle(fontScale)
    Box(
        modifier = Modifier.fillMaxHeight()
            .clickable(enabled = canReset, onClickLabel = resetLabel) { onReset?.invoke() }
            .widthIn(min = STEPPER_VALUE_MIN_WIDTH * fontScale),
        contentAlignment = Alignment.Center,
    ) {
        StepperValueRoom(
            values = widestValues,
            style = style,
            isResettable = onReset != null,
            isResetPressable = false,
            fontScale = fontScale,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(
                targetState = StepperLabel(value = value, key = valueKey),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentKey = { it.key },
            ) { label ->
                Text(
                    modifier = if (valueDescription != null) Modifier.semantics { contentDescription = valueDescription } else Modifier,
                    text = label.value,
                    style = style,
                    textAlign = TextAlign.Center,
                    color = color,
                )
            }
            StepperResetMark(
                isShown = canReset,
                color = color,
                fontScale = fontScale,
            )
        }
    }
}

/** A stepper's value with the key that decides whether a change of it is animated, see [Stepper]. */
internal data class StepperLabel(
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
 * How far along the pill one of a stepper's two buttons reaches: its icon, which grows with the song's text, and a
 * padding on either side of it that does not, the way the pills around the controls keep theirs.
 */
private fun stepperButtonLength(fontScale: Float) = STEPPER_ICON_SIZE * fontScale + BUTTON_ICON_PADDING * 2

internal val STEPPER_ICON_SIZE = 20.dp
private val BUTTON_ICON_PADDING = 8.dp

/** How wide a stepper's value is drawn at least, where the stepper is not scaled by a song's text size. */
internal val STEPPER_VALUE_MIN_WIDTH = 44.dp

/** How far along the pill each of a stepper's two buttons reaches, where the stepper is not scaled by a song's text size. */
internal val STEPPER_BUTTON_LENGTH = stepperButtonLength(fontScale = 1f)

/** The line in front of a [Stepper]'s trailing control, stopping short of the pill's own edges. */
@Composable
internal fun StepperDivider() = VerticalDivider(
    modifier = Modifier.padding(vertical = STEPPER_DIVIDER_PADDING),
    color = MaterialTheme.colorScheme.outlineVariant,
)

private val STEPPER_DIVIDER_PADDING = 8.dp
