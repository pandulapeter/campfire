/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.useCases

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.domain.api.models.ScreenData
import com.pandulapeter.campfire.domain.api.models.SongFilter
import kotlinx.coroutines.flow.Flow

interface GetScreenDataUseCase {

    /**
     * The library as the list screens show it. The [songFilter] is taken as a flow from the caller rather than read
     * from a repository, since nothing below the presentation layer holds it: it lives only as long as the UI does.
     */
    operator fun invoke(songFilter: Flow<SongFilter>): Flow<DataState<ScreenData>>
}
