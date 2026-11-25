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

import com.pandulapeter.campfire.presentation.ui.widget.PlacedWidgets
import com.pandulapeter.campfire.presentation.ui.widget.WidgetPlacement
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The widgets on the Home Screen, by the kinds WidgetKit lists (`RandomPicksWidget.kind`, `SetlistWidget`'s). WidgetKit
 * says nothing when one is added, but a widget is added with the app away, so it is asked each time the app returns.
 *
 * @param readPlacedKinds Swift's `WidgetCenter.shared.getCurrentConfigurations`, which has no Objective-C API this module
 *   could call, answering the kind of every widget placed (none where WidgetKit could not say).
 */
internal class IosWidgetPlacement(private val readPlacedKinds: ((List<String>) -> Unit) -> Unit) : WidgetPlacement {

    override suspend fun placedWidgets() = suspendCancellableCoroutine { continuation ->
        readPlacedKinds { kinds ->
            if (continuation.isActive) {
                continuation.resume(PlacedWidgets(hasRandomPicks = RANDOM_PICKS_KIND in kinds, hasSetlist = SETLIST_KIND in kinds))
            }
        }
    }

    override val changes = emptyFlow<Unit>()

    private companion object {
        const val RANDOM_PICKS_KIND = "RandomPicks"
        const val SETLIST_KIND = "Setlist"
    }
}
