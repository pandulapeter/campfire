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
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.pandulapeter.campfire.chordpro.ChordProCoverArt
import com.pandulapeter.campfire.data.model.domain.CoverArtCandidate
import com.pandulapeter.campfire.data.model.domain.CoverArtQuery
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cover_art_address
import com.pandulapeter.campfire.presentation.resources.cover_art_address_failed
import com.pandulapeter.campfire.presentation.resources.cover_art_address_hint
import com.pandulapeter.campfire.presentation.resources.cover_art_address_mode
import com.pandulapeter.campfire.presentation.resources.cover_art_search
import com.pandulapeter.campfire.presentation.resources.cover_art_search_attribution
import com.pandulapeter.campfire.presentation.resources.cover_art_search_busy
import com.pandulapeter.campfire.presentation.resources.cover_art_search_failed
import com.pandulapeter.campfire.presentation.resources.cover_art_search_hint
import com.pandulapeter.campfire.presentation.resources.cover_art_search_loading
import com.pandulapeter.campfire.presentation.resources.cover_art_search_no_results
import com.pandulapeter.campfire.presentation.resources.cover_art_search_remove
import com.pandulapeter.campfire.presentation.resources.done
import com.pandulapeter.campfire.presentation.resources.ic_check
import com.pandulapeter.campfire.presentation.resources.ic_delete
import com.pandulapeter.campfire.presentation.resources.ic_search
import com.pandulapeter.campfire.presentation.resources.retry
import com.pandulapeter.campfire.presentation.resources.save
import com.pandulapeter.campfire.presentation.resources.song_details_change_cover_art
import com.pandulapeter.campfire.presentation.resources.song_details_set_cover_art
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_album
import com.pandulapeter.campfire.presentation.resources.songs_new_song_artist
import com.pandulapeter.campfire.presentation.resources.songs_new_song_title
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.CoverArt
import com.pandulapeter.campfire.presentation.ui.components.MAX_SEARCH_QUERY_LENGTH
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.SegmentedChoice
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.rememberClearTextButton
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import com.pandulapeter.campfire.presentation.ui.platform.bounceScrollableContent
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll

/**
 * The cover search: the song's artist, album and title as fields, and under them the records MusicBrainz and iTunes
 * know by those, each with its front cover, one of which is made the song's cover by Save. The other tab takes the
 * address of an image instead, for a cover neither catalogue has.
 *
 * The search is asked by the view model as the sheet is put up, with the fields as the file fills them, and after that
 * only when it is asked for — the keyboard's search key or the button — never per keystroke, since MusicBrainz allows
 * one request a second from the whole app. Its state is the view model's ([CampfireViewModel.coverArtSearch]), so that
 * it outlives the Android activity being recreated, and goes with the sheet.
 *
 * The sheet opens at its full height whatever it holds, since what it holds changes after it has opened: a sheet that
 * grew from a line saying the search is running to a grid of covers moved while it was still sliding up, and again
 * with every catalogue that answered.
 *
 * A record whose thumbnail does not load is taken off the grid rather than left as an empty square: the archive only
 * says that a record has no cover by answering its address with nothing, and a cover that cannot be seen cannot be
 * chosen either — so one that was selected while it was still loading is let go of as it leaves, or Save would write an
 * address that answers nothing. A new search gives every record back its place, since a thumbnail may have failed only
 * for the moment.
 */
