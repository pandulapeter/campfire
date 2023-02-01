package com.pandulapeter.campfire.shared.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongDetails
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings
import com.pandulapeter.campfire.shared.ui.catalogue.utilities.getUiStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
    val songDetails: State<SongDetails?>,
    val modalBottomSheetState: ModalBottomSheetState
) {
    fun onQueryChanged(query: String) = viewModel.onQueryChanged(query)

    @OptIn(ExperimentalMaterialApi::class)
    fun onSongClicked(song: Song) = coroutineScope.launch {
        modalBottomSheetState.show()
        viewModel.onSongClicked(song)
    }

    @OptIn(ExperimentalMaterialApi::class)
    fun onSongClosed() = coroutineScope.launch {
        modalBottomSheetState.hide()
        viewModel.onSongClicked(null)
    }

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
            songDetails = viewModel.songDetails.collectAsState(null),
            modalBottomSheetState = rememberModalBottomSheetState(
                initialValue = ModalBottomSheetValue.Hidden,
                confirmStateChange = { it != ModalBottomSheetValue.HalfExpanded },
                skipHalfExpanded = true
            )
        )
    }
}