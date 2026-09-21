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

import java.io.File

internal actual val isDesktopPlatform = true

/**
 * The folder the songs and setlists are in, which on desktop is a folder the user can open and edit files in.
 *
 * The path is derived the same way the storage itself derives it; the two have to agree, so keep this in step with
 * `FileStorage.desktop.kt` in `:data:source:local:implementation` (`:presentation` cannot see that module, which
 * only the `:app:*` modules depend on).
 */
internal actual val libraryLocation: LibraryLocation? = LibraryLocation.Folder(File(desktopDataDirectory(), LIBRARY_DIRECTORY).absolutePath)

// The installers are handed out by the project itself, with no store's rules to follow.
internal actual val canAskForDonations = true

// One build for three operating systems, each handed out from somewhere else, so the answer is only known once it
// runs.
internal actual val currentDistribution: Distribution? = when {
    isMacOs -> Distribution.MAC_APP_STORE
    isWindows -> Distribution.MICROSOFT_STORE
    operatingSystem.contains("linux") -> Distribution.LINUX
    else -> null
}

private val operatingSystem get() = System.getProperty("os.name").orEmpty().lowercase()

private val isMacOs get() = operatingSystem.contains("mac") || operatingSystem.contains("darwin")

// "darwin" has a "win" in it as well, so the two are not independent questions.
private val isWindows get() = !isMacOs && operatingSystem.contains("win")

private fun desktopDataDirectory(): File {
    val userHome = File(System.getProperty("user.home").orEmpty())
    return when {
        isMacOs -> File(userHome, "Library/Application Support/$APPLICATION_NAME")
        isWindows -> System.getenv("APPDATA").orEmpty()
            .let { if (it.isEmpty()) File(userHome, "AppData/Roaming") else File(it) }
            .let { File(it, APPLICATION_NAME) }

        else -> System.getenv("XDG_DATA_HOME").orEmpty()
            .let { if (it.isEmpty()) File(userHome, ".local/share") else File(it) }
            .let { File(it, APPLICATION_NAME.lowercase()) }
    }
}

private const val APPLICATION_NAME = "Campfire"
private const val LIBRARY_DIRECTORY = "library"
