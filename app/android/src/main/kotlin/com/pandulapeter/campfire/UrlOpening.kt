/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/**
 * Opens [url] in a Custom Tab, except a Play listing, which goes to the Play Store app: a Custom Tab shows it as a
 * web page that can only send the user on to the store. Play claims its own https addresses, so the same URL is
 * handed to it by package, and a device without Play gets the web page after all. Returns false where nothing could
 * open it, for the UI to say so in the app's language.
 */
internal fun Activity.openUrl(url: String, isDarkTheme: Boolean): Boolean {
    val uri = url.toUri()
    if (uri.host == PLAY_STORE_HOST) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(PLAY_STORE_PACKAGE))
            return true
        } catch (_: ActivityNotFoundException) {
        }
    }
    return openInCustomTab(uri, isDarkTheme)
}

private fun Activity.openInCustomTab(uri: Uri, isDarkTheme: Boolean): Boolean = try {
    CustomTabsIntent.Builder()
        .setColorScheme(if (isDarkTheme) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT)
        .build()
        .launchUrl(this, uri)
    true
} catch (_: ActivityNotFoundException) {
    false
}

private const val PLAY_STORE_HOST = "play.google.com"
private const val PLAY_STORE_PACKAGE = "com.android.vending"
