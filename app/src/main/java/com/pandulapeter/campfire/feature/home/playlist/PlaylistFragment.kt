package com.pandulapeter.campfire.feature.home.playlist

import android.content.Context
import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.feature.home.shared.ElevationItemTouchHelperCallback
import com.pandulapeter.campfire.feature.home.shared.songList.SongListFragment
import com.pandulapeter.campfire.feature.home.shared.songList.SongListItemViewModel
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
            if (viewModel.playlist.get()?.id != Playlist.FAVORITES_ID) {
                mainActivity.shouldAllowAppBarScrolling = !it
            }
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
        val itemTouchHelper = ItemTouchHelper(object : ElevationItemTouchHelperCallback((context?.dimension(R.dimen.content_padding) ?: 0).toFloat()) {

            override fun getMovementFlags(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?) =
                if (viewModel.isInEditMode.get())
                    makeMovementFlags(
                        if (viewModel.adapter.items.size > 1) ItemTouchHelper.UP or ItemTouchHelper.DOWN else 0,
                        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                    ) else 0

            override fun onMove(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?, target: RecyclerView.ViewHolder?) =
                consume {
                    viewHolder?.adapterPosition?.let { originalPosition ->
                        target?.adapterPosition?.let { targetPosition ->
                            hideSnackbar()
//                                firstTimeUserExperienceManager.managePlaylistsDragCompleted = true
                            viewModel.swapSongsInPlaylist(originalPosition, targetPosition)
                        }
                    }
                }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder?, direction: Int) {
                viewHolder?.adapterPosition?.let { position ->
                    if (position != RecyclerView.NO_POSITION) {
//                        firstTimeUserExperienceManager.managePlaylistsSwipeCompleted = true
                        (viewModel.adapter.items[position] as? SongListItemViewModel.SongViewModel)?.song?.let { song ->
                            showSnackbar(
                                message = getString(R.string.playlist_song_removed_message, song.title),
                                actionText = R.string.undo,
                                action = { viewModel.cancelDeleteSong() },
                                dismissAction = { viewModel.deleteSongPermanently() }
                            )
                            binding.root.post { viewModel.deleteSongTemporarily(song.id) }
                        }
                    }
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
        viewModel.adapter.dragHandleTouchListener = { position -> itemTouchHelper.startDrag(binding.recyclerView.findViewHolderForAdapterPosition(position)) }
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