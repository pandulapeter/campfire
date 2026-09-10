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
 * True on platforms driven by a pointer rather than touch (no pull to refresh, the scrollbar is always shown).
 */
internal expect val isDesktopPlatform: Boolean

/**
 * Where the library's files can be found, or null where there is nothing the user could go and look at. It is a
 * value rather than a string so that the wording that needs translating stays in the string resources, while a path
 * (which does not) can be handed over as it is.
 */
internal expect val libraryLocation: LibraryLocation?

internal sealed interface LibraryLocation {

    /** An absolute path, shown as it is. */
    data class Folder(val path: String) : LibraryLocation

    /** Somewhere only a sentence can describe, which the settings screen translates. */
    data object FilesApp : LibraryLocation
}
