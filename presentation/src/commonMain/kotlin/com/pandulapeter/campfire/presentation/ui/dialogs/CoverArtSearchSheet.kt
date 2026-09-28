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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.pandulapeter.campfire.data.model.domain.CoverArtCandidate
import com.pandulapeter.campfire.data.model.domain.CoverArtQuery
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cover_art_search
import com.pandulapeter.campfire.presentation.resources.cover_art_search_attribution
import com.pandulapeter.campfire.presentation.resources.cover_art_search_busy
import com.pandulapeter.campfire.presentation.resources.cover_art_search_failed
import com.pandulapeter.campfire.presentation.resources.cover_art_search_hint
import com.pandulapeter.campfire.presentation.resources.cover_art_search_loading
import com.pandulapeter.campfire.presentation.resources.cover_art_search_no_results
import com.pandulapeter.campfire.presentation.resources.cover_art_search_remove
import com.pandulapeter.campfire.presentation.resources.cover_art_search_title
import com.pandulapeter.campfire.presentation.resources.ic_check
import com.pandulapeter.campfire.presentation.resources.retry
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_album
import com.pandulapeter.campfire.presentation.resources.songs_new_song_artist
import com.pandulapeter.campfire.presentation.resources.songs_new_song_title
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.CoverArt
import com.pandulapeter.campfire.presentation.ui.components.MAX_SEARCH_QUERY_LENGTH
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import org.jetbrains.compose.resources.painterResource

/**
 * The cover search: the song's artist, album and title as fields, and under them the records MusicBrainz knows by
 * those, each with its front cover from the Cover Art Archive, one of which is made the song's cover by Save.
 *
 * The search is asked on opening, with the fields as the file fills them, and after that only when it is asked for —
 * the keyboard's search key or the button — never per keystroke, since the service allows one request a second from
 * the whole app. Its state is the view model's ([CampfireViewModel.coverArtSearch]), so that it outlives the Android
 * activity being recreated, and goes with the sheet.
 *
 * A record whose thumbnail does not load is taken off the grid rather than left as an empty square: the archive only
 * says that a record has no cover by answering its address with nothing, and a cover that cannot be seen cannot be
 * chosen either.
 */
@Composable
internal fun CoverArtSearchSheet(
    viewModel: CampfireViewModel,
    dialog: CampfireViewModel.DialogType.CoverArtSearch,
) {
    val searchState by viewModel.coverArtSearch.collectAsStateWithLifecycle()
    val initialQuery = remember(dialog.song.fileName) { viewModel.coverArtQueryOf(dialog.song) }
    var artist by rememberSaveable { mutableStateOf(initialQuery.artist) }
    var album by rememberSaveable { mutableStateOf(initialQuery.album) }
    var title by rememberSaveable { mutableStateOf(initialQuery.title) }
    var selectedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var unavailableIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val query = CoverArtQuery(artist = artist, album = album, title = title)
    val search = {
        keyboardController?.hide()
        selectedUrl = null
        viewModel.searchCoverArt(query)
    }
    // A search that is already running or done belongs to this sheet, which a recreated activity composes again.
    LaunchedEffect(Unit) {
        if (searchState == CampfireViewModel.CoverArtSearchState.Idle) viewModel.searchCoverArt(query)
    }
    CampfireBottomSheet(
        title = stringResource(Res.string.cover_art_search_title),
        subtitle = songLabel(dialog.song),
        sheetMaxWidth = SHEET_MAX_WIDTH,
        onDismiss = { viewModel.dismissSheet(dialog) },
    ) { contentPadding ->
        CoverArtQueryFields(
            artist = artist,
            album = album,
            title = title,
            onArtistChange = { artist = it },
            onAlbumChange = { album = it },
            onTitleChange = { title = it },
            canSearch = query.isSearchable,
            onSearch = search,
        )
        CoverArtResults(
            state = searchState,
            unavailableIds = unavailableIds,
            selectedUrl = selectedUrl,
            onSelected = { selectedUrl = it.coverArtUrl.takeUnless { url -> url == selectedUrl } },
            onUnavailable = { unavailableIds += it.id },
            onRetry = search,
        )
        CoverArtSearchActions(
            contentPadding = contentPadding,
            canRemove = dialog.song.coverArtUrl != null,
            canSave = selectedUrl != null,
            onRemove = {
                viewModel.setSongCoverArt(fileName = dialog.song.fileName, url = null)
                close()
            },
            onSave = {
                viewModel.setSongCoverArt(fileName = dialog.song.fileName, url = selectedUrl)
                close()
            },
        )
    }
}

