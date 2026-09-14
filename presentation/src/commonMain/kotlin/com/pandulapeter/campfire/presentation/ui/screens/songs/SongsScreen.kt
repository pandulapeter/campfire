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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_tune
import com.pandulapeter.campfire.presentation.resources.songs
import com.pandulapeter.campfire.presentation.resources.songs_create_song
import com.pandulapeter.campfire.presentation.resources.songs_new_song
import com.pandulapeter.campfire.presentation.resources.songs_search
import com.pandulapeter.campfire.presentation.resources.songs_sort_and_filter
import com.pandulapeter.campfire.presentation.resources.songs_sort_and_filter_active
import com.pandulapeter.campfire.presentation.resources.songs_unknown_artist
import com.pandulapeter.campfire.presentation.resources.songs_unsorted_label
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ControlsSidePanel
import com.pandulapeter.campfire.presentation.ui.components.DismissSheetWhenSidePanelAppears
import com.pandulapeter.campfire.presentation.ui.components.FastScroller
import com.pandulapeter.campfire.presentation.ui.components.ImportProgress
import com.pandulapeter.campfire.presentation.ui.components.HideKeyboardWhenScrolledDown
import com.pandulapeter.campfire.presentation.ui.components.KeepTopAppBarInSync
import com.pandulapeter.campfire.presentation.ui.components.ListColumns
import com.pandulapeter.campfire.presentation.ui.components.ListPlaceholder
import com.pandulapeter.campfire.presentation.ui.components.NewItemMenu
import com.pandulapeter.campfire.presentation.ui.components.SearchableTopAppBar
import com.pandulapeter.campfire.presentation.ui.components.SECTION_HEADER_GAP
import com.pandulapeter.campfire.presentation.ui.components.SectionHeader
import com.pandulapeter.campfire.presentation.ui.components.SongActionsButton
import com.pandulapeter.campfire.presentation.ui.components.SongListItem
import com.pandulapeter.campfire.presentation.ui.components.SongsControls
import com.pandulapeter.campfire.presentation.ui.components.TopLevelScreenLayout
import com.pandulapeter.campfire.presentation.ui.components.animateScrollToKey
import com.pandulapeter.campfire.presentation.ui.components.allowsNewItemMenu
import com.pandulapeter.campfire.presentation.ui.components.besideSidePanel
import com.pandulapeter.campfire.presentation.ui.components.hasRoomForSidePanel
import com.pandulapeter.campfire.presentation.ui.components.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.components.rememberHasLoadedLibrary
import com.pandulapeter.campfire.presentation.ui.components.rememberOverflowMenuState
import com.pandulapeter.campfire.presentation.ui.components.rememberRetainedLazyGridState
import com.pandulapeter.campfire.presentation.ui.components.songListColumnCount
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.isDesktopPlatform
import com.pandulapeter.campfire.presentation.localization.stringResource
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SongsScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    settledWidth: Dp,
    railWidth: Dp,
    contentPadding: PaddingValues,
) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val placeholder by viewModel.songsPlaceholder.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val isPerformanceModeEnabled by viewModel.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    val visibleDialog by viewModel.visibleDialog.collectAsStateWithLifecycle()
    val isSongFilterActive by viewModel.isSongFilterActive.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberRetainedLazyGridState(viewModel.songsScrollPosition)
    val isSidePanelVisible = hasRoomForSidePanel(settledWidth)
    val listContentPadding = contentPadding.besideSidePanel(isSidePanelVisible)
    val columnCount = songListColumnCount(
        settledWidth = settledWidth,
        contentPadding = contentPadding,
        isSidePanelVisible = isSidePanelVisible,
    )
    val hasLoadedLibrary = rememberHasLoadedLibrary(isLoading)
    KeepTopAppBarInSync(scrollBehavior, listState)
    HideKeyboardWhenScrolledDown(listState)
    DismissSheetWhenSidePanelAppears(
        isSidePanelVisible = isSidePanelVisible,
        isSheetVisible = visibleDialog == CampfireViewModel.DialogType.SongsControls,
        onDismiss = viewModel::dismissDialog,
    )
    TopLevelScreenLayout(
        modifier = modifier,
        railWidth = railWidth,
        appBar = {
            SearchableTopAppBar(
                scrollBehavior = scrollBehavior,
                title = stringResource(Res.string.songs),
                placeholder = stringResource(Res.string.songs_search),
                searchState = viewModel.songsSearch,
                actions = {
                    // The reasons this one comes and goes are the library being read and the mode being switched,
                    // both of which happen while the bar is being looked at, so it makes room for itself rather than
                    // appearing between two frames and pushing the action beside it aside as it lands.
                    AnimatedVisibility(visible = !isPerformanceModeEnabled && placeholder.allowsNewItemMenu) {
                        NewItemMenu(
                            viewModel = viewModel,
                            contentDescription = stringResource(Res.string.songs_new_song),
                            createLabel = stringResource(Res.string.songs_create_song),
                            onCreate = { viewModel.showDialog(CampfireViewModel.DialogType.NewSong) },
                        )
                    }
                    if (!isSidePanelVisible) {
                        SongsControlsAction(
                            isSongFilterActive = isSongFilterActive,
                            onClick = { viewModel.showDialog(CampfireViewModel.DialogType.SongsControls) },
                        )
                    }
                },
            )
        },
    ) {
        ImportProgress(isImporting = isImporting)
        Row {
            SongList(
                // Only the list tints the bar: the panel next to it scrolls under the same bar, but the bar is kept in
                // step with the list's own scroll position, and the two would fight over it.
                modifier = Modifier.weight(1f).fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                viewModel = viewModel,
                listState = listState,
                placeholder = placeholder,
                columnCount = columnCount,
                hasLoadedLibrary = hasLoadedLibrary,
                contentPadding = listContentPadding,
            )
            ControlsSidePanel(
                isVisible = isSidePanelVisible,
                contentPadding = contentPadding,
            ) { panelModifier, panelContentPadding ->
                SongsControls(
                    modifier = panelModifier,
                    viewModel = viewModel,
                    contentPadding = panelContentPadding,
                )
            }
        }
    }
}

