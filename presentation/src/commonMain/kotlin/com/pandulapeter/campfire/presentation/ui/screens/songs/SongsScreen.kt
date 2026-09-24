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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.domain.api.models.SongSection
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_filter
import com.pandulapeter.campfire.presentation.resources.ic_filter_outline
import com.pandulapeter.campfire.presentation.resources.songs_create_song
import com.pandulapeter.campfire.presentation.resources.songs_new_song
import com.pandulapeter.campfire.presentation.resources.songs_search
import com.pandulapeter.campfire.presentation.resources.songs_filter
import com.pandulapeter.campfire.presentation.resources.songs_filter_active
import com.pandulapeter.campfire.presentation.resources.songs_sort
import com.pandulapeter.campfire.presentation.resources.songs_sorting_mode_by_artist
import com.pandulapeter.campfire.presentation.resources.songs_sorting_mode_by_title
import com.pandulapeter.campfire.presentation.resources.songs_unknown_artist
import com.pandulapeter.campfire.presentation.resources.songs_unsorted_label
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.AppBarOverlap
import com.pandulapeter.campfire.presentation.ui.components.ControlsSidePanel
import com.pandulapeter.campfire.presentation.ui.components.DismissSheetWhenSidePanelAppears
import com.pandulapeter.campfire.presentation.ui.components.FastScroller
import com.pandulapeter.campfire.presentation.ui.components.FAST_SCROLLER_WIDTH
import com.pandulapeter.campfire.presentation.ui.components.ImportProgress
import com.pandulapeter.campfire.presentation.ui.components.HideKeyboardWhenScrolledDown
import com.pandulapeter.campfire.presentation.ui.components.ListColumns
import com.pandulapeter.campfire.presentation.ui.components.ListPlaceholder
import com.pandulapeter.campfire.presentation.ui.components.NewItemMenu
import com.pandulapeter.campfire.presentation.ui.components.ScrollToTopWhenChanged
import com.pandulapeter.campfire.presentation.ui.components.SearchableTopAppBar
import com.pandulapeter.campfire.presentation.ui.components.SectionHeader
import com.pandulapeter.campfire.presentation.ui.components.SectionHeaderState
import com.pandulapeter.campfire.presentation.ui.components.SongActionsButton
import com.pandulapeter.campfire.presentation.ui.components.SongListItem
import com.pandulapeter.campfire.presentation.ui.components.SongFilters
import com.pandulapeter.campfire.presentation.ui.components.SortMenu
import com.pandulapeter.campfire.presentation.ui.components.allowsNewItemMenu
import com.pandulapeter.campfire.presentation.ui.components.animateAppBarReveal
import com.pandulapeter.campfire.presentation.ui.components.besideSidePanel
import com.pandulapeter.campfire.presentation.ui.components.hasRoomForSidePanel
import com.pandulapeter.campfire.presentation.ui.components.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.components.rememberListTopFade
import com.pandulapeter.campfire.presentation.ui.components.listTopFadeViewport
import com.pandulapeter.campfire.presentation.ui.components.fadingUnderListTop
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.pushedSectionHeader
import com.pandulapeter.campfire.presentation.ui.components.rememberHasLoadedLibrary
import com.pandulapeter.campfire.presentation.ui.components.rememberOverflowMenuState
import com.pandulapeter.campfire.presentation.ui.components.rememberRetainedLazyGridState
import com.pandulapeter.campfire.presentation.ui.components.sectionHeaderState
import com.pandulapeter.campfire.presentation.ui.components.songCardPadding
import com.pandulapeter.campfire.presentation.ui.components.songListColumnCount
import com.pandulapeter.campfire.presentation.ui.components.underAppBar
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.isDesktopPlatform
import com.pandulapeter.campfire.presentation.localization.stringResource
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun SongsScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    settledWidth: Dp,
    contentPadding: PaddingValues,
) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val placeholder by viewModel.songsPlaceholder.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val isPerformanceModeEnabled by viewModel.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    val visibleDialog by viewModel.visibleDialog.collectAsStateWithLifecycle()
    val isSongFilterActive by viewModel.isSongFilterActive.collectAsStateWithLifecycle()
    val hasSongFilters by viewModel.hasSongFilters.collectAsStateWithLifecycle()
    val listState = rememberRetainedLazyGridState(viewModel.songsScrollPosition)
    val isSidePanelVisible = hasSongFilters && hasRoomForSidePanel(settledWidth)
    val listContentPadding = contentPadding.besideSidePanel(isSidePanelVisible)
    val columnCount = songListColumnCount(
        settledWidth = settledWidth,
        contentPadding = contentPadding,
        isSidePanelVisible = isSidePanelVisible,
    )
    val hasLoadedLibrary = rememberHasLoadedLibrary(isLoading)
    HideKeyboardWhenScrolledDown(listState)
    DismissSheetWhenSidePanelAppears(
        isSidePanelVisible = isSidePanelVisible,
        isSheetVisible = visibleDialog == CampfireViewModel.DialogType.SongFilters,
        onDismiss = viewModel::dismissDialog,
    )
    // A sync run can take the last tag out of the library under an open sheet, which would leave it empty.
    LaunchedEffect(hasSongFilters) {
        if (!hasSongFilters && visibleDialog == CampfireViewModel.DialogType.SongFilters) viewModel.dismissDialog()
    }
    // The widest the app bar's buttons reach in over the list, which a pinned header keeps clear of - nothing once
    // the bar has filled in and moved the list down under itself.
    var appBarReach by remember { mutableStateOf(0.dp) }
    val appBarReveal = animateAppBarReveal(
        searchState = viewModel.songsSearch,
        // The ranked results of a search come in one group with no header, and a placeholder has none either.
        isShownWithoutSearch = placeholder != null,
    )
    Box(modifier = modifier.fillMaxSize()) {
        Row {
            // The bar spans the list alone rather than the whole screen, so that its buttons and the search stay at the
            // top of the list they act on instead of standing over the side panel beside it.
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                SongList(
                    modifier = Modifier.fillMaxSize().underAppBar { appBarReveal.value },
                    viewModel = viewModel,
                    listState = listState,
                    placeholder = placeholder,
                    columnCount = columnCount,
                    hasLoadedLibrary = hasLoadedLibrary,
                    contentPadding = listContentPadding,
                    appBarOverlap = AppBarOverlap.of(reach = appBarReach, appBarReveal = appBarReveal.value),
                )
                SearchableTopAppBar(
                    contentPadding = listContentPadding,
                    appBarReveal = { appBarReveal.value },
                    placeholder = stringResource(Res.string.songs_search),
                    searchState = viewModel.songsSearch,
                    onReachChanged = { appBarReach = it },
                    closedSearchActions = {
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
                    },
                    actions = {
                        SongSortMenu(viewModel)
                        // The last tag leaving the library takes the filters with it while the list is being looked at.
                        AnimatedVisibility(visible = hasSongFilters && !isSidePanelVisible) {
                            SongFiltersAction(
                                isSongFilterActive = isSongFilterActive,
                                onClick = { viewModel.showDialog(CampfireViewModel.DialogType.SongFilters) },
                            )
                        }
                    },
                )
            }
            ControlsSidePanel(
                isVisible = isSidePanelVisible,
                contentPadding = contentPadding,
            ) { panelModifier, panelContentPadding ->
                SongFilters(
                    modifier = panelModifier,
                    viewModel = viewModel,
                    contentPadding = panelContentPadding,
                )
            }
        }
        ImportProgress(isImporting = isImporting)
    }
}

