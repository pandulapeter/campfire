package com.pandulapeter.campfire.presentation.ui.screens.songs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_refresh
import com.pandulapeter.campfire.presentation.resources.ic_tune
import com.pandulapeter.campfire.presentation.resources.refresh
import com.pandulapeter.campfire.presentation.resources.songs_sort_and_filter
import com.pandulapeter.campfire.presentation.resources.songs_unsorted_label
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.CampfireTopAppBar
import com.pandulapeter.campfire.presentation.ui.components.FastScroller
import com.pandulapeter.campfire.presentation.ui.components.KeepTopAppBarInSync
import com.pandulapeter.campfire.presentation.ui.components.ListColumns
import com.pandulapeter.campfire.presentation.ui.components.ListPlaceholder
import com.pandulapeter.campfire.presentation.ui.components.SearchField
import com.pandulapeter.campfire.presentation.ui.components.SECTION_HEADER_GAP
import com.pandulapeter.campfire.presentation.ui.components.SectionHeader
import com.pandulapeter.campfire.presentation.ui.components.SongListItem
import com.pandulapeter.campfire.presentation.ui.components.SongsControlsSidePanel
import com.pandulapeter.campfire.presentation.ui.components.besideSidePanel
import com.pandulapeter.campfire.presentation.ui.components.hasRoomForSidePanel
import com.pandulapeter.campfire.presentation.ui.components.songListColumnCount
import com.pandulapeter.campfire.presentation.ui.platform.isDesktopPlatform
import com.pandulapeter.campfire.presentation.localization.stringResource
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SongsScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    settledWidth: Dp,
    contentPadding: PaddingValues
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val placeholder by viewModel.songsPlaceholder.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyGridState()
    val isSidePanelVisible = hasRoomForSidePanel(settledWidth)
    val listContentPadding = contentPadding.besideSidePanel(isSidePanelVisible)
    val columnCount = songListColumnCount(
        settledWidth = settledWidth,
        contentPadding = contentPadding,
        isSidePanelVisible = isSidePanelVisible
    )
    KeepTopAppBarInSync(scrollBehavior, listState)
    Row(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {
            CampfireTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    SearchField(
                        modifier = Modifier.fillMaxWidth(),
                        query = query,
                        onQueryChanged = viewModel::onQueryChanged
                    )
                },
                actions = {
                    if (isDesktopPlatform) {
                        RefreshAction(
                            isLoading = isLoading,
                            onClick = viewModel::refresh
                        )
                    }
                    if (!isSidePanelVisible) {
                        IconButton(onClick = { viewModel.showDialog(CampfireViewModel.DialogType.SongsControls) }) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_tune),
                                contentDescription = stringResource(Res.string.songs_sort_and_filter)
                            )
                        }
                    }
                }
            )
            SongList(
                modifier = Modifier.fillMaxSize(),
                viewModel = viewModel,
                listState = listState,
                placeholder = placeholder,
                // While the list is empty its own placeholder is the loading indicator; two of them at once would
                // only say the same thing twice.
                isRefreshing = isLoading && placeholder == null,
                columnCount = columnCount,
                contentPadding = listContentPadding
            )
        }
        SongsControlsSidePanel(
            isVisible = isSidePanelVisible,
            viewModel = viewModel,
            shouldIncludeSorting = true,
            contentPadding = contentPadding
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RefreshAction(
    isLoading: Boolean,
    onClick: () -> Unit
) = AnimatedContent(
    targetState = isLoading,
    transitionSpec = { fadeIn() togetherWith fadeOut() }
) { loading ->
    if (loading) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator(modifier = Modifier.size(32.dp))
        }
    } else {
        IconButton(onClick = onClick) {
            Icon(
                painter = painterResource(Res.drawable.ic_refresh),
                contentDescription = stringResource(Res.string.refresh)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SongList(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    listState: LazyGridState,
    placeholder: CampfireViewModel.Placeholder?,
    isRefreshing: Boolean,
    columnCount: Int,
    contentPadding: PaddingValues
) {
    val songGroups by viewModel.songGroups.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
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

    // Scroll back to the top whenever the search query or the sorting changes, before the new items arrive. The
    // combination that was last scrolled to the top is remembered across recompositions and state restoration, so
    // that coming back from the song details keeps the restored scroll position instead of jumping to the top.
    val scrollToTopKey = "$query|${userPreferences?.sortingMode?.name}"
    var lastScrollToTopKey by rememberSaveable { mutableStateOf(scrollToTopKey) }
    LaunchedEffect(scrollToTopKey) {
        if (scrollToTopKey != lastScrollToTopKey) {
            lastScrollToTopKey = scrollToTopKey
            listState.scrollToItem(0)
        }
    }

    RefreshableContainer(
        modifier = modifier,
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh
    ) {
        LazyVerticalGrid(
            columns = ListColumns(columnCount),
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                top = SECTION_HEADER_GAP,
                end = contentPadding.calculateEndPadding(layoutDirection),
                bottom = contentPadding.calculateBottomPadding() + 8.dp
            )
        ) {
            placeholder?.let {
                item(
                    key = "placeholder",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    ListPlaceholder(
                        modifier = Modifier.fillMaxWidth().animateItem(),
                        placeholder = it,
                        onRetry = viewModel::refresh
                    )
                }
            }
            songGroups.forEach { group ->
                group.header?.let { header ->
                    stickyHeader(key = "header_$header") { headerIndex ->
                        SectionHeader(
                            modifier = Modifier.animateItem(),
                            text = when (header) {
                                is CampfireViewModel.SongGroup.Header.Artist -> header.name
                                is CampfireViewModel.SongGroup.Header.Letter -> header.letter.toString()
                                CampfireViewModel.SongGroup.Header.Symbols -> stringResource(Res.string.songs_unsorted_label)
                            },
                            onClick = { coroutineScope.launch { listState.animateScrollToItem(headerIndex) } }
                        )
                    }
                }
                items(
                    items = group.songs,
                    key = { "song_${it.fileName}" }
                ) { song ->
                    SongListItem(
                        modifier = Modifier.animateItem(),
                        song = song,
                        onClick = {
                            keyboardController?.hide()
                            viewModel.openSong(song)
                        }
                    )
                }
            }
        }
        FastScroller(
            modifier = Modifier.padding(contentPadding),
            gridState = listState,
            labelForItem = { sectionLabels.getOrNull(it) }
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

/**
 * Pull to refresh only makes sense with touch input; on desktop the app bar has a refresh action instead.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RefreshableContainer(
    modifier: Modifier = Modifier,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) = if (isDesktopPlatform) {
    Box(
        modifier = modifier,
        content = content
    )
} else {
    val pullToRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        modifier = modifier,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                modifier = Modifier.align(Alignment.TopCenter),
                state = pullToRefreshState,
                isRefreshing = isRefreshing
            )
        },
        content = content
    )
}

private const val SYMBOLS_LABEL = "#"