@Composable
internal fun CoverArtSearchSheet(
    viewModel: CampfireViewModel,
    dialog: CampfireViewModel.DialogType.CoverArtSearch,
) {
    val searchState by viewModel.coverArtSearch.collectAsStateWithLifecycle()
    val initialQuery = remember(dialog.song.fileName) { viewModel.coverArtQueryOf(song = dialog.song, isEditorDraft = dialog.isEditorDraft) }
    var mode by rememberSaveable { mutableStateOf(CoverArtSheetMode.SEARCH) }
    var artist by rememberSaveable { mutableStateOf(initialQuery.artist) }
    var album by rememberSaveable { mutableStateOf(initialQuery.album) }
    var title by rememberSaveable { mutableStateOf(initialQuery.title) }
    var address by rememberSaveable { mutableStateOf(dialog.song.coverArtUrl.orEmpty()) }
    var selectedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var unavailableKeys by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val query = CoverArtQuery(artist = artist, album = album, title = title)
    val search = {
        keyboardController?.hide()
        selectedUrl = null
        // A record that is really without a cover is answered at once from the repository's memory of the failure and
        // drops out again, while one that only failed for the moment — a timeout, a busy archive — gets another chance.
        unavailableKeys = emptySet()
        viewModel.searchCoverArt(query)
    }
    val usableAddress = ChordProCoverArt.usableUrl(address)
    CampfireBottomSheet(
        title = stringResource(if (dialog.song.coverArtUrl == null) Res.string.song_details_set_cover_art else Res.string.song_details_change_cover_art),
        subtitle = songLabel(dialog.song),
        sheetMaxWidth = SHEET_MAX_WIDTH,
        actions = { close ->
            CoverArtSearchActions(
                isEditorDraft = dialog.isEditorDraft,
                canRemove = dialog.song.coverArtUrl != null,
                canSave = when (mode) {
                    CoverArtSheetMode.SEARCH -> selectedUrl != null && selectedUrl != dialog.song.coverArtUrl
                    CoverArtSheetMode.ADDRESS -> usableAddress != null && usableAddress != dialog.song.coverArtUrl
                },
                onRemove = {
                    viewModel.showDialog(CampfireViewModel.DialogType.RemoveSongCoverArt(song = dialog.song, isEditorDraft = dialog.isEditorDraft))
                },
                onSave = {
                    viewModel.setSongCoverArt(
                        fileName = dialog.song.fileName,
                        isEditorDraft = dialog.isEditorDraft,
                        url = when (mode) {
                            CoverArtSheetMode.SEARCH -> selectedUrl
                            CoverArtSheetMode.ADDRESS -> usableAddress
                        },
                    )
                    close()
                },
            )
        },
        onDismiss = { viewModel.dismissSheet(dialog) },
    ) { contentPadding ->
        // Choose the layout from the window, not the space above the keyboard. Moving a focused field between
        // the pinned area and a lazy grid item disposes that input as the IME opens and ends its editing session.
        // Small windows keep their fields in the scroll from the start; keyboard insets only change the padding.
        val windowSize = LocalWindowInfo.current.containerDpSize
        val pinFields = minOf(windowSize.width, windowSize.height) >= MIN_WINDOW_SIZE_FOR_PINNED_FIELDS
        val modeControls: @Composable () -> Unit = {
            SegmentedChoice(
                modifier = Modifier.padding(top = if (pinFields) 8.dp else 0.dp),
                shouldApplyPadding = pinFields,
                options = listOf(
                    CoverArtSheetMode.SEARCH to stringResource(Res.string.cover_art_search),
                    CoverArtSheetMode.ADDRESS to stringResource(Res.string.cover_art_address_mode),
                ),
                selected = mode,
                onSelected = {
                    keyboardController?.hide()
                    mode = it
                },
            )
        }
        if (pinFields) modeControls()
        AnimatedContent(
            modifier = Modifier.weight(1f),
            targetState = mode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
        ) { currentMode ->
            when (currentMode) {
                CoverArtSheetMode.SEARCH -> CoverArtResults(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    pinFields = pinFields,
                    scrollingControls = modeControls.takeUnless { pinFields },
                    state = searchState,
                    unavailableKeys = unavailableKeys,
                    selectedUrl = selectedUrl,
                    onSelected = { selectedUrl = it.coverArtUrl.takeUnless { url -> url == selectedUrl } },
                    onUnavailable = { candidate ->
                        unavailableKeys += candidate.key
                        if (candidate.coverArtUrl == selectedUrl) {
                            selectedUrl = null
                        }
                    },
                    onRetry = search,
                ) {
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
                }

                CoverArtSheetMode.ADDRESS -> CoverArtAddress(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    pinFields = pinFields,
                    scrollingControls = modeControls.takeUnless { pinFields },
                    address = address,
                    usableAddress = usableAddress,
                    onAddressChange = { address = it },
                    onDone = { keyboardController?.hide() },
                )
            }
        }
    }
}

