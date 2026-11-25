/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.widget

import kotlinx.coroutines.flow.Flow

/**
 * Which of the app's widgets are on the home screen, as the platform tells: covers are fetched and made into thumbnails
 * only for songs a placed widget can show ([WidgetPublisher]).
 */
interface WidgetPlacement {

    /** The widgets placed now. */
    suspend fun placedWidgets(): PlacedWidgets

    /**
     * Emits whenever a widget may have been placed or removed while the app is in front, for [placedWidgets] to be asked
     * again; never, where the platform does not say (the app then asks each time it comes to the front).
     */
    val changes: Flow<Unit>
}

/** Whether any Random picks widget ([hasRandomPicks]) and any Setlist widget ([hasSetlist]) is placed. */
data class PlacedWidgets(
    val hasRandomPicks: Boolean,
    val hasSetlist: Boolean,
)
