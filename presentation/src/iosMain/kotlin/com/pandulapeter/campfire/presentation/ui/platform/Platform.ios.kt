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

// Info.plist declares UIFileSharingEnabled and LSSupportsOpeningDocumentsInPlace, so the documents directory the
// library lives in shows up under "On My iPhone".
internal actual val libraryLocation: LibraryLocation? = LibraryLocation.FilesApp

internal actual val currentDistribution: Distribution? = Distribution.APP_STORE

internal actual val storeForRating: Distribution? = Distribution.APP_STORE

internal actual fun PointerEvent.verticalWheelNotches() = changes.fold(0f) { total, change -> total + change.scrollDelta.y }
