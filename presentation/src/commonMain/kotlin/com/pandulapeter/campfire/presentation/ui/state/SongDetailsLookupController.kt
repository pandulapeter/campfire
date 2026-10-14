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

import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields
import com.pandulapeter.campfire.data.model.domain.SongDetailsCandidate
import com.pandulapeter.campfire.data.model.domain.SongDetailsQuery
import com.pandulapeter.campfire.domain.api.useCases.LoadSongCreditsUseCase
import com.pandulapeter.campfire.domain.api.useCases.SearchSongDetailsUseCase
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.lookup.SongCreditsLookup
import com.pandulapeter.campfire.presentation.ui.dialogs.lookup.SongDetailsLookupState
import com.pandulapeter.campfire.presentation.ui.dialogs.slotKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The song details lookup sheet's search, and the songwriters of the recording open in its review. Held here rather than
 * by the sheet so that both survive the Android activity being recreated under it, and cleared with their requests
 * cancelled whenever the sheet stops being the dialog on screen ([DialogType.SongDetailsLookup], see
 * [DialogHost.setVisibleDialog]): a search nobody is waiting for still counts against MusicBrainz's one request a second.
 */
internal class SongDetailsLookupController(
    private val scope: CoroutineScope,
    private val searchSongDetails: SearchSongDetailsUseCase,
    private val loadSongCredits: LoadSongCreditsUseCase,
) {

    private val _songDetailsLookup = MutableStateFlow<SongDetailsLookupState>(SongDetailsLookupState.Idle)
    val songDetailsLookup = _songDetailsLookup.asStateFlow()

    private val _songCredits = MutableStateFlow<SongCreditsLookup?>(null)
    val songCredits = _songCredits.asStateFlow()

    private var searchJob: Job? = null
    private var creditsJob: Job? = null

    /** Searches for [query], cancelling the search still running: only the question asked last is still being asked. */
    fun searchSongDetails(query: SongDetailsQuery) {
        searchJob?.cancel()
        clearSongCredits()
        if (!query.isSearchable) {
            _songDetailsLookup.value = SongDetailsLookupState.Idle
            return
        }
        searchJob = scope.launch {
            searchSongDetails.invoke(query = query).collect { results ->
                _songDetailsLookup.value = SongDetailsLookupState.Active(query = query, results = results)
            }
        }
    }

    /**
     * Asks who wrote [candidate], cancelling the question about any other: only the recording open in the review is
     * asked about. One that has already been answered, or is being asked, is not asked again.
     */
    fun loadSongCredits(candidate: SongDetailsCandidate) {
        if (_songCredits.value?.candidateKey == candidate.key) return
        creditsJob?.cancel()
        creditsJob = scope.launch {
            loadSongCredits.invoke(candidate = candidate).collect { state ->
                _songCredits.value = SongCreditsLookup(candidateKey = candidate.key, state = state)
            }
        }
    }

    /** Forgets the songwriters asked for, so that [loadSongCredits] asks again — after a failure, or for another row. */
    fun clearSongCredits() {
        creditsJob?.cancel()
        creditsJob = null
        _songCredits.value = null
    }

    /**
     * Follows the dialogs as they change, as [CoverArtSearchController.onOpenDialogsChanged] does: cleared once no
     * lookup sheet is open, and asked as one is put up, from the form's draft, so that its first frame already says
     * that the search is running.
     */
    fun onOpenDialogsChanged(before: List<DialogType>, after: List<DialogType>) {
        if (after.none { it is DialogType.SongDetailsLookup }) clear()
        after.filter { dialog -> before.none { it.slotKey == dialog.slotKey } }
            .filterIsInstance<DialogType.SongDetailsLookup>()
            .forEach { searchSongDetails(songDetailsQueryOf(it.draft)) }
    }

    fun clear() {
        searchJob?.cancel()
        searchJob = null
        _songDetailsLookup.value = SongDetailsLookupState.Idle
        clearSongCredits()
    }
}

/** What the lookup is first asked from a form holding [draft]: the title, artist and album typed into it. */
internal fun songDetailsQueryOf(draft: Map<ChordProMetadataFields.Field, String>) = SongDetailsQuery(
    title = draft[ChordProMetadataFields.Field.TITLE].orEmpty().trim(),
    artist = draft[ChordProMetadataFields.Field.ARTIST].orEmpty().trim(),
    album = draft[ChordProMetadataFields.Field.ALBUM].orEmpty().trim(),
)
