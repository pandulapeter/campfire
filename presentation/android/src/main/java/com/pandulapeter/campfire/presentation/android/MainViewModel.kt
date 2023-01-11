package com.pandulapeter.campfire.presentation.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandulapeter.campfire.domain.api.useCases.GetScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.LoadScreenDataUseCase
import com.pandulapeter.campfire.shared.describe
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class MainViewModel(
    getScreenData: GetScreenDataUseCase,
    private val loadScreenData: LoadScreenDataUseCase
) : ViewModel() {

    val text = getScreenData()
        .map { it.describe() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Uninitialized")

    init {
        viewModelScope.launch { loadScreenData(false) }
    }
}