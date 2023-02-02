package com.pandulapeter.campfire.shared.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings
import com.pandulapeter.campfire.shared.ui.catalogue.utilities.getUiStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

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
    val query: State<String>,
    val databases: State<List<Database>>,
    val songs: State<List<Song>>,
    val rawSongDetails: State<Map<String, RawSongDetails>?>,
    val selectedSong: State<Song?>,
    val modalBottomSheetState: ModalBottomSheetState,
    val songsScreenScrollState: LazyListState
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

    fun onSongClicked(song: Song) = coroutineScope.launch { viewModel.onSongClicked(song) }

    @OptIn(ExperimentalMaterialApi::class)
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

    fun onSortingModeChanged(sortingMode: UserPreferences.SortingMode) = userPreferences.value?.let { userPreferences ->
        scrollToTop()
        coroutineScope.launch {
            viewModel.onSortingModeChanged(
                userPreferences = userPreferences,
                sortingMode = sortingMode
            )
        }
    }

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

    companion object {

        @OptIn(ExperimentalMaterialApi::class)
        @Composable
        fun fromViewModel(viewModel: CampfireViewModel) = CampfireViewModelStateHolder(
            viewModel = viewModel,
            coroutineScope = rememberCoroutineScope(),
            uiStrings = viewModel.userPreferences.map { it.getUiStrings() }.distinctUntilChanged().collectAsState(CampfireStrings.English),
            uiMode = viewModel.uiMode.collectAsState(null),
            userPreferences = viewModel.userPreferences.collectAsState(null),
            selectedNavigationDestination = viewModel.selectedNavigationDestination.collectAsState(initial = null),
            navigationDestinations = viewModel.navigationDestinations.collectAsState(initial = emptyList()),
            isRefreshing = viewModel.shouldShowLoadingIndicator.collectAsState(false),
            query = viewModel.query.collectAsState(""),
            databases = viewModel.databases.collectAsState(emptyList()),
            songs = viewModel.songs.collectAsState(emptyList()),
            rawSongDetails = viewModel.songDetails.collectAsState(null),
            selectedSong = viewModel.selectedSong.collectAsState(null),
            modalBottomSheetState = rememberModalBottomSheetState(
                initialValue = ModalBottomSheetValue.Hidden,
                confirmStateChange = { it != ModalBottomSheetValue.HalfExpanded },
                skipHalfExpanded = true
            ),
            songsScreenScrollState = rememberLazyListState()
        )
    }
}