/**
 * The three things a record is found by. Artist and album share a row, since those two name a record, with the title
 * and the button under them, since the title is what is searched by only where the album is left empty.
 */
@Composable
private fun CoverArtQueryFields(
    artist: String,
    album: String,
    title: String,
    onArtistChange: (String) -> Unit,
    onAlbumChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    canSearch: Boolean,
    onSearch: () -> Unit,
) = Column(
    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CoverArtQueryField(
            modifier = Modifier.weight(1f),
            value = artist,
            label = stringResource(Res.string.songs_new_song_artist),
            onValueChange = onArtistChange,
            onSearch = onSearch,
        )
        CoverArtQueryField(
            modifier = Modifier.weight(1f),
            value = album,
            label = stringResource(Res.string.song_editor_insert_album),
            onValueChange = onAlbumChange,
            onSearch = onSearch,
        )
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArtQueryField(
            modifier = Modifier.weight(1f),
            value = title,
            label = stringResource(Res.string.songs_new_song_title),
            onValueChange = onTitleChange,
            onSearch = onSearch,
        )
        Button(
            modifier = Modifier.padding(top = 8.dp),
            enabled = canSearch,
            onClick = onSearch,
        ) {
            Text(stringResource(Res.string.cover_art_search))
        }
    }
}

@Composable
private fun CoverArtQueryField(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
) = OutlinedTextField(
    modifier = modifier,
    value = value,
    onValueChange = { onValueChange(it.replace("\n", "").take(MAX_SEARCH_QUERY_LENGTH)) },
    label = { Text(label) },
    singleLine = true,
    // A name is searched for as it is spelled, so autocorrect is off for the reason it is off in the list screens' search.
    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
)

