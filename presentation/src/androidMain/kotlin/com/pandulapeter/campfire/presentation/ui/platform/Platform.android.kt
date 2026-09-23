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

import androidx.compose.ui.input.pointer.PointerEvent

internal actual val isDesktopPlatform = false

internal actual val isLaunchScreenWholeStartup = false

// The files live in the app's private storage, which no file manager will show.
internal actual val libraryLocation: LibraryLocation? = null

internal actual val platformStore: Distribution? = Distribution.PLAY_STORE

internal actual fun PointerEvent.verticalWheelNotches() = changes.fold(0f) { total, change -> total + change.scrollDelta.y }
