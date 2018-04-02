package com.pandulapeter.campfire.feature.home.library

import android.content.Context
import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.Song
import com.pandulapeter.campfire.feature.home.shared.SongListFragment
import com.pandulapeter.campfire.feature.home.shared.SongViewModel
import com.pandulapeter.campfire.feature.shared.widget.ToolbarTextInputView
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.util.*
import org.koin.android.ext.android.inject

class LibraryFragment : SongListFragment() {

    private var Bundle.isTextInputVisible by BundleArgumentDelegate.Boolean("isTextInputVisible")
    private var Bundle.searchQuery by BundleArgumentDelegate.String("searchQuery")
    private var query = ""
        set(value) {
            if (field != value) {
                field = value
                updateAdapterItems(true)
            }
        }
    private val toolbarTextInputView by lazy {
        ToolbarTextInputView(mainActivity.toolbarContext).apply {
            title.updateToolbarTitle(R.string.home_library)
            textInput.onTextChanged { if (isTextInputVisible) query = it }
        }
    }
    private val searchToggle by lazy { mainActivity.toolbarContext.createToolbarButton(R.drawable.ic_search_24dp) { toggleTextInputVisibility() } }
    private val drawableCloseToSearch by lazy { context.animatedDrawable(R.drawable.avd_close_to_search_24dp) }
    private val drawableSearchToClose by lazy { context.animatedDrawable(R.drawable.avd_search_to_close_24dp) }
    private val appShortcutManager by inject<AppShortcutManager>()
    override val navigationMenu = R.menu.library

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        savedInstanceState?.let {
            if (it.isTextInputVisible) {
                searchToggle.setImageDrawable(context.drawable(R.drawable.ic_close_24dp))
                toolbarTextInputView.textInput.run {
                    setText(savedInstanceState.searchQuery)
                    setSelection(text.length)
                }
                toolbarTextInputView.showTextInput()
            }
        } ?: appShortcutManager.onLibraryOpened()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.isTextInputVisible = toolbarTextInputView.isTextInputVisible
        outState?.searchQuery = query
    }

    override fun inflateToolbarTitle(context: Context) = toolbarTextInputView

    override fun inflateToolbarButtons(context: Context) = listOf<View>(
        searchToggle,
        context.createToolbarButton(R.drawable.ic_view_options_24dp) { mainActivity.openSecondaryNavigationDrawer() }
    )

    override fun onBackPressed() = if (toolbarTextInputView.isTextInputVisible) {
        toggleTextInputVisibility()
        true
    } else super.onBackPressed()

    override fun Sequence<Song>.createViewModels() = filterByQuery()
        .map { SongViewModel(it) }
        .toList()

    private fun toggleTextInputVisibility() {
        toolbarTextInputView.run {
            if (title.tag == null) {
                val shouldScrollToTop = !query.isEmpty()
                animateTextInputVisibility(!isTextInputVisible)
                if (isTextInputVisible) {
                    textInput.setText("")
                }
                searchToggle.setImageDrawable((if (toolbarTextInputView.isTextInputVisible) drawableSearchToClose else drawableCloseToSearch).apply { this?.start() })
                updateAdapterItems(!isTextInputVisible && shouldScrollToTop)
            }
        }
    }

    //TODO: Prioritize results that begin with the searchQuery.
    private fun Sequence<Song>.filterByQuery() = if (toolbarTextInputView.isTextInputVisible) {
        query.trim().normalize().let { query ->
            filter { it.getNormalizedTitle().contains(query, true) || it.getNormalizedArtist().contains(query, true) }
        }
    } else this

}