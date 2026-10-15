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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.selectAll
import androidx.compose.foundation.text.input.setTextAndSelectAll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.pandulapeter.campfire.presentation.ui.components.menu.LocalOverlayState
import com.pandulapeter.campfire.presentation.ui.components.scaled
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_undo
import org.jetbrains.compose.resources.painterResource

/**
 * A [Stepper]'s value as a field its number is typed into, see [StepperEntry]. It reads exactly like the plain value
 * until it is focused - by a tap or a click, or by Tab - and the stepper's buttons, its Tap, and a slider beside it keep
 * working while it is, replacing whatever was being typed with the number they set.
 *
 * Every keystroke that leaves a number of the range sets it at once, so the click or the reference tone follows the
 * typing with no key to confirm it. A number outside the range is drawn in the error color and set nothing, never
 * refused or corrected on a keystroke: a narrow range like the reference pitch's 415..466 is only reached through
 * numbers outside it. Leaving the field - Done or Enter, Tab, the keyboard put away, or a press anywhere outside the
 * stepper (`OverlayState.onPointerPressed`) - writes back the value last set; Escape puts back the one the field was
 * focused at. Up and Down step the value while it is focused, as a hardware keyboard's arrows do in any number field.
 *
 * The reset is not a tap on the value here, since the tap is what starts typing: a focused value that can be reset
 * offers it as the pill's last segment instead ([StepperEntryTrailing]), which is why the focus is reported.
 */
@Composable
internal fun StepperField(
    value: String,
    valueDescription: String?,
    valueKey: Any,
    isDefault: Boolean,
    fontScale: Float,
    entry: StepperEntry,
    pillBounds: () -> Rect,
    onFocusChanged: (Boolean) -> Unit,
) = Box(contentAlignment = Alignment.Center) {
    val state = rememberTextFieldState(entry.number.toString())
    val latestEntry by rememberUpdatedState(entry)
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    var numberAtFocus by remember { mutableIntStateOf(entry.number) }
    // Not over a number typed that is the value: that is the typing having set it, and selecting it would have the next
    // digit replace it (30 on the way to 300).
    LaunchedEffect(entry.number) {
        if (state.text.toString().toIntOrNull() != entry.number) state.setTextAndSelectAll(entry.number.toString())
    }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { text ->
            val current = latestEntry
            val typed = typedNumber(text, current.range)
            if (isFocused && typed != null && typed != current.number) current.onEntered(typed)
        }
    }
    if (isFocused) {
        val overlayState = LocalOverlayState.current
        DisposableEffect(overlayState) {
            overlayState.onValueTypingStarted(bounds = pillBounds, onEnd = { focusManager.clearFocus() })
            onDispose { overlayState.onValueTypingEnded() }
        }
        // The keyboard put away - by its own button, or by Back, which a touch platform spends on that first - is the
        // end of typing, since a field left focused with nothing to type with would keep its caret and its reset.
        val ime = WindowInsets.ime
        val density = LocalDensity.current
        val isImeVisible by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
        var hasImeBeenVisible by remember { mutableStateOf(false) }
        LaunchedEffect(isImeVisible) {
            if (isImeVisible) hasImeBeenVisible = true else if (hasImeBeenVisible) focusManager.clearFocus()
        }
    }
    val changedProgress by animateFloatAsState(
        if (isDefault) 0f else 1f,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    val isOutOfRange = isFocused && state.text.isNotEmpty() && typedNumber(state.text, entry.range) == null
    val outOfRangeProgress by animateFloatAsState(
        if (isOutOfRange) 1f else 0f,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    val colorScheme = MaterialTheme.colorScheme
    val color = lerp(lerp(colorScheme.onSurface, colorScheme.primary, changedProgress), colorScheme.error, outOfRangeProgress)
    // Not centered by its alignment, which would have the field take every width it is let have: the decoration centers
    // the line, and every part of it is as wide as its text.
    val textStyle = MaterialTheme.typography.labelLarge.scaled(fontScale).copy(
        color = color,
        fontWeight = FontWeight.Bold,
    )
    val (prefix, suffix) = remember(value, entry.number) { affixesAround(value, entry.number) }
    BasicTextField(
        modifier = Modifier
            .fillMaxHeight()
            // As wide as its text: a text field is otherwise as wide as it is let be, which would stretch the pill.
            .width(IntrinsicSize.Max)
            .onFocusChanged { focusState ->
                if (focusState.isFocused == isFocused) return@onFocusChanged
                isFocused = focusState.isFocused
                onFocusChanged(focusState.isFocused)
                if (focusState.isFocused) {
                    numberAtFocus = latestEntry.number
                    // The number is there to be typed over rather than edited a digit at a time. This covers Tab; a
                    // press places its caret once it is released, which selectsAllOnRelease answers.
                    state.edit { selectAll() }
                } else {
                    state.setTextAndSelectAll(latestEntry.number.toString())
                }
            }
            .selectsAllOnRelease(state = state, isFocused = { isFocused }, coroutineScope = coroutineScope)
            .stepperFieldKeys(
                state = state,
                entry = { latestEntry },
                onEscape = {
                    val current = latestEntry
                    if (numberAtFocus != current.number) current.onEntered(numberAtFocus)
                    focusManager.clearFocus()
                },
            )
            .then(if (valueDescription != null) Modifier.semantics { contentDescription = valueDescription } else Modifier),
        state = state,
        inputTransformation = remember(entry.range) { StepperDigits(maxLength = entry.range.last.toString().length) },
        lineLimits = TextFieldLineLimits.SingleLine,
        textStyle = textStyle,
        cursorBrush = SolidColor(colorScheme.primary),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
            autoCorrectEnabled = false,
        ),
        onKeyboardAction = KeyboardActionHandler { focusManager.clearFocus() },
        decorator = { innerTextField ->
            StepperFieldDecoration(
                value = value,
                valueKey = valueKey,
                textStyle = textStyle,
                prefix = prefix,
                suffix = suffix,
                fontScale = fontScale,
                isFocused = isFocused,
                innerTextField = innerTextField,
            )
        },
    )
}

