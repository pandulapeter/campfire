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

internal actual val isDesktopPlatform = false

// Info.plist declares UIFileSharingEnabled and LSSupportsOpeningDocumentsInPlace, so the documents directory the
// library lives in shows up under "On My iPhone".
internal actual val libraryLocation: LibraryLocation? = LibraryLocation.FilesApp

// App Store guideline 3.1.1: no button or link may lead to a way of paying the developer other than an in-app purchase.
internal actual val canAskForDonations = false
