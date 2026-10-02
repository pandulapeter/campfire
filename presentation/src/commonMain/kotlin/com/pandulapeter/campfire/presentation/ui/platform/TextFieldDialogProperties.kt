/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties

/** The typed dialog's window properties, allowing Android to deliver system and IME insets to its content. */
internal expect fun textFieldDialogProperties(isFullScreen: Boolean): DialogProperties

/**
 * Whether the typed dialog's window always fills the screen, leaving the dialog to center its small form and to answer
 * a tap around it itself. Android's does: a window sized to its content is resized whenever the content's height
 * changes, which the keyboard's padding makes it do while the keyboard slides, and a window and a content measuring
 * each other over and over move the dialog's bottom edge up and down, and change the height the dialog decides on its
 * full screen form from.
 */
internal expect val isTextFieldDialogWindowFullSize: Boolean

/**
 * Keeps the typed dialog's content clear of the system bars and the keyboard where its window reaches under them, as
 * Android's does; elsewhere the platform places the dialog clear of them itself, and padding it as well would take
 * them off twice. The full screen form follows the keyboard as it slides, its surface filling the window whatever the
 * padding. The small form is padded by where the keyboard is going rather than by where it is: its size and its
 * place follow the padding, and a padding that changed on every frame of the keyboard's slide would have it chasing
 * the keyboard with an animation started over on every one of them. It is only drawn following the slide.
 */
@Composable
internal expect fun Modifier.textFieldDialogInsetsPadding(isFullScreen: Boolean): Modifier
