/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.shortcuts

import com.pandulapeter.campfire.presentation.ui.platform.AppBuild
import kotlin.test.Test
import kotlin.test.assertEquals

class ShortcutPlatformsTest {

    @Test
    fun `the web has no Ctrl+N, and only the desktop and the web switch screens by number`() {
        assertEquals(AppBuild.entries - AppBuild.WEB, AppBuild.entries.filter { KeyShortcut.NEW.platforms.includes(it) })
        val desktopAndWeb = listOf(AppBuild.MACOS, AppBuild.WINDOWS, AppBuild.LINUX, AppBuild.WEB)
        KeyShortcut.screens.forEach { shortcut ->
            assertEquals(desktopAndWeb, AppBuild.entries.filter { shortcut.platforms.includes(it) }, shortcut.name)
        }
        assertEquals(AppBuild.entries, AppBuild.entries.filter { KeyShortcut.SAVE.platforms.includes(it) })
    }
}