@Composable
private fun SongSortMenu(viewModel: CampfireViewModel) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    SortMenu(
        contentDescription = stringResource(Res.string.songs_sort),
        options = listOf(
            UserPreferences.SortingMode.BY_ARTIST to stringResource(Res.string.songs_sorting_mode_by_artist),
            UserPreferences.SortingMode.BY_TITLE to stringResource(Res.string.songs_sorting_mode_by_title),
        ),
        selected = userPreferences?.sortingMode,
        onSelected = viewModel::setSortingMode,
    )
}

/**
 * The action that opens the filters where there is no room for them beside the list. While a filter is on its funnel
 * is filled in and carries a badge in the accent color, since a filter hides songs for the moment rather than as a
 * standing preference, and a list that is shorter than the library with nothing on screen saying why reads as songs
 * having gone missing. The badge is not Material's default error red: nothing is wrong, the list is only narrowed.
 * The side panel needs no such mark, since the selected chips are in it.
 */
@Composable
private fun SongFiltersAction(
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
                Badge(containerColor = MaterialTheme.colorScheme.primary)
            }
        },
    ) {
        Crossfade(targetState = isSongFilterActive) { isActive ->
            Icon(
                painter = painterResource(if (isActive) Res.drawable.ic_filter else Res.drawable.ic_filter_outline),
                // The badge and the fill are drawn and nothing else, so a screen reader would otherwise never hear that
                // the list is narrowed.
                contentDescription = if (isActive) {
                    stringResource(Res.string.songs_filter_active)
                } else {
                    stringResource(Res.string.songs_filter)
                },
            )
        }
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
    appBarOverlap: AppBarOverlap,
) {
    val songGroups by viewModel.songGroups.collectAsStateWithLifecycle()
    val isSearchOpen by viewModel.songsSearch.isOpen.collectAsStateWithLifecycle()
    // Read straight off the field's own state, which is where the text lives now, see SearchState. A closed search
    // narrows nothing whatever its field still holds, so closing one changes what the list holds as much as emptying
    // the field does.
    val query = if (isSearchOpen) viewModel.songsSearch.textFieldState.text.toString() else ""
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
    val coroutineScope = rememberCoroutineScope()
    // The section label of every list item (headers included), in the order of the lazy grid, for the fast scroller.
    val sectionLabels = remember(songGroups) {
        songGroups.flatMap { group ->
            val label = group.header?.fastScrollerLabel
            List(size = group.songs.size + (if (group.header == null) 0 else 1)) { label }
        }
    }
    val pushedHeader = pushedSectionHeader(listState, contentType = "header")
    val topFade = rememberListTopFade(listState)

    ScrollToTopWhenChanged(
        listState = listState,
        key = "$query|${userPreferences?.sortingMode?.name}|${songFilter.selectedTags.sorted()}|${userPreferences?.tagMatchMode?.name}|${songFilter.selectedLanguages.sorted()}|${userPreferences?.languageMatchMode?.name}",
        contents = songGroups,
    )

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

    // The header row reaches through the grid's end padding to the edge the app bar's reach is measured from, while
    // cards stop before the scroller's touch column.
    val headerEndPadding = FAST_SCROLLER_WIDTH + contentPadding.calculateEndPadding(LocalLayoutDirection.current)
    Box(modifier = modifier) {
        LazyVerticalGrid(
            columns = ListColumns(columnCount),
            modifier = Modifier.fillMaxSize().listTopFadeViewport(topFade),
            state = listState,
            contentPadding = contentPadding.only(start = true, end = true, bottom = true, extraEnd = FAST_SCROLLER_WIDTH, extraBottom = 8.dp),
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
                    stickyHeader(
                        key = "header_${header.key}",
                        contentType = "header",
                    ) { headerIndex ->
                        val headerState = sectionHeaderState(listState, headerIndex)
                        SectionHeader(
                            modifier = listItemAnimation(listState, hasLoadedLibrary),
                            state = headerState,
                            endPadding = headerEndPadding,
                            text = header.displayText(),
                            onClick = { coroutineScope.launch { listState.animateScrollToItem(headerIndex) } },
                            opacity = if (headerState.visibleFraction < 1f) 0f else 1f,
                            appBarOverlap = appBarOverlap,
                        )
                    }
                }
                itemsIndexed(
                    items = group.songs,
                    key = { _, song -> "song_${song.fileName}" },
                    contentType = { _, _ -> "song" },
                ) { songIndex, song ->
                    val actionsMenuState = rememberOverflowMenuState()
                    SongListItem(
                        modifier = listItemAnimation(listState, hasLoadedLibrary).fadingUnderListTop(topFade),
                        song = song,
                        cardPadding = songCardPadding(songIndex, columnCount),
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
        // The grid clips at its top edge. Draw the outgoing header here while it is pushed, so its fade can continue
        // past that edge under the transparent app bar rather than ending at it; a bar that has filled in is drawn
        // over it.
        pushedHeader?.let { pushed ->
            val header = songGroups.mapNotNull { it.header }.firstOrNull { "header_${it.key}" == pushed.key }
            if (header != null) {
                val density = LocalDensity.current
                SectionHeader(
                    modifier = Modifier.offset { pushed.offset }.width(with(density) { pushed.width.toDp() }).clearAndSetSemantics {},
                    text = header.displayText(),
                    // Pinned for as long as it is being pushed away: it keeps the width it had in the bar's place rather than
                    // widening again as it leaves.
                    state = SectionHeaderState(visibleFraction = pushed.visibleFraction, pinnedFraction = 1f),
                    endPadding = headerEndPadding,
                    onClick = null,
                    contentOpacity = pushed.visibleFraction,
                    pushedDistancePx = pushed.pushedDistance,
                    appBarOverlap = appBarOverlap,
                )
            }
        }
        FastScroller(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = appBarOverlap.height).padding(contentPadding.only(top = true, end = true, bottom = true)),
            gridState = listState,
            labelForItem = { sectionLabels.getOrNull(it) },
        )
    }
}

/**
 * The single character shown in the bubble of the fast scroller while this section is at the top of the list.
 */
private val SongSection.Header.fastScrollerLabel: String
    get() = when (this) {
        is SongSection.Header.Artist -> initial?.toString() ?: SYMBOLS_LABEL
        is SongSection.Header.Letter -> letter.toString()
        SongSection.Header.Symbols -> SYMBOLS_LABEL
    }

@Composable
private fun SongSection.Header.displayText(): String = when (this) {
    // A song can be created without an artist, so the section still needs a name.
    is SongSection.Header.Artist -> name.ifBlank { stringResource(Res.string.songs_unknown_artist) }
    is SongSection.Header.Letter -> letter.toString()
    SongSection.Header.Symbols -> stringResource(Res.string.songs_unsorted_label)
}

private const val SYMBOLS_LABEL = "#"
