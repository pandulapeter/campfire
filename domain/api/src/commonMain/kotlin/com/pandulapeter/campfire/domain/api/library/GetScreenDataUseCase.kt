/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.library

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.domain.api.library.ScreenData
import com.pandulapeter.campfire.domain.api.library.SongFilter
import kotlinx.coroutines.flow.Flow

interface GetScreenDataUseCase {

    /**
     * The library as the list screens show it. The [songFilter] is taken as a flow from the caller rather than read
     * from a repository, since nothing below the presentation layer holds it: it lives only as long as the UI does.
     *
     * @param shuffleHour The hour the [UserPreferences.SortingMode.SHUFFLE] order is drawn for, in hours since the epoch.
     * A flow from the caller rather than a clock read here, because when a new hour takes over is a question of the
     * screen: the order must never change under somebody reading the list, only the next time they open it.
     */
    operator fun invoke(songFilter: Flow<SongFilter>, shuffleHour: Flow<Int>): Flow<DataState<ScreenData>>
}
