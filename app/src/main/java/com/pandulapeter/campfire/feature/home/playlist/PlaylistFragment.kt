package com.pandulapeter.campfire.feature.home.playlist

import android.content.Context
import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.feature.home.shared.songList.SongListFragment
import com.pandulapeter.campfire.feature.shared.widget.ToolbarButton
import com.pandulapeter.campfire.feature.shared.widget.ToolbarTextInputView
import com.pandulapeter.campfire.util.*

class PlaylistFragment : SongListFragment<PlaylistViewModel>() {

    companion object {
        private var Bundle?.playlistId by BundleArgumentDelegate.String("playlistId")

        fun newInstance(playlistId: String) = PlaylistFragment().withArguments { it.playlistId = playlistId }
    }

    override val viewModel by lazy {
        PlaylistViewModel(
            context = mainActivity,
            playlistId = arguments.playlistId,
            openLibrary = { mainActivity.openLibraryScreen() },
            toolbarTextInputView = if (arguments?.playlistId == Playlist.FAVORITES_ID) null else ToolbarTextInputView(mainActivity.toolbarContext),
            onDataLoaded = {
                if (it) {
                    mainActivity.updateToolbarButtons(listOf(editToggle))
                }
            }
        )
    }
    private var Bundle.isInEditMode by BundleArgumentDelegate.Boolean("isInEditMode")
    private val editToggle: ToolbarButton by lazy { mainActivity.toolbarContext.createToolbarButton(R.drawable.ic_edit_24dp) { viewModel.toggleEditMode() } }
    private val drawableEditToDone by lazy { mainActivity.animatedDrawable(R.drawable.avd_edit_to_done_24dp) }
    private val drawableDoneToEdit by lazy { mainActivity.animatedDrawable(R.drawable.avd_done_to_edit_24dp) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.swipeRefreshLayout.isEnabled = false
        viewModel.songCount.onPropertyChanged(this) { updateToolbarTitle(it) }
        viewModel.playlist.onPropertyChanged(this) { updateToolbarTitle() }
        viewModel.isInEditMode.onPropertyChanged {
            editToggle.setImageDrawable((if (it) drawableEditToDone else drawableDoneToEdit)?.apply { start() })
            mainActivity.shouldAllowAppBarScrolling = !it
            mainActivity.transitionMode = true
        }
        savedInstanceState?.let {
            if (it.isInEditMode) {
                viewModel.isInEditMode.set(true)
                editToggle.setImageDrawable(mainActivity.drawable(R.drawable.ic_done_24dp))
                viewModel.toolbarTextInputView?.textInput?.run {
                    setText(viewModel.playlist.get()?.title)
                    setSelection(text.length)
                }
                viewModel.toolbarTextInputView?.showTextInput()
            }
        }
        viewModel.toolbarTextInputView?.textInput?.requestFocus()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.isInEditMode = viewModel.isInEditMode.get()
    }

    override fun onBackPressed() = if (viewModel.toolbarTextInputView?.isTextInputVisible == true) {
        viewModel.toggleEditMode()
        true
    } else super.onBackPressed()

    override fun inflateToolbarTitle(context: Context) = viewModel.toolbarTextInputView ?: defaultToolbar

    private fun updateToolbarTitle(songCount: Int = viewModel.songCount.get()) = (viewModel.toolbarTextInputView?.title ?: defaultToolbar).updateToolbarTitle(
        viewModel.playlist.get()?.title ?: getString(R.string.home_favorites),
        if (songCount == 0) getString(R.string.manage_playlists_song_count_empty) else mainActivity.resources.getQuantityString(R.plurals.playlist_song_count, songCount, songCount)
    )
}