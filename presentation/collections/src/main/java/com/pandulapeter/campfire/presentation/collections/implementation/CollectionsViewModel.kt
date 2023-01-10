package com.pandulapeter.campfire.presentation.collections.implementation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandulapeter.campfire.data.model.Result
import com.pandulapeter.campfire.domain.useCases.GetCollectionsUseCase
import com.pandulapeter.campfire.domain.useCases.GetDatabasesUseCase
import com.pandulapeter.campfire.domain.useCases.GetSongsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal class CollectionsViewModel(
    private val getCollections: GetCollectionsUseCase,
    private val getDatabases: GetDatabasesUseCase,
    private val getSongs: GetSongsUseCase
) : ViewModel() {

    private val _text = MutableStateFlow("Uninitialized")
    val text: StateFlow<String> = _text

    init {
        viewModelScope.launch {
            val initialText = "Using ${getDatabases().size} databases."
            _text.value = "$initialText\nLoading collections..."
            val collectionsResult = loadCollections()
            _text.value = "$initialText\n$collectionsResult\nLoading songs..."
            val songsResult = loadSongs()
            _text.value = "$initialText\n$collectionsResult\n$songsResult"
        }
    }

    private suspend fun loadCollections(): String = when (val result = getCollections(true)) {
        is Result.Success -> "${result.data.size} collections loaded."
        is Result.Failure -> "Loading collections failed: ${result.exception.message}"
    }

    private suspend fun loadSongs(): String = when (val result = getSongs(true)) {
        is Result.Success -> "${result.data.size} songs loaded."
        is Result.Failure -> "Loading songs failed: ${result.exception.message}"
    }
}