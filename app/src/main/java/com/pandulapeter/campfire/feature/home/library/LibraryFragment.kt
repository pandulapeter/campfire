package com.pandulapeter.campfire.feature.home.library

import android.content.Context
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.annotation.IdRes
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CompoundButton
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.ViewSearchControlsBinding
import com.pandulapeter.campfire.feature.home.shared.songList.SongListFragment
import com.pandulapeter.campfire.feature.shared.widget.ToolbarButton
import com.pandulapeter.campfire.feature.shared.widget.ToolbarTextInputView
import com.pandulapeter.campfire.util.*


class LibraryFragment : SongListFragment<LibraryViewModel>() {

    companion object {
        private const val COMPOUND_BUTTON_TRANSITION_DELAY = 10L
        private const val COMPOUND_BUTTON_LONG_TRANSITION_DELAY = 300L
    }

    override val viewModel: LibraryViewModel by lazy {
        LibraryViewModel(
            context = mainActivity,
            toolbarTextInputView = ToolbarTextInputView(mainActivity.toolbarContext).apply { title.updateToolbarTitle(R.string.home_library) },
            updateSearchToggleDrawable = {
                searchToggle.setImageDrawable((if (it) drawableSearchToClose else drawableCloseToSearch).apply { this?.start() })
                mainActivity.shouldAllowAppBarScrolling = !it
                mainActivity.toggleTransitionMode(true)
                searchControlsViewModel.isVisible.set(it)
                binding.swipeRefreshLayout.isEnabled = !it
                binding.swipeRefreshLayout.isRefreshing = viewModel.isLoading.get()
            },
            onDataLoaded = { languages ->
                mainActivity.toolbarContext.let { context ->
                    mainActivity.updateToolbarButtons(listOf(
                        searchToggle,
                        context.createToolbarButton(R.drawable.ic_filter_and_sort_24dp) { mainActivity.openSecondaryNavigationDrawer() }
                    ))
                }
                mainActivity.enableSecondaryNavigationDrawer(R.menu.library)
                initializeCompoundButton(R.id.downloaded_only) { viewModel.shouldShowDownloadedOnly }
                initializeCompoundButton(R.id.show_work_in_progress) { viewModel.shouldShowWorkInProgress }
                initializeCompoundButton(R.id.show_explicit) { viewModel.shouldShowExplicit }
                initializeCompoundButton(R.id.sort_by_title) { viewModel.sortingMode == LibraryViewModel.SortingMode.TITLE }
                initializeCompoundButton(R.id.sort_by_artist) { viewModel.sortingMode == LibraryViewModel.SortingMode.ARTIST }
                initializeCompoundButton(R.id.sort_by_popularity) { viewModel.sortingMode == LibraryViewModel.SortingMode.POPULARITY }
                mainActivity.secondaryNavigationMenu.findItem(R.id.filter_by_language).subMenu.run {
                    clear()
                    languages.forEachIndexed { index, language ->
                        add(R.id.language_container, language.nameResource, index, language.nameResource).apply {
                            setActionView(R.layout.widget_checkbox)
                            initializeCompoundButton(language.nameResource) { !viewModel.disabledLanguageFilters.contains(language.id) }
                        }
                    }
                }
            }
        )
    }
    private var Bundle.isTextInputVisible by BundleArgumentDelegate.Boolean("isTextInputVisible")
    private var Bundle.searchQuery by BundleArgumentDelegate.String("searchQuery")
    private var Bundle.placeholderText by BundleArgumentDelegate.Int("placeholderText")
    private var Bundle.buttonText by BundleArgumentDelegate.Int("buttonText")
    private val searchToggle: ToolbarButton by lazy { mainActivity.toolbarContext.createToolbarButton(R.drawable.ic_search_24dp) { viewModel.toggleTextInputVisibility() } }
    private val drawableCloseToSearch by lazy { mainActivity.animatedDrawable(R.drawable.avd_close_to_search_24dp) }
    private val drawableSearchToClose by lazy { mainActivity.animatedDrawable(R.drawable.avd_search_to_close_24dp) }
    private val searchControlsViewModel = SearchControlsViewModel()
    private val searchControlsBinding by lazy {
        DataBindingUtil.inflate<ViewSearchControlsBinding>(LayoutInflater.from(mainActivity.toolbarContext), R.layout.view_search_controls, null, false).apply {
            viewModel = searchControlsViewModel
            executePendingBindings()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        savedInstanceState?.let {
            if (it.isTextInputVisible) {
                searchToggle.setImageDrawable(mainActivity.drawable(R.drawable.ic_close_24dp))
                viewModel.toolbarTextInputView.textInput.run {
                    setText(savedInstanceState.searchQuery)
                    setSelection(text.length)
                    viewModel.query = text.toString()
                }
                viewModel.toolbarTextInputView.showTextInput()
            }
            viewModel.placeholderText.set(savedInstanceState.placeholderText)
            viewModel.buttonText.set(savedInstanceState.buttonText)
            searchControlsViewModel.isVisible.set(savedInstanceState.isTextInputVisible)
        }
        binding.root.postDelayed({
            try {
                mainActivity.addViewToAppBar(searchControlsBinding.root)
                viewModel.toolbarTextInputView.textInput.requestFocus()
            } catch (_: IllegalStateException) {
            }
        }, 100)
        searchControlsViewModel.searchInTitles.onPropertyChanged {
            binding.root.postDelayed(
                { if (isAdded) viewModel.shouldSearchInTitles = it },
                COMPOUND_BUTTON_LONG_TRANSITION_DELAY
            )
        }
        searchControlsViewModel.searchInArtists.onPropertyChanged {
            binding.root.postDelayed(
                { if (isAdded) viewModel.shouldSearchInArtists = it },
                COMPOUND_BUTTON_LONG_TRANSITION_DELAY
            )
        }
    }

    override fun onResume() {
        super.onResume()
        mainActivity.shouldAllowAppBarScrolling = !viewModel.toolbarTextInputView.isTextInputVisible
        viewModel.restoreToolbarButtons()
    }

    override fun onPause() {
        super.onPause()
        toolbarWidth = viewModel.toolbarTextInputView.width
    }

    override fun onSaveInstanceState(outState: Bundle) = outState.run {
        super.onSaveInstanceState(this)
        isTextInputVisible = viewModel.toolbarTextInputView.isTextInputVisible
        searchQuery = viewModel.query
        placeholderText = viewModel.placeholderText.get()
        buttonText = viewModel.buttonText.get()
    }

    override fun onNavigationItemSelected(menuItem: MenuItem) = viewModel.run {
        when (menuItem.itemId) {
            R.id.downloaded_only -> consumeAndUpdateBoolean(menuItem, { shouldShowDownloadedOnly = it }, { shouldShowDownloadedOnly })
            R.id.show_work_in_progress -> consumeAndUpdateBoolean(menuItem, { shouldShowWorkInProgress = it }, { shouldShowWorkInProgress })
            R.id.show_explicit -> consumeAndUpdateBoolean(menuItem, { shouldShowExplicit = it }, { shouldShowExplicit })
            R.id.sort_by_title -> consumeAndUpdateSortingMode(LibraryViewModel.SortingMode.TITLE) { sortingMode = it }
            R.id.sort_by_artist -> consumeAndUpdateSortingMode(LibraryViewModel.SortingMode.ARTIST) { sortingMode = it }
            R.id.sort_by_popularity -> consumeAndUpdateSortingMode(LibraryViewModel.SortingMode.POPULARITY) { sortingMode = it }
            else -> consumeAndUpdateLanguageFilter(menuItem, viewModel.languages.find { it.nameResource == menuItem.itemId }?.id ?: "")
        }
    }

    override fun inflateToolbarTitle(context: Context) = viewModel.toolbarTextInputView

    override fun onBackPressed() = if (viewModel.toolbarTextInputView.isTextInputVisible) {
        viewModel.toggleTextInputVisibility()
        true
    } else super.onBackPressed()

    private inline fun initializeCompoundButton(itemId: Int, crossinline getValue: () -> Boolean) = consume {
        mainActivity.secondaryNavigationMenu.findItem(itemId)?.let {
            (it.actionView as? CompoundButton)?.run {
                isChecked = getValue()
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked != getValue()) {
                        onNavigationItemSelected(it)
                    }
                }
            }
        }
    }

    private fun CompoundButton?.updateCheckedStateWithDelay(checked: Boolean) {
        this?.postDelayed({ if (isAdded) isChecked = checked }, COMPOUND_BUTTON_TRANSITION_DELAY)
    }

    private fun consumeAndUpdateLanguageFilter(menuItem: MenuItem, languageId: String) = consume {
        viewModel.disabledLanguageFilters.run {
            viewModel.disabledLanguageFilters = toMutableSet().apply { if (contains(languageId)) remove(languageId) else add(languageId) }
            (menuItem.actionView as? CompoundButton).updateCheckedStateWithDelay(contains(languageId))
        }
    }

    private inline fun consumeAndUpdateBoolean(menuItem: MenuItem, crossinline setValue: (Boolean) -> Unit, crossinline getValue: () -> Boolean) = consume {
        setValue(!getValue())
        (menuItem.actionView as? CompoundButton).updateCheckedStateWithDelay(getValue())
    }

    private inline fun consumeAndUpdateSortingMode(sortingMode: LibraryViewModel.SortingMode, crossinline setValue: (LibraryViewModel.SortingMode) -> Unit) = consume {
        setValue(sortingMode)
        (mainActivity.secondaryNavigationMenu[R.id.sort_by_title].actionView as? CompoundButton).updateCheckedStateWithDelay(sortingMode == LibraryViewModel.SortingMode.TITLE)
        (mainActivity.secondaryNavigationMenu[R.id.sort_by_artist].actionView as? CompoundButton).updateCheckedStateWithDelay(sortingMode == LibraryViewModel.SortingMode.ARTIST)
        (mainActivity.secondaryNavigationMenu[R.id.sort_by_popularity].actionView as? CompoundButton)?.updateCheckedStateWithDelay(sortingMode == LibraryViewModel.SortingMode.POPULARITY)
    }

    private operator fun Menu.get(@IdRes id: Int) = findItem(id)
}