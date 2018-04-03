package com.pandulapeter.campfire.feature.home.library

import android.content.Context
import android.os.Bundle
import android.support.annotation.IdRes
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CompoundButton
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.home.shared.SongListFragment
import com.pandulapeter.campfire.feature.shared.widget.ToolbarButton
import com.pandulapeter.campfire.feature.shared.widget.ToolbarTextInputView
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.animatedDrawable
import com.pandulapeter.campfire.util.consume
import com.pandulapeter.campfire.util.drawable

class LibraryFragment : SongListFragment<LibraryViewModel>() {

    override val viewModel by lazy {
        LibraryViewModel(
            toolbarTextInputView = ToolbarTextInputView(mainActivity.toolbarContext).apply { title.updateToolbarTitle(R.string.home_library) },
            updateSearchToggleDrawable = { searchToggle.setImageDrawable((if (it) drawableSearchToClose else drawableCloseToSearch).apply { this?.start() }) },
            onDataLoaded = {
                mainActivity.enableSecondaryNavigationDrawer(R.menu.library)
                mainActivity.toolbarContext.let { context ->
                    mainActivity.updateToolbarButtons(listOf(
                        searchToggle,
                        context.createToolbarButton(R.drawable.ic_view_options_24dp) { mainActivity.openSecondaryNavigationDrawer() }
                    ))
                }
            }
        )
    }
    private var Bundle.isTextInputVisible by BundleArgumentDelegate.Boolean("isTextInputVisible")
    private var Bundle.searchQuery by BundleArgumentDelegate.String("searchQuery")
    private val searchToggle: ToolbarButton by lazy { mainActivity.toolbarContext.createToolbarButton(R.drawable.ic_search_24dp) { viewModel.toggleTextInputVisibility() } }
    private val drawableCloseToSearch by lazy { context.animatedDrawable(R.drawable.avd_close_to_search_24dp) }
    private val drawableSearchToClose by lazy { context.animatedDrawable(R.drawable.avd_search_to_close_24dp) }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        savedInstanceState?.let {
            if (it.isTextInputVisible) {
                searchToggle.setImageDrawable(context.drawable(R.drawable.ic_close_24dp))
                viewModel.toolbarTextInputView.textInput.run {
                    setText(savedInstanceState.searchQuery)
                    setSelection(text.length)
                }
                viewModel.toolbarTextInputView.showTextInput()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.isTextInputVisible = viewModel.toolbarTextInputView.isTextInputVisible
        outState?.searchQuery = viewModel.query
    }

    //TODO: Set up checkedChangeListeners for the CompoundButtons.
    override fun onNavigationItemSelected(menuItem: MenuItem) = viewModel.run {
        when (menuItem.itemId) {
            R.id.downloaded_only -> consumeAndUpdateBoolean(menuItem, { shouldShowDownloadedOnly = it }, { shouldShowDownloadedOnly })
            R.id.show_work_in_progress -> consumeAndUpdateBoolean(menuItem, { shouldShowWorkInProgress = it }, { shouldShowWorkInProgress })
            R.id.show_explicit -> consumeAndUpdateBoolean(menuItem, { shouldShowExplicit = it }, { shouldShowExplicit })
            R.id.sort_by_title -> consumeAndUpdateSortingMode(LibraryViewModel.SortingMode.TITLE) { sortingMode = it }
            R.id.sort_by_artist -> consumeAndUpdateSortingMode(LibraryViewModel.SortingMode.ARTIST) { sortingMode = it }
            R.id.sort_by_popularity -> consumeAndUpdateSortingMode(LibraryViewModel.SortingMode.POPULARITY) { sortingMode = it }
            else -> consume { showSnackbar(R.string.work_in_progress) }//viewModel.languageFilters.get()?.filterKeys { language -> language.nameResource == it.itemId }?.values?.first()?.toggle()
        }
    }

    private inline fun consumeAndUpdateBoolean(menuItem: MenuItem, crossinline setValue: (Boolean) -> Unit, crossinline getValue: () -> Boolean) = consume {
        setValue(!getValue())
        (menuItem.actionView as? CompoundButton)?.isChecked = getValue()
    }

    private operator fun Menu.get(@IdRes id: Int) = findItem(id)

    private inline fun consumeAndUpdateSortingMode(sortingMode: LibraryViewModel.SortingMode, crossinline setValue: (LibraryViewModel.SortingMode) -> Unit) = consume {
        setValue(sortingMode)
        (mainActivity.secondaryNavigationMenu[R.id.sort_by_title].actionView as? CompoundButton)?.isChecked = sortingMode == LibraryViewModel.SortingMode.TITLE
        (mainActivity.secondaryNavigationMenu[R.id.sort_by_artist].actionView as? CompoundButton)?.isChecked = sortingMode == LibraryViewModel.SortingMode.ARTIST
        (mainActivity.secondaryNavigationMenu[R.id.sort_by_popularity].actionView as? CompoundButton)?.isChecked = sortingMode == LibraryViewModel.SortingMode.POPULARITY
    }

    override fun inflateToolbarTitle(context: Context) = viewModel.toolbarTextInputView

    override fun onBackPressed() = if (viewModel.toolbarTextInputView.isTextInputVisible) {
        viewModel.toggleTextInputVisibility()
        true
    } else super.onBackPressed()
}