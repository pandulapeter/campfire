package com.pandulapeter.campfire.shared.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.ScaffoldState
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.shared.ui.catalogue.components.SongDetailsScreenData
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings
import com.pandulapeter.campfire.shared.ui.catalogue.utilities.getUiStrings
import com.pandulapeter.campfire.shared.ui.screenComponents.setlists.SetlistItemKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.burnoutcrew.reorderable.ReorderableLazyListState
import org.burnoutcrew.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterialApi::class)
data class CampfireViewModelStateHolder @OptIn(ExperimentalMaterialApi::class) constructor(
    private val viewModel: CampfireViewModel,
    private val coroutineScope: CoroutineScope,
    val uiStrings: State<CampfireStrings>,
    val uiMode: State<UserPreferences.UiMode?>,
    val userPreferences: State<UserPreferences?>,
    val selectedNavigationDestination: State<CampfireViewModel.NavigationDestination?>,
    val navigationDestinations: State<List<CampfireViewModel.NavigationDestinationWrapper>>,
    val isRefreshing: State<Boolean>,
    val visibleDialog: State<CampfireViewModel.DialogType?>,
    val query: State<String>,
    val databases: State<List<Database>>,
    val songs: State<List<Song>>,
    val setlists: State<List<Setlist>>,
    val rawSongDetails: State<Map<String, RawSongDetails>>,
    val selectedSong: State<SongDetailsScreenData?>,
    val modalBottomSheetState: ModalBottomSheetState,
    val detailScreenCarouselState: LazyListState,
    val songsScreenScrollState: LazyListState,
    val setlistsScreenScrollState: ReorderableLazyListState,
    val scaffoldState: ScaffoldState
) {
    private var shouldScrollOnNextValue = false

    init {
        viewModel.songs.onEach {
            if (shouldScrollOnNextValue) {
                shouldScrollOnNextValue = false
                delay(100) // TODO: Hacky solution, might not work well on older devices
                songsScreenScrollState.animateScrollToItem(0)
            }
        }.launchIn(coroutineScope)
        viewModel.selectedSong.onEach { selectedSong ->
            if (selectedSong == null) {
                modalBottomSheetState.hide()
            } else {
                modalBottomSheetState.show()
            }
        }.launchIn(coroutineScope)
        snapshotFlow { modalBottomSheetState.isVisible }.onEach {
            if (!it) {
                onSongClosed()
            }
        }.launchIn(coroutineScope)
    }

    fun onQueryChanged(query: String) {
        scrollToTop()
        viewModel.onQueryChanged(query)
    }

    private fun scrollToTop() {
        shouldScrollOnNextValue = true
    }

    fun dismissDialog() = viewModel.dismissDialog()

    fun createNewSetlist(title: String) = coroutineScope.launch {
        dismissDialog()
        viewModel.createNewSetlist(
            newSetlistTitle = title.trim(),
            currentSetlists = setlists.value
        )
    }

    fun createNewDatabase(name: String, url: String) = coroutineScope.launch {
        viewModel.addNewDatabase(
            newDatabaseName = name.trim(),
            newDatabaseUrl = url.trim(),
            currentDatabases = databases.value
        )
        dismissDialog()
    }

    fun onSongClicked(songDetailsScreenData: SongDetailsScreenData) = coroutineScope.launch {
        viewModel.onSongClicked(songDetailsScreenData)
        delay(25L) // TODO: Bad practice
        detailScreenCarouselState.animateScrollToItem((songDetailsScreenData as? SongDetailsScreenData.SetlistData)?.initiallySelectedSongIndex ?: 0)
    }

    fun onSongClosed() = coroutineScope.launch { viewModel.onSongClicked(null) }

    fun onForceRefreshTriggered() = coroutineScope.launch { viewModel.onForceRefreshTriggered() }

    fun onDatabaseEnabledChanged(database: Database, isEnabled: Boolean) = databases.value.let { databases ->
        coroutineScope.launch {
            viewModel.onDatabaseEnabledChanged(
                databases = databases,
                database = database,
                isEnabled = isEnabled
            )
        }
    }

    fun onDatabaseRemoved(databaseUrl: String) = coroutineScope.launch {
        viewModel.updateDatabases(databases.value.filter { it.url != databaseUrl })
    }

    fun onDatabaseSelectedChanged(database: Database, isSelected: Boolean) = userPreferences.value?.let { userPreferences ->
        coroutineScope.launch {
            viewModel.onDatabaseSelectedChanged(
                userPreferences = userPreferences,
                database = database,
                isSelected = isSelected
            )
        }
    }

    fun onShouldShowExplicitSongsChanged(shouldShowExplicitSongs: Boolean) = userPreferences.value?.let { userPreferences ->
        coroutineScope.launch {
            viewModel.onShouldShowExplicitSongsChanged(
                userPreferences = userPreferences,
                shouldShowExplicitSongs = shouldShowExplicitSongs
            )
        }
    }

    fun onShouldShowSongsWithoutChordsChanged(shouldShowSongsWithoutChords: Boolean) = userPreferences.value?.let { userPreferences ->
        coroutineScope.launch {
            viewModel.onShouldShowSongsWithoutChordsChanged(
                userPreferences = userPreferences,
                shouldShowSongsWithoutChords = shouldShowSongsWithoutChords
            )
        }
    }

    fun onShowOnlyDownloadedSongsChanged(showOnlyDownloadedSongs: Boolean) = userPreferences.value?.let { userPreferences ->
        coroutineScope.launch {
            viewModel.onShowOnlyDownloadedSongsChanged(
                userPreferences = userPreferences,
                showOnlyDownloadedSongs = showOnlyDownloadedSongs
            )
        }
    }

    fun onSortingModeChanged(sortingMode: UserPreferences.SortingMode) = userPreferences.value?.let { userPreferences ->
        scrollToTop()
        coroutineScope.launch {
            viewModel.onSortingModeChanged(
                userPreferences = userPreferences,
                sortingMode = sortingMode
            )
        }
    }

    fun onNewSetlistClicked() = viewModel.onNewSetlistClicked()

    fun onUiModeChanged(uiMode: UserPreferences.UiMode) = userPreferences.value?.let { userPreferences ->
        coroutineScope.launch {
            viewModel.onUiModeChanged(
                userPreferences = userPreferences,
                uiMode = uiMode
            )
        }
    }

    fun onLanguageChanged(language: UserPreferences.Language) = userPreferences.value?.let { userPreferences ->
        coroutineScope.launch {
            viewModel.onLanguageChanged(
                userPreferences = userPreferences,
                language = language
            )
        }
    }

    fun onAddDatabaseClicked() = viewModel.onAddDatabaseClicked()

    fun onSetlistPickerClicked(songId: String, currentSetlistId: String?) = viewModel.onSetlistPickerClicked(songId, currentSetlistId)

    fun addSongToSetlist(songId: String, setlistId: String) = coroutineScope.launch {
        viewModel.addSongToSetlist(
            songId = songId,
            setlistId = setlistId,
            setlists = setlists.value
        )
    }

    fun removeSongFromSetlist(songId: String, setlistId: String) = coroutineScope.launch {
        viewModel.removeSongFromSetlist(
            songId = songId,
            setlistId = setlistId,
            setlists = setlists.value
        )
    }

    private fun showSnackbar(message: String) = coroutineScope.launch {
        scaffoldState.snackbarHostState.showSnackbar(
            message = message
        )
    }

    companion object {

        @OptIn(ExperimentalMaterialApi::class)
        @Composable
        fun fromViewModel(viewModel: CampfireViewModel): CampfireViewModelStateHolder {
            val setlists = viewModel.setlists.collectAsState(emptyList())
            val coroutineScope = rememberCoroutineScope()
            return CampfireViewModelStateHolder(
                viewModel = viewModel,
                coroutineScope = coroutineScope,
                uiStrings = viewModel.userPreferences.map { it.getUiStrings() }.distinctUntilChanged().collectAsState(CampfireStrings.English),
                uiMode = viewModel.uiMode.collectAsState(null),
                userPreferences = viewModel.userPreferences.collectAsState(null),
                selectedNavigationDestination = viewModel.selectedNavigationDestination.collectAsState(initial = null),
                navigationDestinations = viewModel.navigationDestinations.collectAsState(initial = emptyList()),
                isRefreshing = viewModel.shouldShowLoadingIndicator.collectAsState(false),
                visibleDialog = viewModel.visibleDialog.collectAsState(null),
                query = viewModel.query.collectAsState(""),
                databases = viewModel.databases.collectAsState(emptyList()),
                songs = viewModel.songs.collectAsState(emptyList()),
                setlists = setlists,
                rawSongDetails = viewModel.rawSongDetails.collectAsState(emptyMap()),
                selectedSong = viewModel.selectedSong.collectAsState(null),
                modalBottomSheetState = rememberModalBottomSheetState(
                    initialValue = ModalBottomSheetValue.Hidden,
                    confirmValueChange = { it != ModalBottomSheetValue.HalfExpanded },
                    skipHalfExpanded = true
                ),
                detailScreenCarouselState = rememberLazyListState(),
                songsScreenScrollState = rememberLazyListState(),
                setlistsScreenScrollState = rememberReorderableLazyListState(
                    canDragOver = { from, to ->
                        val fromKey = SetlistItemKey(from.key as? String)
                        val toKey = SetlistItemKey(to.key as? String)
                        fromKey.setlistId == toKey.setlistId
                    },
                    onMove = { from, to ->
                        val fromKey = SetlistItemKey(from.key as? String)
                        val toKey = SetlistItemKey(to.key as? String)
                        fromKey.setlistId?.let { setlistId ->
                            fromKey.songId?.let { fromSongId ->
                                toKey.songId?.let { toSongId ->
                                    if (setlistId == toKey.setlistId) {
                                        coroutineScope.launch {
                                            viewModel.swapSongsInSetlist(
                                                setlistId = setlistId,
                                                fromSongId = fromSongId,
                                                toSongId = toSongId,
                                                setlists = setlists.value
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                ),
                scaffoldState = rememberScaffoldState()
            )
        }
    }
}