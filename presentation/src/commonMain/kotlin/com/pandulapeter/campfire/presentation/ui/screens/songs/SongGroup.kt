/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songs

import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.domain.api.models.SongSection

/** @param header Null for the results of a search, which are ranked rather than filed under anything. */
data class SongGroup(
    val header: SongSection.Header?,
    val songs: List<Song>,
)
