/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.storage.file

import org.koin.core.scope.Scope
import java.io.File

/**
 * The place each desktop operating system expects an application to keep the data it owns.
 *
 * The settings screen shows the resulting path, and cannot see this module, so it derives it the same way; keep the
 * two in step (`presentation/src/desktopMain/.../ui/platform/Platform.desktop.kt`).
 */
internal actual fun Scope.createFileStorage(): FileStorage = JvmFileStorage(desktopDataDirectory())

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
