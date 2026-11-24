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
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

/**
 * The random picks widget's button: draws new picks for that one widget at once. Glance makes it by its class name, with
 * no arguments, which is why it is a public class of its own: Glance's rules keep every public one in a release build.
 */
class ShuffleAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { it.drawRandomPicks(System.currentTimeMillis()) }
        RandomPicksWidget().update(context, glanceId)
        println("Random picks widget drew new picks")
    }
}
