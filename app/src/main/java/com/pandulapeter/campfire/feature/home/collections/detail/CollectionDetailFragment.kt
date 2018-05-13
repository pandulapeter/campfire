package com.pandulapeter.campfire.feature.home.collections.detail

import android.os.Bundle
import android.support.v4.app.SharedElementCallback
import android.transition.*
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.feature.detail.DetailFragment
import com.pandulapeter.campfire.feature.detail.FadeInTransition
import com.pandulapeter.campfire.feature.home.shared.songList.SongListFragment
import com.pandulapeter.campfire.feature.shared.widget.ToolbarButton
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.animatedDrawable
import com.pandulapeter.campfire.util.visibleOrGone
import com.pandulapeter.campfire.util.withArguments

class CollectionDetailFragment : SongListFragment<CollectionDetailViewModel>() {

    companion object {
        private var Bundle.collection by BundleArgumentDelegate.Parcelable("collection")

        fun newInstance(collection: Collection) = CollectionDetailFragment().withArguments {
            it.collection = collection
        }
    }

    override val viewModel by lazy {
        CollectionDetailViewModel(
            mainActivity,
            (arguments?.collection as? Collection) ?: throw IllegalStateException("No Collection specified.")
        ) { shuffleButton.visibleOrGone = true }
    }
    private val shuffleButton: ToolbarButton by lazy {
        mainActivity.toolbarContext.createToolbarButton(R.drawable.ic_shuffle_24dp) { shuffleSongs() }.apply { visibleOrGone = false }
    }
    private val drawableSavedToNotSaved by lazy { mainActivity.animatedDrawable(R.drawable.avd_bookmarked_to_not_bookmarked_24dp) }
    private val drawableNotSavedToSaved by lazy { mainActivity.animatedDrawable(R.drawable.avd_not_bookmarked_to_bookmarked_24dp) }
    private val playlistButton: ToolbarButton by lazy {
        mainActivity.toolbarContext.createToolbarButton(if (viewModel.collection.get()?.collection?.isBookmarked == true) R.drawable.ic_bookmarked_24dp else R.drawable.ic_not_bookmarked_24dp) {
            viewModel.collection.get()?.collection?.let {
                viewModel.collectionRepository.toggleSavedState(it.id)
                playlistButton.setImageDrawable((if (it.isBookmarked == true) drawableNotSavedToSaved else drawableSavedToNotSaved).apply { this?.start() })
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun createTransition(delay: Long) = TransitionSet()
            .addTransition(FadeInTransition())
            .addTransition(ChangeBounds())
            .addTransition(ChangeClipBounds())
            .addTransition(ChangeTransform())
            .addTransition(ChangeImageTransform())
            .apply {
                ordering = TransitionSet.ORDERING_TOGETHER
                startDelay = delay
                duration = DetailFragment.TRANSITION_DURATION
            }
        sharedElementEnterTransition = createTransition(DetailFragment.TRANSITION_DELAY)
        sharedElementReturnTransition = createTransition(0)
        setEnterSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(names: MutableList<String>, sharedElements: MutableMap<String, View>) {
                sharedElements[names[0]] = binding.collection
                sharedElements[names[1]] = binding.image
            }
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.swipeRefreshLayout.isEnabled = false
        mainActivity.updateToolbarButtons(listOf(playlistButton, shuffleButton))
        (arguments?.collection as? Collection).let {
            if (it == null) {
                defaultToolbar.updateToolbarTitle(R.string.home_collections)
            } else {
                val songCount = it.songs?.size ?: 0
                defaultToolbar.updateToolbarTitle(
                    it.title, if (songCount == 0) {
                        getString(R.string.manage_playlists_song_count_empty)
                    } else {
                        mainActivity.resources.getQuantityString(R.plurals.playlist_song_count, songCount, songCount)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.restoreToolbarButtons()
    }
}