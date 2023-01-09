package com.pandulapeter.campfire.presentation.collections.implementation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandulapeter.campfire.data.model.Result
import com.pandulapeter.campfire.domain.useCases.GetCollectionsUseCase
import com.pandulapeter.campfire.domain.useCases.GetSongsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal class CollectionsViewModel(
    private val getCollections: GetCollectionsUseCase,
    private val getSongs: GetSongsUseCase
) : ViewModel() {

    private val _text = MutableStateFlow("Uninitialized")
    val text: StateFlow<String> = _text

    init {
        viewModelScope.launch {
            _text.value = "Loading collections..."
            when (val collectionsResult = getCollections(true)) {
                is Result.Success -> {
                    val collectionsResultString = "${collectionsResult.data.size} collections loaded."
                    _text.value = "$collectionsResultString\nLoading songs..."
                    when (val songsResult = getSongs(true)) {
                        is Result.Success -> {
                            val songsResultString = "${songsResult.data.size} songs loaded."
                            _text.value = "$collectionsResultString\n$songsResultString"
                        }
                        is Result.Failure -> _text.value = "$collectionsResultString\nLoading songs failed: ${songsResult.exception.message}"
                    }
                }
                is Result.Failure -> _text.value = "Loading collections failed: ${collectionsResult.exception.message}"
            }
        }
    }
}