/** The two ways the sheet finds a cover: picking one of the records a search found, or typing the address of one. */
private enum class CoverArtSheetMode {
    SEARCH,
    ADDRESS,
}

/**
 * The three things a record is found by. Artist and album share a row, since those two name a record, with the title
 * and the button under them, since the title is what is searched by only where the album is left empty.
 *
 * On larger windows they stay above the results. On small windows they scroll with the results so the fields can
 * move out of the way of the covers, especially while the keyboard is open.
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
    modifier = Modifier.fillMaxWidth(),
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
        // Outlined rather than filled: the header's Save is what finishes the sheet, and two filled buttons in sight
        // left the reader to work out which of them was the one that wrote the cover.
        OutlinedButton(
            modifier = Modifier.padding(top = 8.dp),
            enabled = canSearch,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            onClick = onSearch,
        ) {
            Icon(
                modifier = Modifier.size(ButtonDefaults.IconSize),
                painter = painterResource(Res.drawable.ic_search),
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
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
    trailingIcon = rememberClearTextButton(isVisible = value.isNotEmpty(), onClear = { onValueChange("") }),
    singleLine = true,
    // A name is searched for as it is spelled, so autocorrect is off for the reason it is off in the list screens' search.
    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
)

/**
 * The grid of what was found, or what stands in its place: why there is nothing to show yet, that the search is
 * running or waiting for the service, that it failed, or that it found nothing worth showing. The catalogues answer
 * one after the other, so the grid is shown as soon as either has found something, the other's records joining it at
 * the end, with an indicator closing the grid for as long as one of them is still being waited for.
 *
 * The [fields] stay above the scrolling grid on larger windows and become its first full-width item on small ones.
 * The mode controls also scroll with the grid on small windows. The service credit and messages belong to the grid
 * in both layouts.
 */
