package com.pandulapeter.campfire.feature.home.library

import android.content.Context
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.Song
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.databinding.FragmentLibraryBinding
import com.pandulapeter.campfire.feature.CampfireFragment
import com.pandulapeter.campfire.feature.home.shared.SongAdapter
import com.pandulapeter.campfire.feature.home.shared.SongViewModel
import com.pandulapeter.campfire.feature.shared.widget.ToolbarTextInputView
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.old.feature.home.shared.SpacesItemDecoration
import com.pandulapeter.campfire.util.*
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import org.koin.android.ext.android.inject

class LibraryFragment : CampfireFragment<FragmentLibraryBinding>(R.layout.fragment_library), SongRepository.Subscriber {

    private var Bundle.isTextInputVisible by BundleArgumentDelegate.Boolean("isTextInputVisible")
    private var Bundle.searchQuery by BundleArgumentDelegate.String("searchQuery")
    private val toolbarTextInputView by lazy { ToolbarTextInputView(mainActivity.toolbarContext).apply { title.updateToolbarTitle(R.string.home_library) } }
    private val searchToggle by lazy { mainActivity.toolbarContext.createToolbarButton(R.drawable.ic_search_24dp) { toggleTextInputVisibility() } }
    private val drawableCloseToSearch by lazy { context.animatedDrawable(R.drawable.avd_close_to_search_24dp) }
    private val drawableSearchToClose by lazy { context.animatedDrawable(R.drawable.avd_search_to_close_24dp) }
    private val appShortcutManager by inject<AppShortcutManager>()
    private val songRepository by inject<SongRepository>()
    private val adapter = SongAdapter()
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
        binding.swipeRefreshLayout.setColorSchemeColors(context.color(R.color.accent))
        binding.swipeRefreshLayout.setOnRefreshListener { songRepository.updateData() }
        binding.recyclerView.run {
            layoutManager = LinearLayoutManager(context)
            adapter = this@LibraryFragment.adapter
            setHasFixedSize(true)
            addItemDecoration(SpacesItemDecoration(context.dimension(R.dimen.content_padding)))
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                    if (dy > 0) {
                        hideKeyboard(activity?.currentFocus)
                    }
                }
            })
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.isTextInputVisible = toolbarTextInputView.isTextInputVisible
        outState?.searchQuery = toolbarTextInputView.textInput.text.toString()
    }

    override fun onStart() {
        super.onStart()
        songRepository.subscribe(this)
    }

    override fun onStop() {
        super.onStop()
        songRepository.unsubscribe(this)
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

    override fun onSongRepositoryDataUpdated(data: List<Song>) {
        async(UI) {
            adapter.items = async(CommonPool) {
                data.map { SongViewModel(it)}
            }.await()
        }
    }

    override fun onSongRepositoryLoadingStateChanged(isLoading: Boolean) {
        binding.swipeRefreshLayout.isRefreshing = isLoading
    }

    override fun onSongRepositoryUpdateError() = showSnackbar(R.string.library_update_error, View.OnClickListener { songRepository.updateData() })

    private fun toggleTextInputVisibility() {
        toolbarTextInputView.run {
            if (title.tag == null) {
                if (!isTextInputVisible) {
                    textInput.setText("")
                }
                searchToggle.setImageDrawable((if (toolbarTextInputView.isTextInputVisible) drawableCloseToSearch else drawableSearchToClose).apply { this?.start() })
                isTextInputVisible = !isTextInputVisible
            }
        }
    }
}