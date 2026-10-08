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

import com.pandulapeter.campfire.data.model.domain.CoverArtQuery
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.domain.api.useCases.ClearCoverArtCacheUseCase
import com.pandulapeter.campfire.domain.api.useCases.GetCoverArtCacheSizeUseCase
import com.pandulapeter.campfire.domain.api.useCases.ParseChordProUseCase
import com.pandulapeter.campfire.domain.api.useCases.SearchCoverArtUseCase
import com.pandulapeter.campfire.presentation.ui.dialogs.CoverArtSearchState
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.SongEditTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The cover search sheet's search, and the copies of the covers the app keeps.
 *
 * @param songTextOf The text a metadata edit is built on, see `CampfireViewModel.songTextOf`.
 */
internal class CoverArtSearchController(
    private val scope: CoroutineScope,
    private val searchCoverArt: SearchCoverArtUseCase,
    getCoverArtCacheSize: GetCoverArtCacheSizeUseCase,
    private val clearCoverArtCache: ClearCoverArtCacheUseCase,
    private val parseChordPro: ParseChordProUseCase,
    private val songTextOf: (SongEditTarget) -> String?,
) {

    /**
     * The bytes the copies of the covers take up, null until they have been listed. Eager like every other state, so
     * that the settings screen opens on the number rather than fading it in; the repository lists the folder once per
     * change at most, and only one listing at a time.
     */
    val coverArtCacheSize = getCoverArtCacheSize().asState(scope, null)

    /**
     * The state of the cover search sheet ([DialogType.CoverArtSearch]). Held here rather than by the sheet so that a
     * search survives the Android activity being recreated under it, and cleared with its search cancelled whenever
     * the sheet stops being the dialog on screen, see [DialogHost.setVisibleDialog].
     */
    private val _coverArtSearch = MutableStateFlow<CoverArtSearchState>(CoverArtSearchState.Idle)
    val coverArtSearch = _coverArtSearch.asStateFlow()

    private var coverArtSearchJob: Job? = null

    /**
     * What the cover search sheet is prefilled with for [song]: its artist, album and title as the file writes them,
     * read from the text the details screen holds, and from the library's entry where that is not at hand, which has
     * no album and a title with the subtitle after it.
     */
    fun coverArtQueryOf(song: Song, target: SongEditTarget) = songTextOf(target)?.let { text ->
        val metadata = parseChordPro(text).metadata
        CoverArtQuery(
            artist = metadata.artist.orEmpty(),
            album = metadata.album.orEmpty(),
            title = metadata.title ?: song.title,
        )
    } ?: CoverArtQuery(artist = song.artist, album = "", title = song.title)

    /** Searches for [query], cancelling the search still running: only the question asked last is still being asked. */
    fun searchCoverArt(query: CoverArtQuery) {
        coverArtSearchJob?.cancel()
        if (!query.isSearchable) {
            _coverArtSearch.value = CoverArtSearchState.Idle
            return
        }
        coverArtSearchJob = scope.launch {
            searchCoverArt.invoke(query = query).collect { results ->
                _coverArtSearch.value = CoverArtSearchState.Active(query = query, results = results)
            }
        }
    }

    fun clearCoverArtSearch() {
        coverArtSearchJob?.cancel()
        coverArtSearchJob = null
        _coverArtSearch.value = CoverArtSearchState.Idle
    }

    fun clearCoverArtCache() {
        scope.launch { clearCoverArtCache.invoke() }
    }
}
