package com.pandulapeter.campfire.presentation.collections.implementation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandulapeter.campfire.data.model.Result
import com.pandulapeter.campfire.domain.useCases.GetCollectionsUseCase
import com.pandulapeter.campfire.domain.useCases.GetLanguagesUseCase
import com.pandulapeter.campfire.domain.useCases.GetSongsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal class CollectionsViewModel(
    private val getCollections: GetCollectionsUseCase,
    private val getLanguages: GetLanguagesUseCase,
    private val getSongs: GetSongsUseCase
) : ViewModel() {

    private val _text = MutableStateFlow("Uninitialized")
    val text: StateFlow<String> = _text

    init {
        viewModelScope.launch {
            _text.value = "Loading collections..."
            val collectionsResult = loadCollections()
            _text.value = "$collectionsResult\nLoading languages..."
            val languagesResult = loadLanguages()
            _text.value = "$collectionsResult\n$languagesResult\nLoading songs..."
            val songsResult = loadSongs()
            _text.value = "$collectionsResult\n$languagesResult\n$songsResult"
        }
    }

    private suspend fun loadCollections(): String = when (val result = getCollections(true)) {
        is Result.Success -> "${result.data.size} collections loaded."
        is Result.Failure -> "Loading collections failed: ${result.exception.message}"
    }

    private suspend fun loadLanguages(): String = when (val result = getLanguages(true)) {
        is Result.Success -> "${result.data.size} languages loaded."
        is Result.Failure -> "Loading languages failed: ${result.exception.message}"
    }

    private suspend fun loadSongs(): String = when (val result = getSongs(true)) {
        is Result.Success -> "${result.data.size} songs loaded."
        is Result.Failure -> "Loading songs failed: ${result.exception.message}"
    }
}