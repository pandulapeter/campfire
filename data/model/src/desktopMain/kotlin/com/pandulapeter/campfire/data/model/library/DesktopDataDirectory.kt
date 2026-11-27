/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.library

import java.io.File

/**
 * Where the desktop build keeps everything it owns: the library and preferences `DesktopFileStorage` reads and writes,
 * and beside them `:app:desktop`'s single-instance guard and the process log. One function for both, since a storage
 * and an app that disagreed would guard one folder and fill another.
 *
 * The place each operating system expects an application's data. The Microsoft Store build, which the launcher tells
 * its package family name (`packageReleaseMsix`), uses its package's own folder instead of `%APPDATA%`, where Windows
 * would put what it writes in a hidden copy of the folder that Explorer does not show.
 *
 * A debug `run` (`campfire.debug`, which only `app/desktop/build.gradle.kts`'s `run` task sets) uses a sibling folder,
 * `Campfire Debug` (`campfire-debug` on Linux), so it can run beside an installed build without sharing its library or
 * being refused by its single-instance guard.
 */
fun desktopDataDirectory() = desktopDataDirectory(
    operatingSystem = System.getProperty("os.name").orEmpty(),
    userHome = System.getProperty("user.home").orEmpty(),
    environment = System::getenv,
    packageFamilyName = System.getProperty(PACKAGE_FAMILY_NAME_PROPERTY).orEmpty(),
    isDebug = System.getProperty(DEBUG_PROPERTY) == "true",
)

internal fun desktopDataDirectory(
    operatingSystem: String,
    userHome: String,
    environment: (String) -> String?,
    packageFamilyName: String,
    isDebug: Boolean,
): File {
    val system = operatingSystem.lowercase()
    val home = File(userHome)
    val name = if (isDebug) "$APPLICATION_NAME Debug" else APPLICATION_NAME
    fun folder(variable: String, fallback: String) = environment(variable).orEmpty().let { if (it.isEmpty()) File(home, fallback) else File(it) }
    return when {
        // Before Windows: "darwin" has a "win" in it as well.
        system.contains("mac") || system.contains("darwin") -> File(home, "Library/Application Support/$name")
        system.contains("win") && packageFamilyName.isNotEmpty() -> File(folder("LOCALAPPDATA", "AppData/Local"), "Packages/$packageFamilyName/LocalState")
        system.contains("win") -> File(folder("APPDATA", "AppData/Roaming"), name)
        else -> File(folder("XDG_DATA_HOME", ".local/share"), name.lowercase().replace(' ', '-'))
    }
}

private const val APPLICATION_NAME = "Campfire"
private const val PACKAGE_FAMILY_NAME_PROPERTY = "campfire.packageFamilyName"
private const val DEBUG_PROPERTY = "campfire.debug"
