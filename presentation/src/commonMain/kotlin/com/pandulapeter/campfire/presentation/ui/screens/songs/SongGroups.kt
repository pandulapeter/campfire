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

/**
 * The song list with the filter, the sort and the query it was built for: two equal lists for two filters are two
 * values. The song list scrolls by [filterKey] and keeps a tapped row in place once the list built for it arrives,
 * and with the key read from anywhere else a filter that left every song where it was would never deliver that
 * list, leaving the row to be put back in place by whatever changed the list next.
 */
data class SongGroups(
    val filterKey: String,
    val groups: List<SongGroup>,
)
