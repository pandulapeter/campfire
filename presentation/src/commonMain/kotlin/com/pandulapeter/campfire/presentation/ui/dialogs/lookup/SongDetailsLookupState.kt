/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.lookup

import com.pandulapeter.campfire.data.model.domain.CatalogueSearchResults
import com.pandulapeter.campfire.data.model.domain.SongCreditsState
import com.pandulapeter.campfire.data.model.domain.SongDetailsCandidate
import com.pandulapeter.campfire.data.model.domain.SongDetailsQuery
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel

/** What the song details lookup shows under its fields, see [CampfireViewModel.songDetailsLookup]. */
sealed interface SongDetailsLookupState {

    /** Nothing has been asked yet, or there is nothing to ask by. */
    data object Idle : SongDetailsLookupState

    /** A search that was asked, running or answered, see [CatalogueSearchResults]. */
    data class Active(val query: SongDetailsQuery, val results: CatalogueSearchResults<SongDetailsCandidate>) : SongDetailsLookupState
}

/** The songwriters of the recording open in the lookup's review, by its [SongDetailsCandidate.key]. */
data class SongCreditsLookup(val candidateKey: String, val state: SongCreditsState)