@Composable
private fun CoverArtResults(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    pinFields: Boolean,
    scrollingControls: (@Composable () -> Unit)?,
    state: CampfireViewModel.CoverArtSearchState,
    unavailableKeys: Set<String>,
    selectedUrl: String?,
    onSelected: (CoverArtCandidate) -> Unit,
    onUnavailable: (CoverArtCandidate) -> Unit,
    onRetry: () -> Unit,
    fields: @Composable () -> Unit,
) {
    val results = (state as? CampfireViewModel.CoverArtSearchState.Active)?.results
    val candidates = remember(results, unavailableKeys) { results?.candidates?.filterNot { it.key in unavailableKeys }.orEmpty() }
    val content = when {
        results == null -> ResultsContent.HINT
        candidates.isNotEmpty() -> ResultsContent.GRID
        // Only MusicBrainz ever asks to be waited for, so the line saying so is only true once nothing else is left.
        !results.isComplete -> if (results.busy.containsAll(results.pending)) ResultsContent.BUSY else ResultsContent.LOADING
        results.failed.isNotEmpty() -> ResultsContent.FAILED
        else -> ResultsContent.NO_RESULTS
    }
    val gridState = rememberLazyGridState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val keyboardDismissal = remember(keyboardController) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Bringing a focused field into view also scrolls the grid. Only a user scrolling towards the
                // covers should dismiss the keyboard, never the automatic scroll caused by opening it.
                if (source == NestedScrollSource.UserInput && available.y < 0f) keyboardController?.hide()
                return Offset.Zero
            }
        }
    }
    Column(modifier = modifier) {
        if (pinFields) {
            Box(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)) { fields() }
        }
        LazyVerticalGrid(
            modifier = Modifier.bounceScrollableContent(gridState).weight(1f).fillMaxWidth().nestedScroll(keyboardDismissal).fadingTopEdge(
                scrolled = { if (gridState.firstVisibleItemIndex > 0) Int.MAX_VALUE else gridState.firstVisibleItemScrollOffset },
                backgroundColor = sheetContainerColor(),
            ),
            state = gridState,
            columns = GridCells.Adaptive(TILE_MIN_WIDTH),
            contentPadding = contentPadding.only(bottom = true, extraStart = 16.dp, extraEnd = 16.dp, extraTop = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (scrollingControls != null) {
                item(
                    key = MODE_CONTROLS_ITEM_KEY,
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    scrollingControls()
                }
            }
            if (!pinFields) {
                item(
                    key = FIELDS_ITEM_KEY,
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    fields()
                }
            }
            if (content == ResultsContent.GRID) {
                items(
                    items = candidates,
                    key = { it.key },
                ) { candidate ->
                    CoverArtTile(
                        modifier = Modifier.animateItem(),
                        candidate = candidate,
                        isSelected = candidate.coverArtUrl == selectedUrl,
                        onClick = { onSelected(candidate) },
                        onUnavailable = { onUnavailable(candidate) },
                    )
                }
                if (results?.isComplete == false) {
                    item(
                        key = LOADING_ITEM_KEY,
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        Box(
                            modifier = Modifier.animateItem().fillMaxWidth().padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            } else {
                item(
                    // Keyed by what it says, so that one message fades out as the next fades in rather than being swapped.
                    key = content.name,
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    when (content) {
                        ResultsContent.LOADING, ResultsContent.BUSY -> CoverArtSearchMessage(
                            modifier = Modifier.animateItem(),
                            text = stringResource(if (content == ResultsContent.BUSY) Res.string.cover_art_search_busy else Res.string.cover_art_search_loading),
                        ) {
                            CircularProgressIndicator()
                        }

                        ResultsContent.FAILED -> CoverArtSearchMessage(
                            modifier = Modifier.animateItem(),
                            text = stringResource(Res.string.cover_art_search_failed),
                        ) {
                            OutlinedButton(onClick = onRetry) {
                                Text(stringResource(Res.string.retry))
                            }
                        }

                        ResultsContent.NO_RESULTS -> CoverArtSearchMessage(
                            modifier = Modifier.animateItem(),
                            text = stringResource(Res.string.cover_art_search_no_results),
                        )

                        ResultsContent.HINT, ResultsContent.GRID -> CoverArtSearchMessage(
                            modifier = Modifier.animateItem(),
                            text = stringResource(Res.string.cover_art_search_hint),
                        )
                    }
                }
            }
            item(
                key = ATTRIBUTION_ITEM_KEY,
                span = { GridItemSpan(maxLineSpan) },
            ) {
                CoverArtAttribution(modifier = Modifier.animateItem())
            }
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

/**
 * The address of a cover typed in, and under it the image it names, so that a typo is seen before it is saved. The
 * image is asked for only once the typing has paused ([ADDRESS_PREVIEW_DELAY]), since every half-typed address that
 * happens to be a valid one would otherwise be a request of its own. One that does not load can still be saved: a
 * host may refuse the web build what it gives the other three, and the file is read on all of them.
 */
@Composable
private fun CoverArtAddress(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    pinFields: Boolean,
    scrollingControls: (@Composable () -> Unit)?,
    address: String,
    usableAddress: String?,
    onAddressChange: (String) -> Unit,
    onDone: () -> Unit,
    scrollState: ScrollState = rememberScrollState(),
) {
    val field: @Composable (Modifier) -> Unit = { fieldModifier ->
        OutlinedTextField(
            modifier = fieldModifier.fillMaxWidth(),
            value = address,
            onValueChange = { onAddressChange(it.replace("\n", "").take(MAX_ADDRESS_LENGTH)) },
            label = { Text(stringResource(Res.string.cover_art_address)) },
            trailingIcon = rememberClearTextButton(isVisible = address.isNotEmpty(), onClear = { onAddressChange("") }),
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
        )
    }
    var previewUrl by remember { mutableStateOf(usableAddress) }
    LaunchedEffect(usableAddress) {
        if (usableAddress != null) delay(ADDRESS_PREVIEW_DELAY)
        previewUrl = usableAddress
    }
    Column(modifier = modifier) {
        if (pinFields) {
            field(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .fadingTopEdge(scrollState, sheetContainerColor())
                .bounceVerticalScroll(scrollState)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            scrollingControls?.invoke()
            if (!pinFields) field(Modifier)
            CoverArtAddressPreview(url = previewUrl)
        }
    }
}

/** The image [url] names, the indicator while it loads, or a line saying why there is none. */
@Composable
private fun CoverArtAddressPreview(
    url: String?,
) = Surface(
    modifier = Modifier.size(ADDRESS_PREVIEW_SIZE),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val painter = rememberAsyncImagePainter(model = url?.let(::CoverArt), contentScale = ContentScale.Crop)
    val painterState by painter.state.collectAsState()
    AnimatedContent(
        targetState = when {
            url == null -> AddressPreviewContent.HINT
            painterState is AsyncImagePainter.State.Success -> AddressPreviewContent.IMAGE
            painterState is AsyncImagePainter.State.Error -> AddressPreviewContent.FAILED
            else -> AddressPreviewContent.LOADING
        },
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentAlignment = Alignment.Center,
    ) { content ->
        when (content) {
            AddressPreviewContent.IMAGE -> Image(
                modifier = Modifier.fillMaxSize(),
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
            )

            AddressPreviewContent.LOADING -> Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            AddressPreviewContent.HINT, AddressPreviewContent.FAILED -> Box(
                modifier = Modifier.padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(if (content == AddressPreviewContent.HINT) Res.string.cover_art_address_hint else Res.string.cover_art_address_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private enum class AddressPreviewContent {
    HINT,
    LOADING,
    FAILED,
    IMAGE,
}

@Composable
private fun CoverArtSearchMessage(
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
 * Save and, where the song has a cover to take off, Remove, at the end of the sheet's header rather than in a bar of
 * their own under the results: a bar held at the bottom of the sheet sat on the keyboard and took the room the covers
 * had left. Remove is the bin rather than a labelled button, since it is the rare one of the two and asks before it
 * does anything.
 */
@Composable
private fun CoverArtSearchActions(
    isEditorDraft: Boolean,
    canRemove: Boolean,
    canSave: Boolean,
    onRemove: () -> Unit,
    onSave: () -> Unit,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
) {
    if (canRemove) {
        val isClosing = LocalIsSheetClosing.current
        IconButton(onClick = { if (!isClosing()) onRemove() }) {
            Icon(
                painter = painterResource(Res.drawable.ic_delete),
                contentDescription = stringResource(Res.string.cover_art_search_remove),
            )
        }
    }
    BottomSheetConfirmButton(
        enabled = canSave,
        onClick = onSave,
    ) {
        Text(stringResource(if (isEditorDraft) Res.string.done else Res.string.save))
    }
}

/**
 * The credit to the services, closing the search tab's grid: the records and the covers are theirs, and naming them is
 * what they ask of an app that shows them.
 */
@Composable
private fun CoverArtAttribution(
    modifier: Modifier = Modifier,
) = Text(
    modifier = modifier.fillMaxWidth().padding(top = 4.dp),
    text = stringResource(Res.string.cover_art_search_attribution),
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
)

/** Wide enough for four or five covers side by side on a tablet or a desktop window, where a sheet is otherwise 640dp. */
private val SHEET_MAX_WIDTH = 840.dp
/** Small windows scroll the fields with the covers, even before the keyboard opens, so focusing never relocates them. */
private val MIN_WINDOW_SIZE_FOR_PINNED_FIELDS = 600.dp
private val TILE_MIN_WIDTH = 128.dp
private val ADDRESS_PREVIEW_SIZE = 200.dp
private val ADDRESS_PREVIEW_DELAY = 500.milliseconds

/** Longer than any address a cover is found at, and short enough that the field's saved state stays small. */
private const val MAX_ADDRESS_LENGTH = 2048
private const val MODE_CONTROLS_ITEM_KEY = "mode_controls"
private const val FIELDS_ITEM_KEY = "fields"
private const val LOADING_ITEM_KEY = "loading"
private const val ATTRIBUTION_ITEM_KEY = "attribution"
