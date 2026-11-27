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
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopDataDirectoryTest {

    private fun directory(
        operatingSystem: String,
        isDebug: Boolean = false,
        packageFamilyName: String = "",
        environment: Map<String, String> = emptyMap(),
    ) = desktopDataDirectory(
        operatingSystem = operatingSystem,
        userHome = "/home/player",
        environment = environment::get,
        packageFamilyName = packageFamilyName,
        isDebug = isDebug,
    )

    @Test
    fun `macOS keeps the library in Application Support`() =
        assertEquals(File("/home/player/Library/Application Support/Campfire"), directory("Mac OS X"))

    @Test
    fun `a debug run on macOS keeps a library of its own beside it`() =
        assertEquals(File("/home/player/Library/Application Support/Campfire Debug"), directory("Mac OS X", isDebug = true))

    @Test
    fun `Windows keeps the library in the roaming application data`() =
        assertEquals(File("C:/Users/player/AppData/Roaming/Campfire"), directory("Windows 11", environment = mapOf("APPDATA" to "C:/Users/player/AppData/Roaming")))

    @Test
    fun `a debug run on Windows keeps a library of its own beside it`() = assertEquals(
        File("C:/Users/player/AppData/Roaming/Campfire Debug"),
        directory("Windows 11", isDebug = true, environment = mapOf("APPDATA" to "C:/Users/player/AppData/Roaming")),
    )

    @Test
    fun `the Microsoft Store build keeps the library in its package's folder`() = assertEquals(
        File("C:/Users/player/AppData/Local/Packages/Campfire_abc/LocalState"),
        directory("Windows 11", packageFamilyName = "Campfire_abc", environment = mapOf("LOCALAPPDATA" to "C:/Users/player/AppData/Local")),
    )

    @Test
    fun `Linux keeps the library in the XDG data folder`() =
        assertEquals(File("/home/player/.local/share/campfire"), directory("Linux"))

    @Test
    fun `a debug run on Linux keeps a library of its own beside it`() =
        assertEquals(File("/data/campfire-debug"), directory("Linux", isDebug = true, environment = mapOf("XDG_DATA_HOME" to "/data")))
}
