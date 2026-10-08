/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.pandulapeter.campfire.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.ui.theme.appIconColor
import com.pandulapeter.campfire.presentation.ui.theme.appIconThemeColor
import kotlinx.browser.window
import kotlin.js.ExperimentalWasmJsInterop

/**
 * Shows the app icon the preferences ask for (`appIconThemeColor`) as the page's favicon: one of the recolored
 * `favicon-<color>.png` files, the app's own one included, since a browser keeps a favicon by its address and an
 * icon that changed under the same name would go on showing as it was - which is also why an icon that is redrawn is
 * given a new name rather than written over the old one. The name is also left in the browser's local
 * storage, where index.html looks for it before anything else is fetched, so that the tab and the loading screen of the
 * next visit are in that color from the start rather than turning into it once the preferences have been read, sixteen
 * megabytes later.
 */
@Composable
internal fun FaviconEffect(viewModel: CampfireViewModel) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val appIconColor = userPreferences.appIconThemeColor.appIconColor
    if (userPreferences != null) {
        LaunchedEffect(appIconColor) {
            setFavicon("favicon-${appIconColor.id}.png")
        }
    }
}

/**
 * Points the page's icon at [fileName], a file next to index.html, and remembers it for index.html. Storage that
 * cannot be written - blocked for the site, or a private window out of space - costs the next visit its first frames
 * in the app's own icon and nothing else. The icon is asked for by its versioned address, which is the one it is kept
 * in the browser under, so the icon of an app opened without a connection is there too.
 */
private fun setFavicon(fileName: String) {
    js(
        """{
            document.querySelector('link[rel="icon"]').setAttribute('href', window.campfireVersioned ? window.campfireVersioned(fileName) : fileName);
            try {
                window.localStorage.setItem('campfire-icon', fileName);
            } catch (e) {
            }
        }"""
    )
}