/**
 * The action that opens the sorting and the filters where there is no room for them beside the list. It carries a
 * badge while a filter is on, since that is the one thing in the sheet that hides songs for the moment rather than
 * as a standing preference, and a list that is shorter than the library with nothing on screen saying why reads as
 * songs having gone missing. The side panel needs no such mark, since the selected chips are in it.
 */
@Composable
private fun SongsControlsAction(
    modifier: Modifier = Modifier,
    isSongFilterActive: Boolean,
    onClick: () -> Unit,
) = IconButton(
    modifier = modifier,
    onClick = onClick,
) {
    BadgedBox(
        badge = {
            AnimatedVisibility(
                visible = isSongFilterActive,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                Badge()
            }
        },
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_tune),
            // The badge is drawn and nothing else, so a screen reader would otherwise never hear that the list is narrowed.
            contentDescription = if (isSongFilterActive) {
                stringResource(Res.string.songs_sort_and_filter_active)
            } else {
                stringResource(Res.string.songs_sort_and_filter)
            },
        )
    }
}

@Composable
private fun SongList(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    listState: LazyGridState,
    placeholder: CampfireViewModel.Placeholder?,
    columnCount: Int,
    hasLoadedLibrary: Boolean,
    contentPadding: PaddingValues,
) {
    val songGroups by viewModel.songGroups.collectAsStateWithLifecycle()
    // Read straight off the field's own state, which is where the text lives now, see SearchState.
    val query = viewModel.songsSearch.textFieldState.text.toString()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val songFilter by viewModel.songFilter.collectAsStateWithLifecycle()
    val transpositions by viewModel.transpositions.collectAsStateWithLifecycle()
    val labelsOnEverySong by viewModel.labelsOnEverySong.collectAsStateWithLifecycle()
    val isPerformanceModeEnabled by viewModel.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    // Lyrics only mode takes the chords out of the viewer, and the key is the shortest way of writing them down.
    val shouldShowChords = userPreferences?.isLyricsOnlyModeEnabled != true
    val chordSpelling = userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default
    val filePicker = LocalFilePicker.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val layoutDirection = LocalLayoutDirection.current
    val coroutineScope = rememberCoroutineScope()
    // The section label of every list item (headers included), in the order of the lazy grid, for the fast scroller.
    val sectionLabels = remember(songGroups) {
        songGroups.flatMap { group ->
            val label = group.header?.fastScrollerLabel
            List(size = group.songs.size + (if (group.header == null) 0 else 1)) { label }
        }
    }

    // Scroll back to the top whenever the search query, the sorting or the filters change, before the new items
    // arrive. The combination that was last scrolled to the top is remembered across recompositions and state
    // restoration, so that coming back from the song details keeps the restored scroll position instead of jumping
    // to the top.
    val scrollToTopKey =
        "$query|${userPreferences?.sortingMode?.name}|${songFilter.selectedTags.sorted()}|${userPreferences?.tagMatchMode?.name}|${songFilter.selectedLanguages.sorted()}"
    var lastScrollToTopKey by rememberSaveable { mutableStateOf(scrollToTopKey) }
    LaunchedEffect(scrollToTopKey) {
        if (scrollToTopKey != lastScrollToTopKey) {
            lastScrollToTopKey = scrollToTopKey
            listState.scrollToItem(0)
        }
    }

    // A lazy grid holds on to the key of its first visible item across a change of its contents, which is right for
    // an edit and wrong for the library arriving: the read publishes a batch at a time in the order the files are
    // listed rather than the order they are sorted in, so a later batch lands songs above the ones already showing and
    // the grid follows its first row down, opening the app on a list that is already scrolled. Until the last batch
    // is in, the position is held by index instead - unless the user is scrolling it themselves, which a request
    // would cancel.
    if (!hasLoadedLibrary) {
        SideEffect {
            if (!listState.isScrollInProgress) {
                listState.requestScrollToItem(
                    index = listState.firstVisibleItemIndex,
                    scrollOffset = listState.firstVisibleItemScrollOffset,
                )
            }
        }
    }

    // The scroller takes the end inset over from the grid, so that it keeps to the edge of the screen beside the list
    // rather than standing in front of it.
    Row(
        modifier = modifier
    ) {
        LazyVerticalGrid(
            columns = ListColumns(columnCount),
            modifier = Modifier.weight(1f).fillMaxHeight(),
            state = listState,
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                top = SECTION_HEADER_GAP,
                bottom = contentPadding.calculateBottomPadding(),
            ),
        ) {
            placeholder?.let {
                item(
                    key = "placeholder",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "placeholder",
                ) {
                    ListPlaceholder(
                        modifier = listItemAnimation(listState, hasLoadedLibrary).fillMaxWidth(),
                        placeholder = it,
                        onRetry = viewModel::refresh,
                        onNewSong = if (isPerformanceModeEnabled) null else {
                            { viewModel.showDialog(CampfireViewModel.DialogType.NewSong) }
                        },
                        onDemoLibrary = if (isPerformanceModeEnabled) null else {
                            { viewModel.importDemoLibrary() }
                        },
                        onImport = if (isPerformanceModeEnabled) null else {
                            { viewModel.importFiles(filePicker) }
                        },
                    )
                }
            }
            songGroups.forEach { group ->
                group.header?.let { header ->
                    val key = "header_$header"
                    item(
                        key = key,
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "header",
                    ) {
                        SectionHeader(
                            modifier = listItemAnimation(listState, hasLoadedLibrary),
                            text = when (header) {
                                // A song can be created without an artist, and an empty pill would look broken.
                                is CampfireViewModel.SongGroup.Header.Artist -> header.name.ifBlank { stringResource(Res.string.songs_unknown_artist) }
                                is CampfireViewModel.SongGroup.Header.Letter -> header.letter.toString()
                                CampfireViewModel.SongGroup.Header.Symbols -> stringResource(Res.string.songs_unsorted_label)
                            },
                            onClick = { coroutineScope.launch { listState.animateScrollToKey(key) } },
                        )
                    }
                }
                items(
                    items = group.songs,
                    key = { "song_${it.fileName}" },
                    contentType = { "song" },
                ) { song ->
                    val actionsMenuState = rememberOverflowMenuState()
                    SongListItem(
                        modifier = listItemAnimation(listState, hasLoadedLibrary),
                        song = song,
                        // A song opened from the library is transposed in the preferences, so that is the only
                        // amount this list knows about: the setlists each hold their own.
                        key = viewModel.renderKey(song, transpositions[song.fileName, null], chordSpelling),
                        shouldShowChords = shouldShowChords,
                        labelsOnEverySong = labelsOnEverySong,
                        onClick = {
                            keyboardController?.hide()
                            viewModel.openSong(song)
                        },
                        // A shortcut to the row's own overflow menu, where holding a row is a natural way to ask for
                        // it: the same menu, hanging from the same button, since the same entries shown two different
                        // ways would read as two different things. In performance mode there is nothing to open
                        // either way, so a row does nothing but open a song.
                        onLongClick = if (isDesktopPlatform || isPerformanceModeEnabled) {
                            null
                        } else {
                            {
                                keyboardController?.hide()
                                actionsMenuState.open()
                            }
                        },
                        actions = if (isPerformanceModeEnabled) {
                            null
                        } else {
                            {
                                SongActionsButton(
                                    state = actionsMenuState,
                                    viewModel = viewModel,
                                    song = song,
                                    lockedSetlistFileName = null,
                                )
                            }
                        },
                    )
                }
            }
        }
        FastScroller(
            modifier = Modifier.padding(
                top = contentPadding.calculateTopPadding(),
                end = contentPadding.calculateEndPadding(layoutDirection),
                bottom = contentPadding.calculateBottomPadding(),
            ),
            gridState = listState,
            labelForItem = { sectionLabels.getOrNull(it) },
        )
    }
}

/**
 * The single character shown in the bubble of the fast scroller while this section is at the top of the list.
 */
private val CampfireViewModel.SongGroup.Header.fastScrollerLabel: String
    get() = when (this) {
        is CampfireViewModel.SongGroup.Header.Artist -> initial?.toString() ?: SYMBOLS_LABEL
        is CampfireViewModel.SongGroup.Header.Letter -> letter.toString()
        CampfireViewModel.SongGroup.Header.Symbols -> SYMBOLS_LABEL
    }

private const val SYMBOLS_LABEL = "#"
