/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.ic_clear
import com.pandulapeter.campfire.presentation.ui.components.SHORT_WINDOW_HEIGHT
import com.pandulapeter.campfire.presentation.ui.platform.CompactKeyboardEffect
import com.pandulapeter.campfire.presentation.ui.platform.isTextFieldDialogWindowFullSize
import com.pandulapeter.campfire.presentation.ui.platform.textFieldDialogInsetsPadding
import com.pandulapeter.campfire.presentation.ui.platform.textFieldDialogProperties
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.painterResource

/**
 * Material's `AlertDialog`, for every dialog that is typed into, which grows into a full screen one once what the bars
 * and the keyboard leave of the window is too short for it, and shrinks back once there is room again.
 *
 * That room gets that short when the keyboard comes up on a small phone, or in a phone held sideways: a centered dialog
 * then keeps its title, its padding and its buttons and squeezes what is between them, the field and the list being
 * typed into, down to nothing. The full screen form puts the close button, the title and the confirming button in one
 * bar, the way a sheet's header does, and gives the rest of the window to the content. It shrinks back only once the
 * room has grown clearly past the height it grew at, so that a window near that height does not switch it back and
 * forth, and only once nothing is moving the content: a
 * list scrolled down puts the keyboard away ([HideKeyboardWhenScrolledDown]), and a dialog that turned back into a
 * small one under the finger scrolling it would move the very rows being read ([DialogGestureTracker]).
 *
 * Both forms are one layout whose parts move, rather than two dialogs: the content, with the focused field in it, is
 * the same node either way, so the field keeps its focus, and with it the keyboard, as the dialog grows around it. A
 * field that was rebuilt would lose both, the window would grow back, and the dialog would be where it started.
 *
 * @param startButton An action of the content's own, kept at the start of the button row apart from the two that
 *   close the dialog, under the content in the full screen form, or beside the confirming action in a short window.
 * @param dismissButton What cancels the dialog; the full screen form has its close button in its place.
 */
