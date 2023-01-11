package com.pandulapeter.campfire.presentation.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.domain.api.models.ScreenData
import com.pandulapeter.campfire.domain.api.useCases.GetScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.LoadScreenDataUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class MainViewModel(
    getScreenData: GetScreenDataUseCase,
    private val loadScreenData: LoadScreenDataUseCase
) : ViewModel() {

    val text = getScreenData().map {
        when (it) {
            is DataState.Failure -> "Error\n${it.data.describe()}"
            is DataState.Idle -> "Idle\n${it.data.describe()}"
            is DataState.Loading -> "Loading\n${it.data.describe()}"
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "Uninitialized")

    init {
        viewModelScope.launch { loadScreenData(false) }
    }

    private fun ScreenData?.describe() = this?.let {
        "${collections.size} collections, ${databases.size} databases, ${playlists.size} playlists, ${songs.size} songs"
    } ?: "no data"
}