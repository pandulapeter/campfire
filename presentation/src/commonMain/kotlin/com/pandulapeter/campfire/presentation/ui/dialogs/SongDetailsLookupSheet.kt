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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.SongCreditsState
import com.pandulapeter.campfire.data.model.domain.SongDetailsQuery
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cover_art_search_busy
import com.pandulapeter.campfire.presentation.resources.cover_art_search_failed
import com.pandulapeter.campfire.presentation.resources.cover_art_search_loading
import com.pandulapeter.campfire.presentation.resources.retry
import com.pandulapeter.campfire.presentation.resources.song_details_lookup
import com.pandulapeter.campfire.presentation.resources.song_details_lookup_attribution
import com.pandulapeter.campfire.presentation.resources.song_details_lookup_fill_in
import com.pandulapeter.campfire.presentation.resources.song_details_lookup_hint
import com.pandulapeter.campfire.presentation.resources.song_details_lookup_no_results
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.platform.bounceScrollableContent

/**
 * "Find song details online", put up over the Edit song details form: the form's title, artist and album as fields,
 * and under them the recordings MusicBrainz and iTunes know by those. The one tapped opens in place into what it would
 * fill into the form, one ticked or unticked row per field (see [songDetailsReviewRows]); on MusicBrainz its
 * songwriters are asked for as it opens, and join its rows when they arrive. The form stays open under the sheet; Fill in
 * puts the ticked values into its draft as the sheet slides away, and closing the sheet leaves it as it was: nothing
 * here writes the song, the form's Save does.
 *
 * The search is asked by the view model as the sheet is put up, and after that only when it is asked for — the
 * keyboard's search key or the button — never per keystroke, since MusicBrainz allows one request a second from the
 * whole app. Its state is the view model's ([CampfireViewModel.songDetailsLookup]), so that it outlives the Android
 * activity being recreated, and goes with the sheet; which row is open and which of its boxes are changed are saved
 * here. Everything but the header scrolls, the fields first, since they are set once and the results need the room.
 */
