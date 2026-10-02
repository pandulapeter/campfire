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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.close
import com.pandulapeter.campfire.presentation.resources.ic_clear
import org.jetbrains.compose.resources.painterResource

/**
 * Material's `AlertDialog`, for every dialog that is typed into, which grows into a full screen one once the window
 * is too short for it and stays that way until it closes.
 *
 * The window gets that short when the keyboard comes up in the web build, where it makes the page itself shorter (see
 * `app/web`), or in a phone held sideways: a centered dialog then keeps its title, its padding and its buttons and
 * squeezes what is between them, the field and the list being typed into, down to nothing. The full screen form puts
 * the close button, the title and the confirming button in one bar, the way a sheet's header does, and gives the rest
 * of the window to the content. It does not shrink back when the window grows again, which is what the keyboard going
 * away looks like: a list scrolled down puts it away ([HideKeyboardWhenScrolledDown]), and a dialog that turned back
 * into a small one under the finger scrolling it would move the very rows being read.
 *
 * Both forms are one layout whose parts move, rather than two dialogs: the content, with the focused field in it, is
 * the same node either way, so the field keeps its focus, and with it the keyboard, as the dialog grows around it. A
 * field that was rebuilt would lose both, the window would grow back, and the dialog would be where it started.
 *
 * @param startButton An action of the content's own, kept at the start of the button row apart from the two that
 *   close the dialog, and under the content in the full screen form.
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
    var isFullScreen by remember { mutableStateOf(windowHeight < FULL_SCREEN_HEIGHT) }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = !isFullScreen),
    ) {
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
        ) {
            LaunchedEffect(maxHeight) {
                if (maxHeight < FULL_SCREEN_HEIGHT) isFullScreen = true
            }
            val cornerRadius by animateDpAsState(if (isFullScreen) 0.dp else DIALOG_CORNER_RADIUS)
            Surface(
                modifier = modifier
                    .sizeIn(minWidth = DIALOG_MIN_WIDTH, maxWidth = if (isFullScreen) maxWidth else DIALOG_MAX_WIDTH)
                    .animateContentSize()
                    .then(if (isFullScreen) Modifier.fillMaxSize() else Modifier),
                shape = RoundedCornerShape(cornerRadius),
                color = AlertDialogDefaults.containerColor,
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                Column(
                    // In the full screen form the content runs on to the bottom edge, where the lists fade out, unless
                    // a button stands under it.
                    modifier = Modifier.padding(
                        top = if (isFullScreen) 0.dp else DIALOG_PADDING,
                        bottom = if (isFullScreen && startButton == null) 0.dp else DIALOG_PADDING,
                    ),
                ) {
                    if (isFullScreen) {
                        FullScreenDialogBar(
                            title = title,
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
                                .padding(bottom = if (isFullScreen && startButton == null) 0.dp else TEXT_PADDING),
                        ) {
                            text()
                        }
                    }
                    if (isFullScreen) {
                        if (startButton != null) {
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

/** The full screen form's top: the close button, the title, and the button that confirms the dialog. */
@Composable
private fun FullScreenDialogBar(
    title: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    onClose: () -> Unit,
) = Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = FULL_SCREEN_BAR_HEIGHT).padding(start = 4.dp, end = 12.dp),
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
    DialogButtonFlow(content = confirmButton)
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
 * Below this the dialog goes full screen: a phone's page in the web build with the keyboard up is about this tall,
 * and a centered dialog that is about one song spends half of it on its title, its padding and its buttons.
 */
private val FULL_SCREEN_HEIGHT = 320.dp
private val FULL_SCREEN_BAR_HEIGHT = 64.dp

/** Material's own measurements of an `AlertDialog`, which the small form has to match the other dialogs in. */
private val DIALOG_MIN_WIDTH = 280.dp
private val DIALOG_MAX_WIDTH = 560.dp
private val DIALOG_CORNER_RADIUS = 28.dp
private val DIALOG_PADDING = 24.dp
private val TITLE_PADDING = 16.dp
private val TEXT_PADDING = 24.dp
private val BUTTON_SPACING = 8.dp
