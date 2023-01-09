package com.pandulapeter.campfire.presentation.collections.implementation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandulapeter.campfire.data.model.Result
import com.pandulapeter.campfire.domain.useCases.GetCollectionsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal class CollectionsViewModel(
    private val getCollections: GetCollectionsUseCase
) : ViewModel() {

    private val _text = MutableStateFlow("Uninitialized")
    val text: StateFlow<String> = _text

    init {
        viewModelScope.launch {
            when (val result = getCollections(true)) {
                is Result.Success -> _text.value = "Loading successful, downloaded ${result.data.size} collections."
                is Result.Failure -> _text.value = "Loading failed: ${result.exception.message}"
            }
        }
    }
}