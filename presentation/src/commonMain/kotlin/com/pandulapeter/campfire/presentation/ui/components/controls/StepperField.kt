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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.pandulapeter.campfire.presentation.ui.components.menu.LocalOverlayState
import com.pandulapeter.campfire.presentation.ui.components.scaled
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
 * The reset is not a tap on the value here, since the tap is what starts typing: only the [StepperResetMark] after it
 * resets it, which ends the typing, as the number typed is then no longer the one wanted. The field is only as wide as its number,
 * so that the icon follows it; a press on the rest of the value's room focuses it too.
 */
@Composable
internal fun StepperField(
    value: String,
    valueDescription: String?,
    valueKey: Any,
    widestValues: List<String>,
    isDefault: Boolean,
    fontScale: Float,
    entry: StepperEntry,
    pillBounds: () -> Rect,
    resetLabel: String?,
    onReset: (() -> Unit)?,
) = Box(
    modifier = Modifier.fillMaxHeight().widthIn(min = STEPPER_VALUE_MIN_WIDTH * fontScale),
    contentAlignment = Alignment.Center,
) {
    val focusRequester = remember { FocusRequester() }
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
    if (isFocused) EndsTypingWhenLeft(pillBounds = pillBounds, onEnd = { focusManager.clearFocus() })
    val colorScheme = MaterialTheme.colorScheme
    val color = stepperFieldColor(
        isDefault = isDefault,
        isOutOfRange = isFocused && state.text.isNotEmpty() && typedNumber(state.text, entry.range) == null,
    )
    val canReset = !isDefault && onReset != null
    // Not centered by its alignment, which would have the field take every width it is let have: the decoration centers
    // the line, and every part of it is as wide as its text.
    val textStyle = stepperValueStyle(fontScale).copy(color = color)
    val (prefix, suffix) = remember(value, entry.number) { affixesAround(value, entry.number) }
    StepperValueRoom(
        modifier = Modifier.pointerInput(focusRequester) { detectTapGestures { focusRequester.requestFocus() } },
        values = widestValues,
        style = textStyle,
        isResettable = onReset != null,
        isResetPressable = true,
        fontScale = fontScale,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            modifier = Modifier
                .fillMaxHeight()
                .focusRequester(focusRequester)
                // As wide as its text: a text field is otherwise as wide as it is let be, which would stretch the pill.
                .width(IntrinsicSize.Max)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused == isFocused) return@onFocusChanged
                    isFocused = focusState.isFocused
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
                .then(spokenValueOf(valueDescription, value, prefix, suffix)?.let { Modifier.semantics { contentDescription = it } } ?: Modifier),
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
                    isFocused = isFocused,
                    innerTextField = innerTextField,
                )
            },
        )
        if (onReset != null) {
            StepperResetMark(
                isShown = canReset,
                color = color,
                fontScale = fontScale,
                resetLabel = resetLabel,
                onReset = {
                    if (isFocused) focusManager.clearFocus()
                    onReset()
                },
            )
        }
    }
}

/**
 * The color of a [StepperField]'s number: highlighted while it differs from the default, as every stepper's value is,
 * and the error color while what is typed is no number of the range.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun stepperFieldColor(
    isDefault: Boolean,
    isOutOfRange: Boolean,
): Color {
    val changedProgress by animateFloatAsState(
        if (isDefault) 0f else 1f,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    val outOfRangeProgress by animateFloatAsState(
        if (isOutOfRange) 1f else 0f,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    val colorScheme = MaterialTheme.colorScheme
    return lerp(lerp(colorScheme.onSurface, colorScheme.primary, changedProgress), colorScheme.error, outOfRangeProgress)
}

/**
 * What ends typing into a focused [StepperField] from outside it: a press outside its pill (`OverlayState`), and the
 * keyboard put away - by its own button, or by Back, which a touch platform spends on that first - since a field left
 * focused with nothing to type with would keep its caret.
 */
@Composable
private fun EndsTypingWhenLeft(
    pillBounds: () -> Rect,
    onEnd: () -> Unit,
) {
    val overlayState = LocalOverlayState.current
    DisposableEffect(overlayState) {
        overlayState.onValueTypingStarted(bounds = pillBounds, onEnd = onEnd)
        onDispose { overlayState.onValueTypingEnded() }
    }
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val isImeVisible by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    var hasImeBeenVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isImeVisible) {
        if (isImeVisible) hasImeBeenVisible = true else if (hasImeBeenVisible) onEnd()
    }
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
    isFocused: Boolean,
    innerTextField: @Composable () -> Unit,
) = Box(
    modifier = Modifier.fillMaxHeight(),
    contentAlignment = Alignment.Center,
) {
    // What the written value has around the number (the unit of `440 Hz`) is drawn beside the field rather than in it,
    // so that selecting what is typed selects the digits alone.
    Row(
        modifier = Modifier.graphicsLayer { alpha = if (isFocused) 1f else 0f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (prefix.isNotEmpty()) Text(modifier = Modifier.clearAndSetSemantics {}, text = prefix, style = textStyle, softWrap = false)
        Box(Modifier.width(IntrinsicSize.Max)) { innerTextField() }
        if (suffix.isNotEmpty()) Text(modifier = Modifier.clearAndSetSemantics {}, text = suffix, style = textStyle, softWrap = false)
    }
    if (!isFocused) {
        AnimatedContent(
            targetState = StepperLabel(value = value, key = valueKey),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            contentKey = { it.key },
        ) { label ->
            // Silent: the field under it is what a screen reader focuses, and it says the same value.
            Text(
                modifier = Modifier.clearAndSetSemantics {},
                text = label.value,
                style = textStyle,
            )
        }
    }
}

/**
 * What a screen reader says for the field: the value as written wherever the field's own text lacks part of it, since
 * the unit drawn beside the field and the plain value drawn over it are silent.
 */
private fun spokenValueOf(valueDescription: String?, value: String, prefix: String, suffix: String) =
    valueDescription ?: value.takeIf { prefix.isNotEmpty() || suffix.isNotEmpty() }

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

/** What the icon of a [Stepper]'s trailing segment (the tempo's Tap) keeps at its ends. */
internal val STEPPER_SEGMENT_PADDING = 12.dp
