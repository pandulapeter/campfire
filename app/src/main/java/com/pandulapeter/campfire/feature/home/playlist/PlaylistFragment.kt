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
import com.pandulapeter.campfire.integration.FirstTimeUserExperienceManager
import com.pandulapeter.campfire.util.*
import org.koin.android.ext.android.inject

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
                    mainActivity.updateToolbarButtons(listOf(editToggle, shuffleButton))
                }
            }
        )
    }
    private var Bundle.isInEditMode by BundleArgumentDelegate.Boolean("isInEditMode")
    private val editToggle: ToolbarButton by lazy { mainActivity.toolbarContext.createToolbarButton(R.drawable.ic_edit_24dp) { viewModel.toggleEditMode() } }
    private val shuffleButton: ToolbarButton by lazy {
        mainActivity.toolbarContext.createToolbarButton(R.drawable.ic_shuffle_24dp) { shuffleSongs() }.apply { visibleOrGone = false }
    }
    private val drawableEditToDone by lazy { mainActivity.animatedDrawable(R.drawable.avd_edit_to_done_24dp) }
    private val drawableDoneToEdit by lazy { mainActivity.animatedDrawable(R.drawable.avd_done_to_edit_24dp) }
    private val firstTimeUserExperienceManager by inject<FirstTimeUserExperienceManager>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.swipeRefreshLayout.isEnabled = false
        viewModel.songCount.onPropertyChanged(this) {
            updateToolbarTitle(it)
            shuffleButton.visibleOrGone = it > 1
        }
        viewModel.playlist.onPropertyChanged(this) { updateToolbarTitle() }
        viewModel.isInEditMode.onPropertyChanged(this) {
            editToggle.setImageDrawable((if (it) drawableEditToDone else drawableDoneToEdit)?.apply { start() })
            mainActivity.shouldAllowAppBarScrolling = !it
            if (it && !firstTimeUserExperienceManager.playlistSwipeCompleted) {
                if (firstTimeUserExperienceManager.playlistDragCompleted) {
                    showSwipeHintIfNeeded()
                } else {
                    if (viewModel.adapter.items.size > 1) {
                        showHint(
                            message = R.string.playlist_hint_drag,
                            action = {
                                firstTimeUserExperienceManager.playlistDragCompleted = true
                                showSwipeHintIfNeeded()
                            }
                        )
                    }
                }
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
                            if (viewModel.hasSongToDelete() || !firstTimeUserExperienceManager.playlistDragCompleted) {
                                hideSnackbar()
                            }
                            firstTimeUserExperienceManager.playlistDragCompleted = true
                            viewModel.swapSongsInPlaylist(originalPosition, targetPosition)
                            showSwipeHintIfNeeded()
                        }
                    }
                }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder?, direction: Int) {
                viewHolder?.adapterPosition?.let { position ->
                    if (position != RecyclerView.NO_POSITION) {
                        firstTimeUserExperienceManager.playlistSwipeCompleted = true
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

    override fun onResume() {
        super.onResume()
        viewModel.restoreToolbarButtons()
    }

    override fun onPause() {
        super.onPause()
        viewModel.toolbarTextInputView?.let {
            toolbarWidth = it.width
        }
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

    private fun showSwipeHintIfNeeded() {
        if (viewModel.adapter.items.isNotEmpty() && !isSnackbarVisible() && !firstTimeUserExperienceManager.playlistSwipeCompleted) {
            showHint(
                message = R.string.playlist_hint_swipe,
                action = { firstTimeUserExperienceManager.playlistSwipeCompleted = true }
            )
        }
    }

    private fun shuffleSongs() {
        //TODO: Scroll before the animation to make sure the shared element is on the screen.
        val tempList = viewModel.adapter.items.filterIsInstance<SongListItemViewModel.SongViewModel>().map { it.song }.toMutableList()
        tempList.shuffle()
        val index = 0
        val originalIndex = viewModel.adapter.items.indexOfFirst { it is SongListItemViewModel.SongViewModel && it.song.id == tempList[index].id }
        if (originalIndex != RecyclerView.NO_POSITION) {
            mainActivity.openDetailScreen(
                binding.recyclerView.findViewHolderForAdapterPosition(originalIndex).itemView,
                tempList,
                tempList.size > 1,
                0,
                false
            )
        }
    }
}