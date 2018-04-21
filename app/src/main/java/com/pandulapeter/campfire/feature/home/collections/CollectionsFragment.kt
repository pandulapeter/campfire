package com.pandulapeter.campfire.feature.home.collections

import android.os.Bundle
import android.support.annotation.IdRes
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CompoundButton
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.home.shared.songList.SongListFragment
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.consume


class CollectionsFragment : SongListFragment<CollectionsViewModel>() {

    companion object {
        private const val COMPOUND_BUTTON_TRANSITION_DELAY = 10L
    }

    override val viewModel: CollectionsViewModel by lazy {
        CollectionsViewModel(
            context = mainActivity,
            onDataLoaded = { languages ->
                mainActivity.enableSecondaryNavigationDrawer(R.menu.library)
                initializeCompoundButton(R.id.downloaded_only) { viewModel.shouldShowDownloadedOnly }
                initializeCompoundButton(R.id.show_explicit) { viewModel.shouldShowExplicit }
                initializeCompoundButton(R.id.sort_by_title) { viewModel.sortingMode == CollectionsViewModel.SortingMode.TITLE }
                initializeCompoundButton(R.id.sort_by_artist) { viewModel.sortingMode == CollectionsViewModel.SortingMode.ARTIST }
                initializeCompoundButton(R.id.sort_by_popularity) { viewModel.sortingMode == CollectionsViewModel.SortingMode.POPULARITY }
                mainActivity.secondaryNavigationMenu.findItem(R.id.filter_by_language).subMenu.run {
                    clear()
                    languages.forEachIndexed { index, language ->
                        add(R.id.language_container, language.nameResource, index, language.nameResource).apply {
                            setActionView(R.layout.widget_checkbox)
                            initializeCompoundButton(language.nameResource) { !viewModel.disabledLanguageFilters.contains(language.id) }
                        }
                    }
                }
                mainActivity.updateToolbarButtons(listOf(
                    mainActivity.toolbarContext.createToolbarButton(R.drawable.ic_filter_and_sort_24dp) { mainActivity.openSecondaryNavigationDrawer() }
                ))
            }
        )
    }
    private var Bundle.placeholderText by BundleArgumentDelegate.Int("placeholderText")
    private var Bundle.buttonText by BundleArgumentDelegate.Int("buttonText")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        savedInstanceState?.let {
            viewModel.placeholderText.set(savedInstanceState.placeholderText)
            viewModel.buttonText.set(savedInstanceState.buttonText)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.restoreToolbarButtons()
    }

    override fun onSaveInstanceState(outState: Bundle) = outState.run {
        super.onSaveInstanceState(this)
        placeholderText = viewModel.placeholderText.get()
        buttonText = viewModel.buttonText.get()
    }

    override fun onNavigationItemSelected(menuItem: MenuItem) = viewModel.run {
        when (menuItem.itemId) {
            R.id.downloaded_only -> consumeAndUpdateBoolean(menuItem, { shouldShowDownloadedOnly = it }, { shouldShowDownloadedOnly })
            R.id.show_explicit -> consumeAndUpdateBoolean(menuItem, { shouldShowExplicit = it }, { shouldShowExplicit })
            R.id.sort_by_title -> consumeAndUpdateSortingMode(CollectionsViewModel.SortingMode.TITLE) { sortingMode = it }
            R.id.sort_by_artist -> consumeAndUpdateSortingMode(CollectionsViewModel.SortingMode.ARTIST) { sortingMode = it }
            R.id.sort_by_popularity -> consumeAndUpdateSortingMode(CollectionsViewModel.SortingMode.POPULARITY) { sortingMode = it }
            else -> consumeAndUpdateLanguageFilter(menuItem, viewModel.languages.find { it.nameResource == menuItem.itemId }?.id ?: "")
        }
    }

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

    private inline fun consumeAndUpdateSortingMode(sortingMode: CollectionsViewModel.SortingMode, crossinline setValue: (CollectionsViewModel.SortingMode) -> Unit) = consume {
        setValue(sortingMode)
        (mainActivity.secondaryNavigationMenu[R.id.sort_by_title].actionView as? CompoundButton).updateCheckedStateWithDelay(sortingMode == CollectionsViewModel.SortingMode.TITLE)
        (mainActivity.secondaryNavigationMenu[R.id.sort_by_artist].actionView as? CompoundButton).updateCheckedStateWithDelay(sortingMode == CollectionsViewModel.SortingMode.ARTIST)
        (mainActivity.secondaryNavigationMenu[R.id.sort_by_popularity].actionView as? CompoundButton)?.updateCheckedStateWithDelay(sortingMode == CollectionsViewModel.SortingMode.POPULARITY)
    }

    private operator fun Menu.get(@IdRes id: Int) = findItem(id)
}