@Composable
internal fun TextFieldDialog(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    startButton: (@Composable () -> Unit)? = null,
) {
    // The window's height stands in for the dialog's own on the first frame, which is only measured once it is
    // composed, so that a dialog opened in a window that is already too short is never drawn small first.
    val windowHeight = LocalWindowInfo.current.containerDpSize.height
    val density = LocalDensity.current
    val initialInsets = WindowInsets.safeDrawing
    val initialAvailableHeight = windowHeight - with(density) {
        (initialInsets.getTop(this) + initialInsets.getBottom(this)).toDp()
    }
    var isFullScreen by remember { mutableStateOf(initialAvailableHeight < FULL_SCREEN_HEIGHT) }
    val gestureTracker = remember { DialogGestureTracker() }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = textFieldDialogProperties(isFullScreen),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .trackingGestures(gestureTracker)
                .then(if (isFullScreen || isTextFieldDialogWindowFullSize) Modifier.fillMaxSize() else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (isTextFieldDialogWindowFullSize && !isFullScreen) {
                // The window fills the screen, so the platform takes every tap for one on the dialog, the scrim's
                // included; this is what is around the small form.
                Box(modifier = Modifier.fillMaxSize().pointerInput(onDismissRequest) { detectTapGestures { onDismissRequest() } })
            }
            // The Android dialog window reaches under the bars and the keyboard rather than being resized for them, so
            // its measured height alone does not say how much of it can be used: what the bars and the keyboard cover
            // is taken off it. Elsewhere the dialog is placed clear of both already, and the insets it reports inside
            // leave them out, so nothing is taken off twice.
            val imeHeight = with(density) { WindowInsets.ime.getBottom(this).toDp() }
            val coveredHeight = with(density) { (WindowInsets.safeDrawing.getTop(this) + WindowInsets.safeDrawing.getBottom(this)).toDp() }
            // Restarted by every frame of the keyboard's slide, so the full screen form also waits for the keyboard
            // to be gone before it shrinks back.
            LaunchedEffect(maxHeight, coveredHeight) {
                val availableHeight = maxHeight - coveredHeight
                if (availableHeight < FULL_SCREEN_HEIGHT) {
                    isFullScreen = true
                } else if (isFullScreen && availableHeight >= FULL_SCREEN_HEIGHT + SHRINK_BACK_MARGIN) {
                    gestureTracker.awaitSettled()
                    isFullScreen = false
                }
            }
            val isShortWindow = windowHeight < SHORT_WINDOW_HEIGHT
            CompactKeyboardEffect(isEnabled = isFullScreen && isShortWindow && imeHeight > 0.dp)
            val cornerRadius by animateDpAsState(if (isFullScreen) 0.dp else DIALOG_CORNER_RADIUS)
            Surface(
                modifier = modifier
                    // The small form is centered in what the bars and the keyboard leave, never behind them.
                    .then(if (isFullScreen) Modifier else Modifier.textFieldDialogInsetsPadding(isFullScreen = false))
                    .then(if (isFullScreen || !isTextFieldDialogWindowFullSize) Modifier else Modifier.padding(DIALOG_WINDOW_MARGIN))
                    .sizeIn(
                        minWidth = DIALOG_MIN_WIDTH,
                        maxWidth = when {
                            isFullScreen -> maxWidth
                            // The width a platform dialog window keeps to on a phone, which this one no longer has.
                            isTextFieldDialogWindowFullSize && maxWidth < PHONE_WINDOW_MAX_WIDTH -> PHONE_DIALOG_MAX_WIDTH
                            else -> DIALOG_MAX_WIDTH
                        },
                    )
                    .animateContentSize()
                    .then(if (isFullScreen) Modifier.fillMaxSize() else Modifier),
                shape = RoundedCornerShape(cornerRadius),
                color = AlertDialogDefaults.containerColor,
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                Column(
                    // The full screen form's surface covers the whole window, bars included, while its content keeps
                    // clear of them. Safe drawing already includes the keyboard, so it is not padded by the IME again.
                    modifier = Modifier
                        .then(if (isFullScreen) Modifier.textFieldDialogInsetsPadding(isFullScreen = true) else Modifier)
                        .padding(
                            top = if (isFullScreen) 0.dp else DIALOG_PADDING,
                            bottom = if (isFullScreen) 0.dp else DIALOG_PADDING,
                        ),
                ) {
                    if (isFullScreen) {
                        FullScreenDialogBar(
                            title = title,
                            startButton = startButton.takeIf { isShortWindow },
                            isCompact = isShortWindow,
                            confirmButton = confirmButton,
                            onClose = onDismissRequest,
                        )
                    } else {
                        ProvideContentColorTextStyle(
                            contentColor = AlertDialogDefaults.titleContentColor,
                            textStyle = MaterialTheme.typography.headlineSmall,
                        ) {
                            Box(modifier = Modifier.padding(start = DIALOG_PADDING, end = DIALOG_PADDING, bottom = TITLE_PADDING)) {
                                title()
                            }
                        }
                    }
                    ProvideContentColorTextStyle(
                        contentColor = AlertDialogDefaults.textContentColor,
                        textStyle = MaterialTheme.typography.bodyMedium,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(weight = 1f, fill = isFullScreen)
                                .padding(horizontal = DIALOG_PADDING)
                                .padding(bottom = if (isFullScreen) 0.dp else TEXT_PADDING),
                        ) {
                            text()
                        }
                    }
                    if (isFullScreen) {
                        if (startButton != null && !isShortWindow) {
                            DialogButtons(modifier = Modifier.fillMaxWidth().padding(horizontal = DIALOG_PADDING)) {
                                startButton()
                            }
                        }
                    } else {
                        // Only as wide as the buttons, like Material's own: a row that filled the width would stretch
                        // a dialog with narrower content to its widest.
                        DialogButtons(
                            modifier = Modifier
                                .then(if (startButton == null) Modifier.align(Alignment.End) else Modifier.fillMaxWidth())
                                .padding(horizontal = DIALOG_PADDING),
                        ) {
                            val closingButtons = @Composable {
                                DialogButtonFlow {
                                    confirmButton()
                                    dismissButton?.invoke()
                                }
                            }
                            if (startButton == null) closingButtons() else StartAndEndButtons(startButton, closingButtons)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The full screen form's top: the close button, the title and the button that confirms the dialog. In a short window
 * the bar is slimmer and also holds the content's own action, which would otherwise take a row of its own from the
 * little the keyboard leaves to the field.
 */
@Composable
private fun FullScreenDialogBar(
    title: @Composable () -> Unit,
    startButton: (@Composable () -> Unit)?,
    isCompact: Boolean,
    confirmButton: @Composable () -> Unit,
    onClose: () -> Unit,
) = Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = if (isCompact) COMPACT_FULL_SCREEN_BAR_HEIGHT else FULL_SCREEN_BAR_HEIGHT).padding(start = 4.dp, end = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    IconButton(onClick = onClose) {
        Icon(
            painter = painterResource(Res.drawable.ic_clear),
            contentDescription = stringResource(Res.string.close),
        )
    }
    ProvideContentColorTextStyle(
        contentColor = AlertDialogDefaults.titleContentColor,
        textStyle = MaterialTheme.typography.titleMedium,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            title()
        }
    }
    startButton?.invoke()
    DialogButtonFlow(content = confirmButton)
}

/**
 * Whether anything is moving the dialog's content, which the full screen form waits out before it shrinks back: a
 * finger or a mouse button held down anywhere on it, or a list in it still scrolling, the fling after a drag and the
 * mouse wheel included. The scrolling is counted rather than kept as state, since it changes on every frame of a
 * scroll and only matters once the dialog is waiting.
 */
private class DialogGestureTracker : NestedScrollConnection {
    var isPressed by mutableStateOf(false)
    private var scrollCount = 0

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (consumed != Offset.Zero) scrollCount++
        return Offset.Zero
    }

    /** Returns once nothing has been pressed or scrolled for [SETTLE_DURATION]. */
    suspend fun awaitSettled() {
        while (true) {
            snapshotFlow { isPressed }.first { !it }
            val scrollCountBefore = scrollCount
            delay(SETTLE_DURATION)
            if (!isPressed && scrollCount == scrollCountBefore) return
        }
    }
}

/** Reports what [tracker] waits out, watching the events on their way down without taking any of them. */
private fun Modifier.trackingGestures(tracker: DialogGestureTracker) = nestedScroll(tracker)
    .pointerInput(tracker) {
        awaitPointerEventScope {
            while (true) {
                tracker.isPressed = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }
            }
        }
    }

/** The row along the bottom of the dialog, in the color and the text style Material gives a dialog's buttons. */
@Composable
private fun DialogButtons(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = ProvideContentColorTextStyle(
    contentColor = MaterialTheme.colorScheme.primary,
    textStyle = MaterialTheme.typography.labelLarge,
) {
    Box(modifier = modifier) {
        content()
    }
}

/**
 * An action of the content's own at the start of the button row and the buttons that close the dialog at its end, on
 * one line where both fit and the action on a line of its own above them where they do not: on one line the closing
 * buttons were left whatever the action did not take, which in Hungarian broke "Mentés" into a letter per line.
 */
@Composable
private fun StartAndEndButtons(
    start: @Composable () -> Unit,
    end: @Composable () -> Unit,
) = Layout(
    content = {
        start()
        end()
    },
) { measurables, constraints ->
    val looseConstraints = constraints.copy(minWidth = 0)
    val endPlaceable = measurables[1].measure(looseConstraints)
    val startPlaceable = measurables[0].measure(looseConstraints)
    val width = constraints.maxWidth
    val spacing = BUTTON_SPACING.roundToPx()
    if (startPlaceable.width + spacing + endPlaceable.width <= width) {
        val height = maxOf(startPlaceable.height, endPlaceable.height)
        layout(width, height) {
            startPlaceable.placeRelative(0, (height - startPlaceable.height) / 2)
            endPlaceable.placeRelative(width - endPlaceable.width, (height - endPlaceable.height) / 2)
        }
    } else {
        layout(width, startPlaceable.height + spacing + endPlaceable.height) {
            startPlaceable.placeRelative(0, 0)
            endPlaceable.placeRelative(width - endPlaceable.width, startPlaceable.height + spacing)
        }
    }
}

/**
 * The buttons that close the dialog, laid out the way Material's own dialog does it: side by side with the confirming
 * one last, and stacked with it first once they no longer fit, which is why the row runs in the opposite direction.
 */
@Composable
private fun DialogButtonFlow(
    content: @Composable () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides if (layoutDirection == LayoutDirection.Ltr) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(BUTTON_SPACING),
            verticalArrangement = Arrangement.spacedBy(BUTTON_SPACING),
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection, content = content)
        }
    }
}

