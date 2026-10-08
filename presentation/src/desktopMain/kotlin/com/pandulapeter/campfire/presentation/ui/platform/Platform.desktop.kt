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
import java.io.File

internal actual val isDesktopPlatform
    get() = when (PlatformImpersonation.platform) {
        null, ImpersonatedPlatform.MACOS, ImpersonatedPlatform.WINDOWS -> true
        ImpersonatedPlatform.ANDROID, ImpersonatedPlatform.IOS -> false
    }

internal actual val isLaunchScreenWholeStartup = true

internal actual val isCtrlWheelZoomOwnedByApp = true

internal actual val isStartupScreenHeldUntilAppReady = false

// The library is in the platform's application data folder, which the user can open and edit files in.
internal actual val isLibraryEditableOutsideApp get() = PlatformImpersonation.platform != ImpersonatedPlatform.ANDROID

internal actual val appIconSurface
    get() = when (PlatformImpersonation.platform) {
        ImpersonatedPlatform.ANDROID -> AppIconSurface.LAUNCHER
        ImpersonatedPlatform.IOS -> AppIconSurface.HOME_SCREEN
        ImpersonatedPlatform.MACOS -> AppIconSurface.DOCK
        ImpersonatedPlatform.WINDOWS -> AppIconSurface.TASKBAR
        null -> when {
            isMacOs -> AppIconSurface.DOCK
            isWindows -> AppIconSurface.TASKBAR
            else -> AppIconSurface.WINDOW
        }
    }

// The store of the machine, not of the build: a Mac build made by hand answers to the Mac App Store's rules and sends
// a review there like the one the store hands out. Linux has no store.
internal actual val platformStore: Distribution?
    get() = when (PlatformImpersonation.platform) {
        ImpersonatedPlatform.ANDROID -> Distribution.PLAY_STORE
        ImpersonatedPlatform.IOS -> Distribution.APP_STORE
        ImpersonatedPlatform.MACOS -> Distribution.MAC_APP_STORE
        ImpersonatedPlatform.WINDOWS -> Distribution.MICROSOFT_STORE
        null -> when {
            isMacOs -> Distribution.MAC_APP_STORE
            isWindows -> Distribution.MICROSOFT_STORE
            else -> null
        }
    }

internal actual fun PointerEvent.verticalWheelNotches() = changes.fold(0f) { total, change -> total + change.scrollDelta.y }

private val operatingSystem get() = System.getProperty("os.name").orEmpty().lowercase()

private val isMacOs get() = operatingSystem.contains("mac") || operatingSystem.contains("darwin")

// "darwin" has a "win" in it as well, so the two are not independent questions.
private val isWindows get() = !isMacOs && operatingSystem.contains("win")

/**
 * Where the desktop build keeps everything it owns: the library, the preferences, and the two files
 * `:app:desktop` uses to keep a second process from opening the same library.
 *
 * The Microsoft Store build keeps them in its package's own folder, which it is told the name of by the launcher (see
 * `packageReleaseMsix`): Windows gives a packaged app's writes under `%APPDATA%` to a hidden copy of the folder that
 * Explorer does not show, so the path the "Location" row opened would not be where the library is.
 *
 * Derived the same way the storage derives it; the two have to agree, so keep this in step with
 * `FileStorage.desktop.kt` in `:data:source:local:implementation`.
 */
fun desktopDataDirectory(): File {
    val userHome = File(System.getProperty("user.home").orEmpty())
    val packageFamilyName = System.getProperty(PACKAGE_FAMILY_NAME_PROPERTY).orEmpty()
    return when {
        isMacOs -> File(userHome, "Library/Application Support/$APPLICATION_NAME")
        isWindows && packageFamilyName.isNotEmpty() -> System.getenv("LOCALAPPDATA").orEmpty()
            .let { if (it.isEmpty()) File(userHome, "AppData/Local") else File(it) }
            .let { File(it, "Packages/$packageFamilyName/LocalState") }

        isWindows -> System.getenv("APPDATA").orEmpty()
            .let { if (it.isEmpty()) File(userHome, "AppData/Roaming") else File(it) }
            .let { File(it, APPLICATION_NAME) }

        else -> System.getenv("XDG_DATA_HOME").orEmpty()
            .let { if (it.isEmpty()) File(userHome, ".local/share") else File(it) }
            .let { File(it, APPLICATION_NAME.lowercase()) }
    }
}

private const val APPLICATION_NAME = "Campfire"
private const val PACKAGE_FAMILY_NAME_PROPERTY = "campfire.packageFamilyName"
