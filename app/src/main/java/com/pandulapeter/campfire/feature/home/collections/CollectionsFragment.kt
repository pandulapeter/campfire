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
                mainActivity.enableSecondaryNavigationDrawer(R.menu.collections)
                initializeCompoundButton(R.id.saved_only) { viewModel.shouldShowSavedOnly }
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
        defaultToolbar.updateToolbarTitle(R.string.home_collections)
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
            R.id.saved_only -> consumeAndUpdateBoolean(menuItem, { shouldShowSavedOnly = it }, { shouldShowSavedOnly })
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

    private operator fun Menu.get(@IdRes id: Int) = findItem(id)
}