/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import com.pandulapeter.campfire.shortcuts.appLinkIntent

/**
 * Opens [link], one of the app's own, or the app as it is where there is none (before the app ever wrote a snapshot),
 * through the launcher entry of the icon's color ([WidgetLaunchEntry]).
 */
@Composable
internal fun openLinkAction(link: String?): Action {
    val context = LocalContext.current
    val entry = WidgetLaunchEntry.component.collectAsState().value
    return actionStartActivity(
        when {
            entry != null -> (if (link == null) Intent(Intent.ACTION_MAIN) else Intent(Intent.ACTION_VIEW, Uri.parse(link))).setComponent(entry)
            link == null -> launchIntent(context)
            else -> appLinkIntent(context = context, link = link)
        },
    )
}

private fun launchIntent(context: Context) = context.packageManager.getLaunchIntentForPackage(context.packageName)
    ?: Intent().setPackage(context.packageName)
