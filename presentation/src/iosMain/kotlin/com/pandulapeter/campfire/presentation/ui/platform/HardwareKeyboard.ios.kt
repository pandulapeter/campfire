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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.GameController.GCKeyboard
import platform.GameController.GCKeyboardDidConnectNotification
import platform.GameController.GCKeyboardDidDisconnectNotification

internal actual val isAppleKeyboard = true

@Composable
internal actual fun rememberHasHardwareKeyboard(): Boolean {
    var hasKeyboard by remember { mutableStateOf(GCKeyboard.coalescedKeyboard != null) }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val observers = listOf(GCKeyboardDidConnectNotification, GCKeyboardDidDisconnectNotification).map { name ->
            center.addObserverForName(name = name, `object` = null, queue = NSOperationQueue.mainQueue) { _ ->
                hasKeyboard = GCKeyboard.coalescedKeyboard != null
            }
        }
        onDispose { observers.forEach(center::removeObserver) }
    }
    return hasKeyboard
}
