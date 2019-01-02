package com.pandulapeter.campfire.feature.main.songs

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CompoundButton
import androidx.annotation.IdRes
import androidx.databinding.DataBindingUtil
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.ViewSearchControlsBinding
import com.pandulapeter.campfire.feature.main.shared.baseSongList.BaseSongListFragment
import com.pandulapeter.campfire.feature.shared.behavior.TopLevelBehavior
import com.pandulapeter.campfire.feature.shared.widget.ToolbarButton
import com.pandulapeter.campfire.feature.shared.widget.ToolbarTextInputView
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.animatedDrawable
import com.pandulapeter.campfire.util.consume
import com.pandulapeter.campfire.util.drawable
import com.pandulapeter.campfire.util.onTextChanged
import org.koin.androidx.viewmodel.ext.android.viewModel


class SongsFragment : BaseSongListFragment<SongsViewModel>() {
    override val shouldSendMultipleSongs = false
    override val shouldShowManagePlaylist = true
    override val viewModel by viewModel<SongsViewModel>()
    override val topLevelBehavior by lazy {
        TopLevelBehavior(
            inflateToolbarTitle = { toolbarTextInputView },
            getCampfireActivity = { getCampfireActivity() })
    }
    private val toolbarTextInputView by lazy {
        ToolbarTextInputView(
            getCampfireActivity()!!.toolbarContext,
            R.string.songs_search,
            true
        ).apply {
            if (viewModel.isTextInputVisible) {
                showTextInput()
            }
            title.updateToolbarTitle(R.string.main_songs)
            textInput.onTextChanged { if (isTextInputVisible) viewModel.query = it }
            visibilityChangeListener = { viewModel.isTextInputVisible = it }
        }
    }
    private val searchToggle: ToolbarButton by lazy {
        getCampfireActivity()!!.toolbarContext.createToolbarButton(R.drawable.ic_search_24dp) { toggleTextInputVisibility() }
    }
    private val eraseButton: ToolbarButton by lazy {
        getCampfireActivity()!!.toolbarContext.createToolbarButton(R.drawable.ic_eraser_24dp) { toolbarTextInputView.textInput.setText("") }.apply {
            scaleX = 0f
            scaleY = 0f
            alpha = 0.5f
            isEnabled = false
        }
    }
    private val drawableCloseToSearch by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) requireContext().animatedDrawable(R.drawable.avd_close_to_search_24dp) else requireContext().drawable(R.drawable.ic_search_24dp)
    }
    private val drawableSearchToClose by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) requireContext().animatedDrawable(R.drawable.avd_search_to_close_24dp) else requireContext().drawable(R.drawable.ic_close_24dp)
    }
    private val searchControlsBinding by lazy {
        DataBindingUtil.inflate<ViewSearchControlsBinding>(LayoutInflater.from(getCampfireActivity()!!.toolbarContext), R.layout.view_search_controls, null, false).apply {
            viewModel = this@SongsFragment.viewModel.searchControlsViewModel
            setLifecycleOwner(viewLifecycleOwner)
            executePendingBindings()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        analyticsManager.onTopLevelScreenOpened(AnalyticsManager.PARAM_VALUE_SCREEN_SONGS)
        viewModel.shouldUpdateSearchToggleDrawable.observeAndReset {
            searchToggle.setImageDrawable((if (it) drawableSearchToClose else drawableCloseToSearch).apply { (this as? AnimatedVectorDrawableCompat)?.start() })
            getCampfireActivity()?.transitionMode = true
            binding.root.post {
                if (isAdded) {
                    viewModel.searchControlsViewModel.isVisible.value = it
                }
            }
        }
        viewModel.languages.observe { languages ->
            if (languages != null) {
                getCampfireActivity()?.updateAppBarView(searchControlsBinding.root)
                getCampfireActivity()?.enableSecondaryNavigationDrawer(R.menu.songs)
                initializeCompoundButton(R.id.downloaded_only) { viewModel.shouldShowDownloadedOnly }
                initializeCompoundButton(R.id.show_explicit) { viewModel.shouldShowExplicit }
                initializeCompoundButton(R.id.sort_by_title) { viewModel.sortingMode == SongsViewModel.SortingMode.TITLE }
                initializeCompoundButton(R.id.sort_by_artist) { viewModel.sortingMode == SongsViewModel.SortingMode.ARTIST }
                initializeCompoundButton(R.id.sort_by_popularity) { viewModel.sortingMode == SongsViewModel.SortingMode.POPULARITY }
                getCampfireActivity()?.secondaryNavigationMenu?.findItem(R.id.filter_by_language)?.subMenu?.run {
                    clear()
                    languages.forEachIndexed { index, language ->
                        add(R.id.language_container, language.nameResource, index, language.nameResource).apply {
                            setActionView(R.layout.widget_checkbox)
                            initializeCompoundButton(language.nameResource) { !viewModel.disabledLanguageFilters.contains(language.id) }
                        }
                    }
                }
                getCampfireActivity()?.toolbarContext?.let { context ->
                    getCampfireActivity()?.updateToolbarButtons(
                        listOf(
                            eraseButton,
                            searchToggle,
                            context.createToolbarButton(R.drawable.ic_filter_and_sort_24dp) { getCampfireActivity()?.openSecondaryNavigationDrawer() }
                        ))
                }
            }
        }
        viewModel.isFastScrollEnabled.observe { binding.recyclerView.setFastScrollEnabled(it) }
        viewModel.shouldOpenSecondaryNavigationDrawer.observeAndReset { getCampfireActivity()?.openSecondaryNavigationDrawer() }
        viewModel.shouldShowEraseButton.observe { eraseButton.animate().scaleX(if (it) 1f else 0f).scaleY(if (it) 1f else 0f).start() }
        viewModel.shouldEnableEraseButton.observe {
            eraseButton.animate().alpha(if (it) 1f else 0.5f).start()
            eraseButton.isEnabled = it
        }
        savedInstanceState?.let {
            viewModel.searchControlsViewModel.isVisible.value = savedInstanceState.isTextInputVisible
            if (it.isTextInputVisible) {
                searchToggle.setImageDrawable(requireContext().drawable(R.drawable.ic_close_24dp))
                toolbarTextInputView.textInput.run {
                    setText(savedInstanceState.searchQuery)
                    setSelection(text.length)
                    viewModel.query = text.toString()
                }
                toolbarTextInputView.showTextInput()
            }
            viewModel.shouldShowEraseButton.value = savedInstanceState.isEraseButtonVisible
            viewModel.shouldEnableEraseButton.value = savedInstanceState.isEraseButtonEnabled
        }
        toolbarTextInputView.textInput.requestFocus()
        viewModel.searchControlsViewModel.searchInTitles.observe {
            binding.root.postDelayed(
                { if (isAdded) viewModel.shouldSearchInTitles = it },
                COMPOUND_BUTTON_LONG_TRANSITION_DELAY
            )
        }
        viewModel.searchControlsViewModel.searchInArtists.observe {
            binding.root.postDelayed(
                { if (isAdded) viewModel.shouldSearchInArtists = it },
                COMPOUND_BUTTON_LONG_TRANSITION_DELAY
            )
        }
        getCampfireActivity()?.showPlayStoreRatingDialogIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        viewModel.restoreToolbarButtons()
    }

    override fun onSaveInstanceState(outState: Bundle) = outState.run {
        super.onSaveInstanceState(this)
        isTextInputVisible = toolbarTextInputView.isTextInputVisible
        searchQuery = viewModel.query
        isEraseButtonVisible = viewModel.shouldShowEraseButton.value == true
        isEraseButtonEnabled = viewModel.shouldEnableEraseButton.value == true
    }

    override fun onNavigationItemSelected(menuItem: MenuItem) = viewModel.run {
        when (menuItem.itemId) {
            R.id.downloaded_only -> consumeAndUpdateBoolean(menuItem, {
                analyticsManager.onSongsFilterToggled(AnalyticsManager.PARAM_VALUE_FILTER_DOWNLOADED_ONLY, it)
                shouldShowDownloadedOnly = it
            }, { shouldShowDownloadedOnly })
            R.id.show_explicit -> consumeAndUpdateBoolean(menuItem, {
                analyticsManager.onSongsFilterToggled(AnalyticsManager.PARAM_VALUE_FILTER_SHOW_EXPLICIT, it)
                shouldShowExplicit = it
            }, { shouldShowExplicit })
            R.id.sort_by_title -> consumeAndUpdateSortingMode(SongsViewModel.SortingMode.TITLE) {
                analyticsManager.onSongsSortingModeUpdated(AnalyticsManager.PARAM_VALUE_BY_TITLE)
                sortingMode = it
            }
            R.id.sort_by_artist -> consumeAndUpdateSortingMode(SongsViewModel.SortingMode.ARTIST) {
                analyticsManager.onSongsSortingModeUpdated(AnalyticsManager.PARAM_VALUE_BY_ARTIST)
                sortingMode = it
            }
            R.id.sort_by_popularity -> consumeAndUpdateSortingMode(SongsViewModel.SortingMode.POPULARITY) {
                analyticsManager.onSongsSortingModeUpdated(AnalyticsManager.PARAM_VALUE_BY_POPULARITY)
                sortingMode = it
            }
            else -> consumeAndUpdateLanguageFilter(menuItem, viewModel.languages.value.orEmpty().find { it.nameResource == menuItem.itemId }?.id ?: "")
        }
    }

    override fun onBackPressed() = if (toolbarTextInputView.isTextInputVisible) {
        toggleTextInputVisibility()
        true
    } else super.onBackPressed()

    override fun onDetailScreenOpened() {
        if (toolbarTextInputView.isTextInputVisible && viewModel.query.trim().isEmpty()) {
            toggleTextInputVisibility()
        }
    }

    private fun toggleTextInputVisibility() {
        toolbarTextInputView.run {
            if (title.tag == null) {
                val shouldScrollToTop = !viewModel.query.isEmpty()
                animateTextInputVisibility(!isTextInputVisible)
                if (isTextInputVisible) {
                    textInput.setText("")
                }
                viewModel.shouldUpdateSearchToggleDrawable.value = toolbarTextInputView.isTextInputVisible
                if (shouldScrollToTop) {
                    viewModel.updateAdapterItems(!isTextInputVisible)
                }
                viewModel.buttonText.value = if (toolbarTextInputView.isTextInputVisible) 0 else R.string.filters
            }
            viewModel.shouldShowEraseButton.value = isTextInputVisible
        }
    }

    private fun consumeAndUpdateLanguageFilter(menuItem: MenuItem, languageId: String) = consume {
        viewModel.disabledLanguageFilters.run {
            viewModel.disabledLanguageFilters = toMutableSet().apply { if (contains(languageId)) remove(languageId) else add(languageId) }
            analyticsManager.onSongsFilterToggled(AnalyticsManager.PARAM_VALUE_FILTER_LANGUAGE + languageId, contains(languageId))
            (menuItem.actionView as? CompoundButton).updateCheckedStateWithDelay(contains(languageId))
        }
    }

    private inline fun consumeAndUpdateSortingMode(sortingMode: SongsViewModel.SortingMode, crossinline setValue: (SongsViewModel.SortingMode) -> Unit) = consume {
        setValue(sortingMode)
        getCampfireActivity()?.also {
            (it.secondaryNavigationMenu[R.id.sort_by_title].actionView as? CompoundButton).updateCheckedStateWithDelay(sortingMode == SongsViewModel.SortingMode.TITLE)
            (it.secondaryNavigationMenu[R.id.sort_by_artist].actionView as? CompoundButton).updateCheckedStateWithDelay(sortingMode == SongsViewModel.SortingMode.ARTIST)
            (it.secondaryNavigationMenu[R.id.sort_by_popularity].actionView as? CompoundButton)?.updateCheckedStateWithDelay(sortingMode == SongsViewModel.SortingMode.POPULARITY)
        }
    }

    private operator fun Menu.get(@IdRes id: Int) = findItem(id)

    companion object {
        private var Bundle.isTextInputVisible by BundleArgumentDelegate.Boolean("isTextInputVisible")
        private var Bundle.searchQuery by BundleArgumentDelegate.String("searchQuery")
        private var Bundle.isEraseButtonVisible by BundleArgumentDelegate.Boolean("isEraseButtonVisible")
        private var Bundle.isEraseButtonEnabled by BundleArgumentDelegate.Boolean("isEraseButtonEnabled")
        const val COMPOUND_BUTTON_LONG_TRANSITION_DELAY = 300L
    }
}