/**
 * The grid of what was found, or what stands in its place: why there is nothing to show yet, that the search is
 * running or waiting for the service, that it failed, or that it found nothing worth showing. It takes what height the
 * sheet has left and never gets shorter while the sheet is open, for the reason the pickers' lists do not.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ColumnScope.CoverArtResults(
    state: CampfireViewModel.CoverArtSearchState,
    unavailableIds: Set<String>,
    selectedUrl: String?,
    onSelected: (CoverArtCandidate) -> Unit,
    onUnavailable: (CoverArtCandidate) -> Unit,
    onRetry: () -> Unit,
) {
    val density = LocalDensity.current
    var tallestHeight by remember { mutableIntStateOf(0) }
    val candidates = (state as? CampfireViewModel.CoverArtSearchState.Results)?.candidates?.filterNot { it.id in unavailableIds }
    AnimatedContent(
        modifier = Modifier
            .weight(1f, fill = false)
            .heightIn(min = with(density) { tallestHeight.toDp() })
            .onSizeChanged { tallestHeight = maxOf(tallestHeight, it.height) },
        targetState = when {
            state is CampfireViewModel.CoverArtSearchState.Results && candidates.isNullOrEmpty() -> ResultsContent.NO_RESULTS
            state is CampfireViewModel.CoverArtSearchState.Results -> ResultsContent.GRID
            state is CampfireViewModel.CoverArtSearchState.Loading -> ResultsContent.LOADING
            state is CampfireViewModel.CoverArtSearchState.Busy -> ResultsContent.BUSY
            state is CampfireViewModel.CoverArtSearchState.Failed -> ResultsContent.FAILED
            else -> ResultsContent.HINT
        },
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentAlignment = Alignment.TopCenter,
    ) { content ->
        when (content) {
            ResultsContent.GRID -> {
                val gridState = rememberLazyGridState()
                LazyVerticalGrid(
                    modifier = Modifier.fadingTopEdge(gridState),
                    state = gridState,
                    columns = GridCells.Adaptive(TILE_MIN_WIDTH),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = candidates.orEmpty(),
                        key = { it.id },
                    ) { candidate ->
                        CoverArtTile(
                            modifier = Modifier.animateItem(),
                            candidate = candidate,
                            isSelected = candidate.coverArtUrl == selectedUrl,
                            onClick = { onSelected(candidate) },
                            onUnavailable = { onUnavailable(candidate) },
                        )
                    }
                }
            }

            ResultsContent.LOADING, ResultsContent.BUSY -> CoverArtSearchMessage(
                text = stringResource(if (content == ResultsContent.BUSY) Res.string.cover_art_search_busy else Res.string.cover_art_search_loading),
            ) {
                ContainedLoadingIndicator()
            }

            ResultsContent.FAILED -> CoverArtSearchMessage(
                text = stringResource(Res.string.cover_art_search_failed),
            ) {
                OutlinedButton(onClick = onRetry) {
                    Text(stringResource(Res.string.retry))
                }
            }

            ResultsContent.NO_RESULTS -> CoverArtSearchMessage(text = stringResource(Res.string.cover_art_search_no_results))

            ResultsContent.HINT -> CoverArtSearchMessage(text = stringResource(Res.string.cover_art_search_hint))
        }
    }
}

private enum class ResultsContent {
    HINT,
    LOADING,
    BUSY,
    FAILED,
    NO_RESULTS,
    GRID,
}

@Composable
private fun CoverArtSearchMessage(
    text: String,
    action: (@Composable () -> Unit)? = null,
) = Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
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

/**
 * One record: its cover, and under it the title, the artist and what kind of record it is from when. Selected, it is
 * ringed in the primary color and carries a check, the way a color disc of the settings screen is marked.
 */
@Composable
private fun CoverArtTile(
    modifier: Modifier = Modifier,
    candidate: CoverArtCandidate,
    isSelected: Boolean,
    onClick: () -> Unit,
    onUnavailable: () -> Unit,
) = Column(
    modifier = modifier
        .clip(MaterialTheme.shapes.medium)
        .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
        .padding(4.dp),
) {
    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .then(
                    if (isSelected) Modifier.border(width = 3.dp, color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) else Modifier
                ),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            AsyncImage(
                model = CoverArt(candidate.coverArtUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { onUnavailable() },
            )
        }
        if (isSelected) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    modifier = Modifier.padding(4.dp),
                    painter = painterResource(Res.drawable.ic_check),
                    contentDescription = null,
                )
            }
        }
    }
    Text(
        modifier = Modifier.padding(top = 6.dp),
        text = candidate.title,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    val details = listOfNotNull(candidate.artist.takeIf { it.isNotBlank() }, candidate.type, candidate.year)
    if (details.isNotEmpty()) {
        Text(
            text = details.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Save and, where the song has a cover to take off, Remove, with the credit to the two services under them: the
 * records and the covers are theirs, and naming them is what both ask of an app that shows them.
 */
@Composable
private fun CoverArtSearchActions(
    contentPadding: PaddingValues,
    canRemove: Boolean,
    canSave: Boolean,
    onRemove: () -> Unit,
    onSave: () -> Unit,
) = Column(
    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp).padding(contentPadding),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canRemove) {
            TextButton(onClick = onRemove) {
                Text(stringResource(Res.string.cover_art_search_remove))
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(
            enabled = canSave,
            onClick = onSave,
        ) {
            Text(stringResource(Res.string.save))
        }
    }
    Text(
        modifier = Modifier.padding(top = 8.dp),
        text = stringResource(Res.string.cover_art_search_attribution),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Wide enough for four or five covers side by side on a tablet or a desktop window, where a sheet is otherwise 640dp. */
private val SHEET_MAX_WIDTH = 840.dp
private val TILE_MIN_WIDTH = 128.dp
