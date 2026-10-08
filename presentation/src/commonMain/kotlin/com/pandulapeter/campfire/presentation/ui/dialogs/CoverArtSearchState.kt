/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import com.pandulapeter.campfire.data.model.domain.CoverArtQuery
import com.pandulapeter.campfire.data.model.domain.CoverArtSearchResults
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel

/** What the cover search sheet shows under its fields, see [CampfireViewModel.coverArtSearch]. */
sealed interface CoverArtSearchState {

    /** Nothing has been asked yet, or there is nothing to ask by. */
    data object Idle : CoverArtSearchState

    /** A search that was asked, running or answered, see [CoverArtSearchResults]. */
    data class Active(val query: CoverArtQuery, val results: CoverArtSearchResults) : CoverArtSearchState
}
