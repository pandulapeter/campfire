package com.pandulapeter.campfire.presentation.collections.implementation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.domain.models.ScreenData
import com.pandulapeter.campfire.domain.useCases.GetScreenDataUseCase
import com.pandulapeter.campfire.domain.useCases.LoadScreenDataUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class CollectionsViewModel(
    getScreenData: GetScreenDataUseCase,
    private val loadScreenData: LoadScreenDataUseCase
) : ViewModel() {

    val text = getScreenData().map {
        when (it) {
            is DataState.Failure -> "Error - ${it.data.describe()}"
            is DataState.Idle -> "Idle - ${it.data.describe()}"
            is DataState.Loading -> "Loading - ${it.data.describe()}"
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "Uninitialized")

    init {
        viewModelScope.launch { loadScreenData(false) }
    }

    private fun ScreenData?.describe() = this?.let {
        "${collections.size} collections, ${databases.size} databases, ${playlists.size} playlists, ${songs.size} songs"
    } ?: "no data"
}