/** Where a [Stepper] with a [StepperField] is, written as it is laid out and read only at a press: no state. */
internal class StepperBounds {
    var rect = Rect.Zero
}

/**
 * The field is drawn only while it is typed into; otherwise the plain value is drawn over it, which cross-fades its
 * changes the way every stepper's value does. Both write the same text, so nothing moves as one gives way to the other.
 */
@Composable
private fun StepperFieldDecoration(
    value: String,
    valueKey: Any,
    textStyle: TextStyle,
    prefix: String,
    suffix: String,
    fontScale: Float,
    isFocused: Boolean,
    innerTextField: @Composable () -> Unit,
) = Box(
    modifier = Modifier.fillMaxHeight().widthIn(min = STEPPER_VALUE_MIN_WIDTH * fontScale),
    contentAlignment = Alignment.Center,
) {
    // What the written value has around the number (the unit of `440 Hz`) is drawn beside the field rather than in it,
    // so that selecting what is typed selects the digits alone.
    Row(
        modifier = Modifier.graphicsLayer { alpha = if (isFocused) 1f else 0f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (prefix.isNotEmpty()) Text(text = prefix, style = textStyle, softWrap = false)
        Box(Modifier.width(IntrinsicSize.Max)) { innerTextField() }
        if (suffix.isNotEmpty()) Text(text = suffix, style = textStyle, softWrap = false)
    }
    if (!isFocused) {
        AnimatedContent(
            targetState = StepperLabel(value = value, key = valueKey),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            contentKey = { it.key },
        ) { label ->
            Text(
                text = label.value,
                style = textStyle,
            )
        }
    }
}

/**
 * Selects the whole number once the press that focused the field is let go of: the text field places the caret where a
 * press is released, which comes frames after the focus it gave on the press itself for as long as a finger or a mouse
 * button is held. Watched in the initial pass and never consumed, so the field still handles the press as its own; a
 * press on a field already focused is left to place the caret, which is how a digit is corrected.
 */
private fun Modifier.selectsAllOnRelease(
    state: TextFieldState,
    isFocused: () -> Boolean,
    coroutineScope: CoroutineScope,
) = pointerInput(state) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (isFocused()) return@awaitEachGesture
        waitForUpOrCancellation(pass = PointerEventPass.Initial) ?: return@awaitEachGesture
        // A frame later, once the field has handled the release this pass only previewed.
        coroutineScope.launch {
            withFrameNanos {}
            if (isFocused()) state.edit { selectAll() }
        }
    }
}

