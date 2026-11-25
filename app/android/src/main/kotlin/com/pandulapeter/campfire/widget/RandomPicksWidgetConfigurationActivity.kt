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

import androidx.activity.compose.setContent
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidget
import com.pandulapeter.campfire.presentation.ui.widget.RandomPicksFilter
import com.pandulapeter.campfire.presentation.ui.widget.RandomPicksWidgetSetup
import com.pandulapeter.campfire.presentation.ui.widget.WidgetSnapshot

/** Asks for a random picks widget's own title and the tags and languages it draws from ([RandomPicksWidgetSetup]). */
class RandomPicksWidgetConfigurationActivity : WidgetConfigurationActivity() {

    override val widget: GlanceAppWidget = RandomPicksWidget()
    override val logName = "Random picks"

    override fun showSetup(snapshot: WidgetSnapshot, state: Preferences) = setContent {
        RandomPicksWidgetSetup(
            snapshot = snapshot,
            initialTitle = state[WidgetState.RANDOM_PICKS_TITLE].orEmpty(),
            initialFilter = RandomPicksFilter(
                tags = state[WidgetState.RANDOM_PICKS_TAGS].orEmpty(),
                languages = state[WidgetState.RANDOM_PICKS_LANGUAGES].orEmpty(),
            ),
            onDone = { title, filter ->
                apply(logDescription = "${if (title.isBlank()) "default" else "own"} title, ${filter.tags.size} tags, ${filter.languages.size} languages") { widgetState ->
                    if (title.isBlank()) widgetState.remove(WidgetState.RANDOM_PICKS_TITLE) else widgetState[WidgetState.RANDOM_PICKS_TITLE] = title.trim()
                    widgetState[WidgetState.RANDOM_PICKS_TAGS] = filter.tags
                    widgetState[WidgetState.RANDOM_PICKS_LANGUAGES] = filter.languages
                }
            },
        )
    }
}
