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

import android.content.ComponentName
import android.content.Context
import com.pandulapeter.campfire.AppIconSwitcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The launcher entry currently enabled, which every tap on a widget starts the app through ([openLinkAction]).
 *
 * The splash screen draws the icon of the component that was started, and only the launcher entries (the
 * `CampfireActivity…` aliases [AppIconSwitcher] moves between) have the icon of the theme color; `.CampfireIntentActivity`
 * has the default one. A widget's PendingIntent is the app's own, so it needs none of that alias's intent filters.
 *
 * Asked of the package manager off the main thread (Glance composes on it), once per process and again after every
 * switch, which then redraws the widgets: a tap aimed at an entry that has since been disabled would open nothing.
 */
internal object WidgetLaunchEntry {

    private val _component = MutableStateFlow<ComponentName?>(null)

    /** The entry, or null until it has been read (the taps then go through `.CampfireIntentActivity`). */
    val component: StateFlow<ComponentName?> = _component.asStateFlow()

    /** Reads the entry unless this process already has. */
    suspend fun load(context: Context) {
        if (_component.value == null) withContext(Dispatchers.IO) { _component.value = read(context) }
    }

    /** Reads the entry again after [AppIconSwitcher] changed it, and redraws every widget that taps could be aimed at it. */
    fun onChanged(context: Context) {
        _component.value = read(context)
        CoroutineScope(Dispatchers.Default).launch {
            RandomPicksWidget().refreshAll(context)
            SetlistWidget().refreshAll(context)
        }
    }

    private fun read(context: Context) = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component
}