@Composable
private fun ProvideContentColorTextStyle(
    contentColor: Color,
    textStyle: TextStyle,
    content: @Composable () -> Unit,
) = CompositionLocalProvider(LocalContentColor provides contentColor) {
    ProvideTextStyle(value = textStyle, content = content)
}

/**
 * Below this the dialog goes full screen: what a small phone leaves above its keyboard is about this tall, and a
 * centered dialog that is about one song spends half of it on its title, its padding and its buttons.
 */
private val FULL_SCREEN_HEIGHT = 320.dp

/**
 * How far past [FULL_SCREEN_HEIGHT] the room has to grow before the full screen form shrinks back. The two forms do not
 * measure the room alike on every platform (on iOS the small form's window keeps clear of the safe area as well), and a
 * small form that measured itself under the height again would grow straight back.
 */
private val SHRINK_BACK_MARGIN = 48.dp

/** How long nothing has to move the content before the full screen form shrinks back. */
private val SETTLE_DURATION = 300.milliseconds
private val FULL_SCREEN_BAR_HEIGHT = 64.dp

/** The full screen form's bar in a short window, as tall as a touch target and no taller. */
private val COMPACT_FULL_SCREEN_BAR_HEIGHT = 48.dp

/** Material's own measurements of an `AlertDialog`, which the small form has to match the other dialogs in. */
private val DIALOG_MIN_WIDTH = 280.dp
private val DIALOG_MAX_WIDTH = 560.dp

/** How far the small form keeps from the screen's edges, the bars and the keyboard where its window fills the screen. */
private val DIALOG_WINDOW_MARGIN = 24.dp

/** Below this window width a dialog is as wide as Android's own dialog windows are on a phone. */
private val PHONE_WINDOW_MAX_WIDTH = 600.dp
private val PHONE_DIALOG_MAX_WIDTH = 320.dp
private val DIALOG_CORNER_RADIUS = 28.dp
private val DIALOG_PADDING = 24.dp
private val TITLE_PADDING = 16.dp
private val TEXT_PADDING = 24.dp
private val BUTTON_SPACING = 8.dp
