package com.pandulapeter.campfire.feature.main.home.home

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.CompoundButton
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentHomeBinding
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.feature.shared.widget.DisableScrollLinearLayoutManager
import com.pandulapeter.campfire.feature.shared.widget.StateLayout
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.*

class HomeFragment : CampfireFragment<FragmentHomeBinding, HomeViewModel>(R.layout.fragment_home) {

    private var Bundle.placeholderText by BundleArgumentDelegate.Int("placeholderText")
    private var Bundle.buttonText by BundleArgumentDelegate.Int("buttonText")
    private var Bundle.buttonIcon by BundleArgumentDelegate.Int("buttonIcon")
    override val viewModel: HomeViewModel by lazy {
        HomeViewModel(
            onDataLoaded = { languages ->
                getCampfireActivity().enableSecondaryNavigationDrawer(R.menu.home)
                initializeCompoundButton(R.id.show_explicit) { viewModel.shouldShowExplicit }
                getCampfireActivity().secondaryNavigationMenu.findItem(R.id.filter_by_language).subMenu.run {
                    clear()
                    languages.forEachIndexed { index, language ->
                        add(R.id.language_container, language.nameResource, index, language.nameResource).apply {
                            setActionView(R.layout.widget_checkbox)
                            initializeCompoundButton(language.nameResource) { !viewModel.disabledLanguageFilters.contains(language.id) }
                        }
                    }
                }
                getCampfireActivity().updateToolbarButtons(
                    listOf(
                        getCampfireActivity().toolbarContext.createToolbarButton(R.drawable.ic_filter_and_sort_24dp) { getCampfireActivity().openSecondaryNavigationDrawer() }
                    ))
            },
            openSecondaryNavigationDrawer = { getCampfireActivity().openSecondaryNavigationDrawer() }
        )
    }
    private lateinit var linearLayoutManager: DisableScrollLinearLayoutManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        getCampfireActivity().onScreenChanged()
        savedInstanceState?.let {
            viewModel.placeholderText.set(it.placeholderText)
            viewModel.buttonText.set(it.buttonText)
            viewModel.buttonIcon.set(it.buttonIcon)
        }
        viewModel.shouldShowUpdateErrorSnackbar.onEventTriggered(this) {
            showSnackbar(
                message = R.string.collections_update_error,
                action = { viewModel.updateData() })
        }
        viewModel.isLoading.onPropertyChanged(this) {
            if (viewModel.state.get() == StateLayout.State.NORMAL) {
                binding.swipeRefreshLayout.isRefreshing = it
            }
        }
        binding.swipeRefreshLayout.run {
            setOnRefreshListener {
                analyticsManager.onSwipeToRefreshUsed(AnalyticsManager.PARAM_VALUE_SCREEN_COLLECTIONS)
                viewModel.updateData()
            }
            setColorSchemeColors(context.color(R.color.accent))
        }
        linearLayoutManager = DisableScrollLinearLayoutManager(getCampfireActivity())
        binding.recyclerView.layoutManager = linearLayoutManager
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.placeholderText = viewModel.placeholderText.get()
        outState.buttonText = viewModel.buttonText.get()
        outState.buttonIcon = viewModel.buttonIcon.get()
    }

    override fun onResume() {
        super.onResume()
        viewModel.restoreToolbarButtons()
    }

    override fun updateUI() {
        super.updateUI()
        linearLayoutManager.isScrollEnabled = true
    }

    override fun onNavigationItemSelected(menuItem: MenuItem) = viewModel.run {
        when (menuItem.itemId) {
            R.id.show_explicit -> consumeAndUpdateBoolean(menuItem, {
                analyticsManager.onCollectionFilterToggled(AnalyticsManager.PARAM_VALUE_FILTER_SHOW_EXPLICIT, it)
                shouldShowExplicit = it
            }, { shouldShowExplicit })
            else -> consumeAndUpdateLanguageFilter(menuItem, viewModel.languages.find { it.nameResource == menuItem.itemId }?.id ?: "")
        }
    }

    private fun consumeAndUpdateLanguageFilter(menuItem: MenuItem, languageId: String) = consume {
        viewModel.disabledLanguageFilters.run {
            viewModel.disabledLanguageFilters = toMutableSet().apply { if (contains(languageId)) remove(languageId) else add(languageId) }
            analyticsManager.onCollectionFilterToggled(AnalyticsManager.PARAM_VALUE_FILTER_LANGUAGE + languageId, contains(languageId))
            (menuItem.actionView as? CompoundButton).updateCheckedStateWithDelay(contains(languageId))
        }
    }
}