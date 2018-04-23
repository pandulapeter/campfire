package com.pandulapeter.campfire.feature.home.collections.detail

import android.os.Bundle
import android.support.v4.app.SharedElementCallback
import android.transition.ChangeBounds
import android.transition.ChangeImageTransform
import android.transition.ChangeTransform
import android.transition.TransitionSet
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.data.repository.CollectionRepository
import com.pandulapeter.campfire.feature.detail.DetailFragment
import com.pandulapeter.campfire.feature.detail.FadeInTransition
import com.pandulapeter.campfire.feature.home.collections.CollectionListItemViewModel
import com.pandulapeter.campfire.feature.home.shared.songList.SongListFragment
import com.pandulapeter.campfire.feature.shared.widget.ToolbarButton
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.animatedDrawable
import com.pandulapeter.campfire.util.withArguments
import org.koin.android.ext.android.inject

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
        ) { mainActivity.updateToolbarButtons(listOf(playlistButton, shuffleButton)) }
    }
    private val shuffleButton: ToolbarButton by lazy { mainActivity.toolbarContext.createToolbarButton(R.drawable.ic_shuffle_24dp) { shuffleSongs() } }
    private val drawableSavedToNotSaved by lazy { mainActivity.animatedDrawable(R.drawable.avd_saved_to_not_saved_24dp) }
    private val drawableNotSavedToSaved by lazy { mainActivity.animatedDrawable(R.drawable.avd_not_saved_to_saved_24dp) }
    private val playlistButton: ToolbarButton by lazy {
        mainActivity.toolbarContext.createToolbarButton(if (viewModel.collection.get()?.collection?.isSaved == true) R.drawable.ic_saved_24dp else R.drawable.ic_not_saved_24dp) {
            viewModel.collection.get()?.collection?.let {
                viewModel.collectionRepository.toggleSavedState(it.id)
                playlistButton.setImageDrawable((if (it.isSaved == true) drawableNotSavedToSaved else drawableSavedToNotSaved).apply { this?.start() })
            }
        }
    }
    private val collectionRepository by inject<CollectionRepository>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun createTransition(delay: Long) = TransitionSet()
            .addTransition(FadeInTransition())
            .addTransition(ChangeBounds())
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
                binding.collectionHeader?.root?.let {
                    sharedElements[names[0]] = it
                }
            }
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.swipeRefreshLayout.isEnabled = false
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
        binding.collectionHeader?.run {
            root.setOnClickListener { mainActivity.onBackPressed() }
            save.setOnClickListener {
                viewModel?.collection?.let {
                    collectionRepository.toggleSavedState(it.id)
                    viewModel = CollectionListItemViewModel.CollectionViewModel(it, true)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.restoreToolbarButtons()
    }
}