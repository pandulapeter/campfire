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
internal actual val libraryLocationHint: String? = File(desktopDataDirectory(), LIBRARY_DIRECTORY).absolutePath

private fun desktopDataDirectory(): File {
    val userHome = File(System.getProperty("user.home").orEmpty())
    val operatingSystem = System.getProperty("os.name").orEmpty().lowercase()
    return when {
        operatingSystem.contains("mac") || operatingSystem.contains("darwin") -> File(userHome, "Library/Application Support/$APPLICATION_NAME")
        operatingSystem.contains("win") -> System.getenv("APPDATA").orEmpty()
            .let { if (it.isEmpty()) File(userHome, "AppData/Roaming") else File(it) }
            .let { File(it, APPLICATION_NAME) }

        else -> System.getenv("XDG_DATA_HOME").orEmpty()
            .let { if (it.isEmpty()) File(userHome, ".local/share") else File(it) }
            .let { File(it, APPLICATION_NAME.lowercase()) }
    }
}

private const val APPLICATION_NAME = "Campfire"
private const val LIBRARY_DIRECTORY = "library"
