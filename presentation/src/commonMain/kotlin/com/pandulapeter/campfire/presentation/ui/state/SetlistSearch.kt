/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.domain.api.useCases.NormalizeSearchTextUseCase
import com.pandulapeter.campfire.presentation.ui.search.SearchableSong

/**
 * Whether a setlist answers the setlists screen's search. The songs are looked up in the library that was
 * normalized once ([SetlistsController]'s indexed songs) rather than normalized here, since this runs over every
 * setlist on every character typed; the setlist's own two lines are short enough to fold on the spot.
 *
 * A song whose file has gone missing can only be matched by the name in the entry, which is not what the user
 * searched for, so it matches nothing.
 */
internal fun Setlist.matchesSearch(
    normalizedQuery: String,
    songs: Map<String, SearchableSong>,
    normalizeSearchText: NormalizeSearchTextUseCase,
): Boolean =
    normalizeSearchText(title).contains(normalizedQuery) ||
        normalizeSearchText(description).contains(normalizedQuery) ||
        entries.any { entry -> songs[entry.songFileName]?.matches(normalizedQuery) == true }
