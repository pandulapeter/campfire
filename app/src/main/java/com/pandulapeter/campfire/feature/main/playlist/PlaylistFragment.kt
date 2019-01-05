package com.pandulapeter.campfire.feature.main.playlist

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.local.Playlist
import com.pandulapeter.campfire.feature.main.shared.ElevationItemTouchHelperCallback
import com.pandulapeter.campfire.feature.main.shared.baseSongList.BaseSongListFragment
import com.pandulapeter.campfire.feature.main.shared.recycler.viewModel.SongItemViewModel
import com.pandulapeter.campfire.feature.shared.behavior.TopLevelBehavior
import com.pandulapeter.campfire.feature.shared.widget.StateLayout
import com.pandulapeter.campfire.feature.shared.widget.ToolbarButton
import com.pandulapeter.campfire.feature.shared.widget.ToolbarTextInputView
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.integration.FirstTimeUserExperienceManager
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.animatedDrawable
import com.pandulapeter.campfire.util.consume
import com.pandulapeter.campfire.util.dimension
import com.pandulapeter.campfire.util.drawable
import com.pandulapeter.campfire.util.visibleOrGone
import com.pandulapeter.campfire.util.withArguments
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class PlaylistFragment : BaseSongListFragment<PlaylistViewModel>() {

    override val shouldSendMultipleSongs = true
    override val shouldShowManagePlaylist = false
    override val viewModel by viewModel<PlaylistViewModel> { parametersOf(arguments?.playlistId ?: throw IllegalArgumentException("No Playlist specified.")) }
    override val topLevelBehavior: TopLevelBehavior by lazy {
        TopLevelBehavior(
            inflateToolbarTitle = { toolbarTextInputView ?: topLevelBehavior.defaultToolbar },
            getCampfireActivity = { getCampfireActivity() })
    }
    private val editToggle: ToolbarButton by lazy {
        getCampfireActivity()!!.toolbarContext.createToolbarButton(R.drawable.ic_edit) { toggleEditMode() }.apply {
            visibleOrGone = false
        }
    }
    private val shareButton: ToolbarButton by lazy {
        getCampfireActivity()!!.toolbarContext.createToolbarButton(R.drawable.ic_share) {
            analyticsManager.onShareButtonPressed(AnalyticsManager.PARAM_VALUE_SCREEN_PLAYLIST, viewModel.songCount.value ?: 0)
        }.apply { visibleOrGone = false }
    }
    private val shuffleButton: ToolbarButton by lazy {
        getCampfireActivity()!!.toolbarContext.createToolbarButton(R.drawable.ic_shuffle) { shuffleSongs(AnalyticsManager.PARAM_VALUE_SCREEN_PLAYLIST) }.apply {
            visibleOrGone = false
        }
    }
    private val toolbarTextInputView by lazy {
        if (arguments?.playlistId == Playlist.FAVORITES_ID) null else ToolbarTextInputView(
            getCampfireActivity()!!.toolbarContext,
            R.string.playlist_title,
            false
        ).apply {
            if (viewModel.isInEditMode.value == true) {
                showTextInput()
            }
            onDoneButtonPressed = {
                if (viewModel.isInEditMode.value == true) {
                    toggleEditMode()
                }
            }
        }
    }
    private val drawableEditToDone by lazy { requireContext().animatedDrawable(R.drawable.avd_edit_to_done) }
    private val drawableDoneToEdit by lazy { requireContext().animatedDrawable(R.drawable.avd_done_to_edit) }
    private val firstTimeUserExperienceManager by inject<FirstTimeUserExperienceManager>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        analyticsManager.onTopLevelScreenOpened(AnalyticsManager.PARAM_VALUE_SCREEN_PLAYLIST)
        binding.swipeRefreshLayout.isEnabled = false
        viewModel.shouldOpenSongs.observeAndReset { getCampfireActivity()?.openSongsScreen() }
        viewModel.songCount.observe {
            updateToolbarTitle(it)
            val previousVisibility = shuffleButton.visibleOrGone
            editToggle.visibleOrGone = arguments?.playlistId != Playlist.FAVORITES_ID || it > 0
            shareButton.visibleOrGone = it > 1
            shuffleButton.visibleOrGone = it > 1
            if (shuffleButton.visibleOrGone != previousVisibility && viewModel.isInEditMode.value == true) {
                getCampfireActivity()?.invalidateAppBar()
            }
        }
        viewModel.state.observe { updateToolbarTitle() }
        viewModel.playlist.observe {
            updateToolbarTitle()
            getCampfireActivity()?.updateToolbarButtons(listOf(editToggle, shuffleButton, shareButton))
        }
        viewModel.isInEditMode.observeAfterDelay {
            editToggle.setImageDrawable((if (it) drawableEditToDone else drawableDoneToEdit)?.apply { start() })
            if (it) {
                showHintIfNeeded()
            } else {
                if (!viewModel.hasSongToDelete()) {
                    hideSnackbar()
                }
            }
        }
        savedInstanceState?.let {
            if (it.isInEditMode) {
                viewModel.isInEditMode.value = true
                editToggle.setImageDrawable(requireContext().drawable(R.drawable.ic_done))
                toolbarTextInputView?.textInput?.run {
                    setText(viewModel.playlist.value?.title)
                    setSelection(text.length)
                }
                toolbarTextInputView?.showTextInput()
            }
        }
        toolbarTextInputView?.textInput?.requestFocus()
        val itemTouchHelper = ItemTouchHelper(object : ElevationItemTouchHelperCallback((context?.dimension(R.dimen.content_padding) ?: 0).toFloat()) {

            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) =
                if (viewModel.isInEditMode.value == true)
                    makeMovementFlags(
                        if (viewModel.adapter.itemCount > 1) ItemTouchHelper.UP or ItemTouchHelper.DOWN else 0,
                        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                    ) else 0

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) =
                consume {
                    viewHolder.adapterPosition.let { originalPosition ->
                        target.adapterPosition.let { targetPosition ->
                            if (viewModel.hasSongToDelete() || !firstTimeUserExperienceManager.playlistDragCompleted) {
                                hideSnackbar()
                            }
                            firstTimeUserExperienceManager.playlistDragCompleted = true
                            viewModel.swapSongsInPlaylist(originalPosition, targetPosition)
                            binding.root.postDelayed({ if (isAdded) showHintIfNeeded() }, 300)
                            analyticsManager.onDragToRearrangeUsed(AnalyticsManager.PARAM_VALUE_SCREEN_PLAYLIST)
                        }
                    }
                }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                viewHolder.adapterPosition.let { position ->
                    if (position != RecyclerView.NO_POSITION) {
                        analyticsManager.onSwipeToDismissUsed(AnalyticsManager.PARAM_VALUE_SCREEN_PLAYLIST)
                        viewModel.deleteSongPermanently()
                        firstTimeUserExperienceManager.playlistSwipeCompleted = true
                        (viewModel.adapter.items[position] as? SongItemViewModel)?.song?.let { song ->
                            showSnackbar(
                                message = getString(R.string.playlist_song_removed_message, song.title),
                                actionText = R.string.undo,
                                action = {
                                    analyticsManager.onUndoButtonPressed(AnalyticsManager.PARAM_VALUE_SCREEN_PLAYLIST)
                                    viewModel.cancelDeleteSong()
                                },
                                dismissAction = { viewModel.deleteSongPermanently() }
                            )
                            viewModel.deleteSongTemporarily(song.id)
                        }
                    }
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
        viewModel.adapter.songDragTouchListener = { position -> binding.recyclerView.findViewHolderForAdapterPosition(position)?.let { itemTouchHelper.startDrag(it) } }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.isInEditMode = viewModel.isInEditMode.value == true
    }

    override fun onResume() {
        super.onResume()
        showHintIfNeeded()
        viewModel.restoreToolbarButtons()
    }

    override fun onBackPressed() = if (viewModel.isInEditMode.value == true) consume { toggleEditMode() } else super.onBackPressed()

    private fun updateToolbarTitle(songCount: Int = (viewModel.songCount.value ?: 0)) =
        (toolbarTextInputView?.title ?: topLevelBehavior.defaultToolbar).updateToolbarTitle(
            viewModel.playlist.value?.title ?: getString(R.string.main_favorites),
            if (songCount == -1) {
                getString(if (viewModel.state.value == StateLayout.State.LOADING) R.string.loading else R.string.manage_playlists_song_count_empty)
            } else {
                resources.getQuantityString(R.plurals.playlist_song_count, songCount, songCount)
            }
        )

    override fun onDetailScreenOpened() {
        if (viewModel.isInEditMode.value == true) {
            toggleEditMode()
        }
    }

    private fun showHintIfNeeded() {

        fun showSwipeHintIfNeeded() {
            if (!firstTimeUserExperienceManager.playlistSwipeCompleted && !isSnackbarVisible() && viewModel.adapter.items.isNotEmpty() && viewModel.isInEditMode.value == true) {
                showHint(
                    message = R.string.playlist_hint_swipe,
                    action = { firstTimeUserExperienceManager.playlistSwipeCompleted = true }
                )
            }
        }

        fun showDragHintIfNeeded() {
            if (!firstTimeUserExperienceManager.playlistDragCompleted && !isSnackbarVisible() && viewModel.adapter.itemCount > 1 && viewModel.isInEditMode.value == true) {
                showHint(
                    message = R.string.playlist_hint_drag,
                    action = {
                        firstTimeUserExperienceManager.playlistDragCompleted = true
                        binding.root.postDelayed({ if (isAdded) showSwipeHintIfNeeded() }, 300)
                    }
                )
            }
        }

        if (!firstTimeUserExperienceManager.playlistDragCompleted) {
            showDragHintIfNeeded()
        } else {
            showSwipeHintIfNeeded()
        }
    }

    private fun toggleEditMode() {
        toolbarTextInputView?.run {
            if (title.tag == null) {
                animateTextInputVisibility(!isTextInputVisible)
                if (isTextInputVisible) {
                    (viewModel.playlist.value?.title ?: "").let {
                        textInput.setText(it)
                        textInput.setSelection(it.length)
                    }
                } else {
                    viewModel.playlist.value?.let {
                        val newTitle = textInput.text?.toString()
                        if (it.title != newTitle && newTitle != null && newTitle.trim().isNotEmpty()) {
                            viewModel.playlistRepository.updatePlaylistTitle(it.id, newTitle)
                            viewModel.appShortcutManager.updateAppShortcuts()
                        }
                        analyticsManager.onPlaylistEdited(newTitle ?: "", viewModel.adapter.itemCount)
                    }
                }
                viewModel.isInEditMode.value = isTextInputVisible
            }
            return
        }
        viewModel.isInEditMode.value = viewModel.isInEditMode.value != true
    }

    companion object {
        private var Bundle.isInEditMode by BundleArgumentDelegate.Boolean("isInEditMode")
        private var Bundle?.playlistId by BundleArgumentDelegate.String("playlistId")

        fun newInstance(playlistId: String) = PlaylistFragment().withArguments { it.playlistId = playlistId }
    }
}