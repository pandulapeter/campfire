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

/**
 * True on platforms driven by a pointer rather than touch, which is what decides how a song's actions are reached:
 * a menu opened from the row and from the app bar, rather than a long press and a bottom sheet.
 */
internal expect val isDesktopPlatform: Boolean

/**
 * Where the library's files can be found, or null where there is nothing the user could go and look at. It is a
 * value rather than a string so that the wording that needs translating stays in the string resources, while a path
 * (which does not) can be handed over as it is.
 */
internal expect val libraryLocation: LibraryLocation?

/**
 * Whether the settings screen may offer a link that asks for money, which depends on where the build comes from.
 *
 * The App Store forbids pointing at any way of paying the developer other than an in-app purchase (guideline 3.1.1),
 * and a tip is such a payment, so the iOS build has no such link. Play's billing is only required for purchases of
 * digital content, which a donation that buys nothing is not, and the desktop installers and the web build answer to
 * no store at all.
 */
internal expect val canAskForDonations: Boolean

internal sealed interface LibraryLocation {

    /** An absolute path, shown as it is. */
    data class Folder(val path: String) : LibraryLocation

    /** Somewhere only a sentence can describe, which the settings screen translates. */
    data object FilesApp : LibraryLocation
}