/**
 * Escape, Up, and Down as the focused field answers them, see [StepperField]. Taken in the preview pass, before the text
 * field itself, which would move the caret with the arrows.
 */
private fun Modifier.stepperFieldKeys(
    state: TextFieldState,
    entry: () -> StepperEntry,
    onEscape: () -> Unit,
) = onPreviewKeyEvent { keyEvent ->
    when (keyEvent.key) {
        // On its release, and the press swallowed too: whatever gets the focus once the field lets go of it would
        // otherwise hear the release, and Compose turns an Escape nobody consumed into a back.
        Key.Escape -> {
            if (keyEvent.type == KeyEventType.KeyUp) onEscape()
            true
        }
        // Written into the field, which sets it the way a typed number is set.
        Key.DirectionUp, Key.DirectionDown -> {
            if (keyEvent.type == KeyEventType.KeyDown) {
                val current = entry()
                val base = typedNumber(state.text, current.range) ?: current.number
                val delta = if (keyEvent.key == Key.DirectionUp) 1 else -1
                state.setTextAndSelectAll((base + delta).coerceIn(current.range).toString())
            }
            true
        }
        else -> false
    }
}

/**
 * What ends a [Stepper] whose value is typed into: its own trailing segment (the tempo's Tap), which gives way to a reset
 * while the value is focused and can be reset, or a reset that joins the pill for that long where it has none (the
 * reference pitch). Inside the pill rather than over the screen, so that it covers nothing; Tap is not wanted while a
 * number is typed. The reset cannot take the focus, so a click on it leaves the field focused until the reset itself
 * lets go of it, rather than taking the reset away on the press it is clicked with.
 */
@Composable
internal fun StepperEntryTrailing(
    isResetShown: Boolean,
    resetLabel: String?,
    onReset: () -> Unit,
    fontScale: Float,
    trailing: (@Composable () -> Unit)?,
) {
    val focusManager = LocalFocusManager.current
    AnimatedContent(
        modifier = Modifier.fillMaxHeight(),
        targetState = isResetShown,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentAlignment = Alignment.CenterStart,
    ) { showsReset ->
        when {
            showsReset -> Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                StepperDivider()
                StepperResetSegment(
                    label = resetLabel,
                    fontScale = fontScale,
                    onClick = {
                        focusManager.clearFocus()
                        onReset()
                    },
                )
            }
            trailing != null -> Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                StepperDivider()
                trailing()
            }
        }
    }
}

/** The reset of [StepperEntryTrailing], drawn and pressed like the Tap segment it stands in for. */
@Composable
private fun StepperResetSegment(
    label: String?,
    fontScale: Float,
    onClick: () -> Unit,
) = Box(
    modifier = Modifier
        .fillMaxHeight()
        .focusProperties { canFocus = false }
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = RESET_SEGMENT_PADDING)
        .semantics { if (label != null) contentDescription = label },
    contentAlignment = Alignment.Center,
) {
    Icon(
        modifier = Modifier.size(STEPPER_ICON_SIZE * fontScale),
        painter = painterResource(Res.drawable.ic_undo),
        contentDescription = null,
    )
}

private val RESET_SEGMENT_PADDING = 12.dp

/** How wide [StepperEntryTrailing]'s reset is, after its divider, where the stepper is not scaled by a song's text size. */
internal val STEPPER_RESET_SEGMENT_WIDTH = STEPPER_ICON_SIZE + RESET_SEGMENT_PADDING * 2
