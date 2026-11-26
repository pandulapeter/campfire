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

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.window.SplashScreen
import androidx.compose.runtime.Composable
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import com.pandulapeter.campfire.shortcuts.appLinkIntent

/** Opens [link], one of the app's own, or the app as it is where there is none (before the app ever wrote a snapshot). */
@OptIn(ExperimentalGlanceApi::class)
@Composable
internal fun openLinkAction(link: String?): Action {
    val context = LocalContext.current
    return actionStartActivity(
        intent = if (link == null) launchIntent(context) else appLinkIntent(context = context, link = link),
        activityOptions = widgetLaunchOptions(),
    )
}

/**
 * The options every launch from a widget carries, a tap on a song in a widget's list included (whose template, not the
 * row, is what the system launches with), so that the app starts behind the same splash as from its launcher icon.
 *
 * Android 12 and above decide the splash by who asked: the launcher gets the icon, but a widget's tap is the app's own
 * PendingIntent, which counts as the app starting itself and gets only the background color. Android 13 lets the
 * launch ask for the icon; Android 12 has no way to, and keeps the plain one.
 */
internal fun widgetLaunchOptions(): Bundle = ActivityOptions.makeBasic().apply {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_ICON)
}.toBundle()

private fun launchIntent(context: Context) = context.packageManager.getLaunchIntentForPackage(context.packageName)
    ?: Intent().setPackage(context.packageName)