@Composable
internal fun SongDetailsLookupSheet(
    viewModel: CampfireViewModel,
    dialog: DialogType.SongDetailsLookup,
) {
    val searchState by viewModel.songDetailsLookup.collectAsStateWithLifecycle()
    val creditsLookup by viewModel.songCredits.collectAsStateWithLifecycle()
    val initialQuery = remember(dialog.song.fileName) { viewModel.songDetailsQueryOf(dialog.draft) }
    var title by rememberSaveable { mutableStateOf(initialQuery.title) }
    var artist by rememberSaveable { mutableStateOf(initialQuery.artist) }
    var album by rememberSaveable { mutableStateOf(initialQuery.album) }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    // The fields whose box the user changed from what it starts as, by name, so that the songwriters' rows arriving
    // later start as their own default rather than being reset or taken for changed.
    var toggledFields by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val query = SongDetailsQuery(title = title, artist = artist, album = album)
    val search = {
        keyboardController?.hide()
        selectedKey = null
        toggledFields = emptySet()
        viewModel.searchSongDetails(query)
    }
    val results = (searchState as? SongDetailsLookupState.Active)?.results
    val candidates = results?.candidates.orEmpty()
    val selected = candidates.firstOrNull { it.key == selectedKey }
    val creditsState = creditsLookup?.takeIf { it.candidateKey == selectedKey }?.state
    val credits = (creditsState as? SongCreditsState.Loaded)?.credits
    val rows = remember(dialog.draft, selected, credits) { selected?.let { songDetailsReviewRows(dialog.draft, it, credits) }.orEmpty() }
    val tickedFields = rows.filter { it.isTickedByDefault != (it.field.name in toggledFields) }.mapTo(mutableSetOf()) { it.field }
    // Asked from here rather than from the tap, so that a row open when a restored process comes back asks again.
    LaunchedEffect(selected) { selected?.let(viewModel::loadSongCredits) }
    CampfireBottomSheet(
        title = stringResource(Res.string.song_details_lookup),
        subtitle = songLabel(dialog.song),
        actions = { close ->
            BottomSheetConfirmButton(
                enabled = tickedFields.isNotEmpty(),
                onClick = {
                    viewModel.fillSongMetadata(dialog = dialog, draft = dialog.draft.withSongDetails(rows, tickedFields))
                    close()
                },
            ) { Text(stringResource(Res.string.song_details_lookup_fill_in)) }
        },
        onDismiss = { viewModel.dismissSheet(dialog) },
    ) { contentPadding ->
        val listState = rememberLazyListState()
        val content = when {
            results == null -> LookupContent.HINT
            candidates.isNotEmpty() -> LookupContent.LIST
            // Only MusicBrainz ever asks to be waited for, so the line saying so is only true once nothing else is left.
            !results.isComplete -> if (results.busy.containsAll(results.pending)) LookupContent.BUSY else LookupContent.LOADING
            results.failed.isNotEmpty() -> LookupContent.FAILED
            else -> LookupContent.NO_RESULTS
        }
        LazyColumn(
            // At the sheet's full height whatever it holds, since what it holds grows after it has opened.
            modifier = Modifier.weight(1f).fillMaxWidth().bounceScrollableContent(listState).fadingTopEdge(
                scrolled = { if (listState.firstVisibleItemIndex > 0) Int.MAX_VALUE else listState.firstVisibleItemScrollOffset },
                backgroundColor = sheetContainerColor(),
            ),
            state = listState,
            contentPadding = contentPadding.only(bottom = true, extraStart = 16.dp, extraEnd = 16.dp, extraTop = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = FIELDS_ITEM_KEY) {
                CatalogueQueryFields(
                    artist = artist,
                    album = album,
                    title = title,
                    onArtistChange = { artist = it },
                    onAlbumChange = { album = it },
                    onTitleChange = { title = it },
                    canSearch = query.isSearchable,
                    onSearch = search,
                )
            }
            if (content == LookupContent.LIST) {
                items(items = candidates, key = { it.key }) { candidate ->
                    val isSelected = candidate.key == selectedKey
                    SongDetailsCandidateRow(
                        modifier = Modifier.animateItem(),
                        candidate = candidate,
                        isSelected = isSelected,
                        rows = if (isSelected) rows else emptyList(),
                        tickedFields = if (isSelected) tickedFields else emptySet(),
                        creditsState = if (isSelected) creditsState else null,
                        onClick = {
                            keyboardController?.hide()
                            selectedKey = candidate.key.takeUnless { isSelected }
                            toggledFields = emptySet()
                        },
                        onToggle = { field -> toggledFields = if (field.name in toggledFields) toggledFields - field.name else toggledFields + field.name },
                        onRetryCredits = {
                            viewModel.clearSongCredits()
                            viewModel.loadSongCredits(candidate)
                        },
                    )
                }
                if (results?.isComplete == false) {
                    item(key = LOADING_ITEM_KEY) {
                        Box(
                            modifier = Modifier.animateItem().fillMaxWidth().padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            } else {
                // Keyed by what it says, so that one message fades out as the next fades in rather than being swapped.
                item(key = content.name) {
                    when (content) {
                        LookupContent.LOADING, LookupContent.BUSY -> LookupMessage(
                            modifier = Modifier.animateItem(),
                            text = stringResource(if (content == LookupContent.BUSY) Res.string.cover_art_search_busy else Res.string.cover_art_search_loading),
                        ) {
                            CircularProgressIndicator()
                        }

                        LookupContent.FAILED -> LookupMessage(
                            modifier = Modifier.animateItem(),
                            text = stringResource(Res.string.cover_art_search_failed),
                        ) {
                            OutlinedButton(onClick = search) {
                                Text(stringResource(Res.string.retry))
                            }
                        }

                        LookupContent.NO_RESULTS -> LookupMessage(
                            modifier = Modifier.animateItem(),
                            text = stringResource(Res.string.song_details_lookup_no_results),
                        )

                        LookupContent.HINT, LookupContent.LIST -> LookupMessage(
                            modifier = Modifier.animateItem(),
                            text = stringResource(Res.string.song_details_lookup_hint),
                        )
                    }
                }
            }
            item(key = ATTRIBUTION_ITEM_KEY) {
                // The credit the catalogues ask of an app that shows what they hold, and the answer to what is missing.
                Text(
                    modifier = Modifier.animateItem().fillMaxWidth().padding(top = 4.dp),
                    text = stringResource(Res.string.song_details_lookup_attribution),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private enum class LookupContent {
    HINT,
    LOADING,
    BUSY,
    FAILED,
    NO_RESULTS,
    LIST,
}

@Composable
private fun LookupMessage(
    modifier: Modifier = Modifier,
    text: String,
    action: (@Composable () -> Unit)? = null,
) = Column(
    modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
) {
    action?.invoke()
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

private const val FIELDS_ITEM_KEY = "fields"

private const val LOADING_ITEM_KEY = "loading"

private const val ATTRIBUTION_ITEM_KEY = "attribution"
