package com.pandulapeter.campfire.feature.home.library

import com.pandulapeter.campfire.data.model.remote.Song
import com.pandulapeter.campfire.feature.home.shared.SongListViewModel
import com.pandulapeter.campfire.feature.home.shared.SongViewModel
import com.pandulapeter.campfire.feature.shared.widget.ToolbarTextInputView
import com.pandulapeter.campfire.util.normalize
import com.pandulapeter.campfire.util.onTextChanged

class LibraryViewModel(
    val toolbarTextInputView: ToolbarTextInputView,
    private val updateSearchToggleDrawable: (Boolean) -> Unit
) : SongListViewModel() {

    var query = ""
        set(value) {
            if (field != value) {
                field = value
                updateAdapterItems(true)
            }
        }

    init {
        toolbarTextInputView.apply {
            textInput.onTextChanged { if (isTextInputVisible) query = it }
        }
    }

    override fun Sequence<Song>.createViewModels() = filterByQuery()
        .map { SongViewModel(it) }
        .toList()

    //TODO: Prioritize results that begin with the searchQuery.
    private fun Sequence<Song>.filterByQuery() = if (toolbarTextInputView.isTextInputVisible) {
        query.trim().normalize().let { query ->
            filter { it.getNormalizedTitle().contains(query, true) || it.getNormalizedArtist().contains(query, true) }
        }
    } else this

    fun toggleTextInputVisibility() {
        toolbarTextInputView.run {
            if (title.tag == null) {
                val shouldScrollToTop = !query.isEmpty()
                animateTextInputVisibility(!isTextInputVisible)
                if (isTextInputVisible) {
                    textInput.setText("")
                }
                updateSearchToggleDrawable(toolbarTextInputView.isTextInputVisible)
                updateAdapterItems(!isTextInputVisible && shouldScrollToTop)
            }
        }
    }
}