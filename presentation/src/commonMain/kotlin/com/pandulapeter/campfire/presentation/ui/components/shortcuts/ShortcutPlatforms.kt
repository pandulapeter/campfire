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

/** Where a [KeyShortcut] works, which is also the only place its hint is shown. */
internal enum class ShortcutPlatforms {
    EVERYWHERE,

    /**
     * The desktop application and the web: the shortcuts a phone's or a tablet's system keeps for itself or has no
     * window chrome to stand in for (switching screens, Settings).
     */
    DESKTOP_AND_WEB,

    /**
     * Everywhere but the web, for a key the browser acts on before the page hears it and never lets it prevent: Chrome
     * opens a new window on Ctrl / Cmd + N whatever the page does.
     */
    NOT_WEB,
    ;

    fun includes(build: AppBuild) = when (this) {
        EVERYWHERE -> true
        DESKTOP_AND_WEB -> build != AppBuild.ANDROID && build != AppBuild.IOS
        NOT_WEB -> build != AppBuild.WEB
    }
}
