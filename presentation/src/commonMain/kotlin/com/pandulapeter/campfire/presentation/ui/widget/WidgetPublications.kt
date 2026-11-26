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

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.domain.api.library.ScreenData
import com.pandulapeter.campfire.presentation.ui.navigation.FeatureSwitches
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

/** What the widgets are handed: the [snapshot], and the cover address of each of its songs that has one, by file name. */
internal data class WidgetPublication(
    val snapshot: WidgetSnapshot,
    val coverAddresses: Map<String, String>,
)

/**
 * The snapshot of every library read whole in [screenData] (never a first read's batches, nor a failed read standing in
 * as empty, which would show the widgets a library shrinking and growing back), built by the latest of [builders] under
 * the switches of [preferences], once per change of what it holds.
 *
 * A flow rather than composed state, because the library changes while the app is out of sight too (a sync run, a
 * rebuild finishing after the app was left), when a window's recomposer is paused and its lifecycle-aware collections
 * have stopped: only the texts, the theme, and the formatters, which change in the app's own screens, come from the
 * composition, as [builders].
 */
internal fun widgetPublications(
    screenData: Flow<DataState<ScreenData>>,
    preferences: Flow<UserPreferences?>,
    builders: Flow<(library: ScreenData, features: FeatureSwitches) -> WidgetSnapshot>,
): Flow<WidgetPublication> = combine(
    screenData.mapNotNull { state -> (state as? DataState.Idle)?.data?.takeIf { it.isWholeLibrary } }.distinctUntilChanged(),
    // The switches alone, not every preference: a transposition or a counted song open would rebuild the whole library's
    // snapshot otherwise.
    preferences.filterNotNull().map { FeatureSwitches.of(it) to it.isCoverArtEnabled }.distinctUntilChanged(),
    builders,
) { library, (features, isCoverArtEnabled), build ->
    WidgetPublication(
        snapshot = build(library, features),
        coverAddresses = widgetCoverAddresses(songs = library.unfilteredSongs, isCoverArtEnabled = isCoverArtEnabled),
    )
}.distinctUntilChanged()
