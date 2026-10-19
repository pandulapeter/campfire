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

import com.pandulapeter.campfire.data.model.catalogue.CoverArtCandidate
import com.pandulapeter.campfire.data.model.catalogue.CoverArtSearchResults

/** What the cover search shows in place of its grid, or the grid itself. */
internal enum class ResultsContent {
    HINT,
    LOADING,
    BUSY,
    FAILED,
    NO_RESULTS,
    GRID,
}

/**
 * What the cover search shows for its [results], of which only [shownCandidates] still have a tile. A search that
 * found records but could not reach a thumbnail of one of the dropped ones has failed rather than found nothing: those
 * records may well have covers, so "nothing was found" would blame the query where Retry is what helps.
 */
internal fun resultsContentOf(
    results: CoverArtSearchResults?,
    shownCandidates: List<CoverArtCandidate>,
    unreachableKeys: Set<String>,
) = when {
    results == null -> ResultsContent.HINT
    shownCandidates.isNotEmpty() -> ResultsContent.GRID
    // Only MusicBrainz ever asks to be waited for, so the line saying so is only true once nothing else is left.
    !results.isComplete -> if (results.busy.containsAll(results.pending)) ResultsContent.BUSY else ResultsContent.LOADING
    results.failed.isNotEmpty() || results.candidates.any { it.key in unreachableKeys } -> ResultsContent.FAILED
    else -> ResultsContent.NO_RESULTS
}
