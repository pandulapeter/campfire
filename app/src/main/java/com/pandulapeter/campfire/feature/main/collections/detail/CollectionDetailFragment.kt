package com.pandulapeter.campfire.feature.main.collections.detail

import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.ChangeClipBounds
import android.transition.ChangeImageTransform
import android.transition.ChangeTransform
import android.transition.TransitionSet
import android.view.View
import androidx.core.app.SharedElementCallback
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.remote.Collection
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.data.repository.CollectionRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongDetailRepository
import com.pandulapeter.campfire.data.repository.SongRepository
import com.pandulapeter.campfire.feature.detail.DetailFragment
import com.pandulapeter.campfire.feature.detail.FadeInTransition
import com.pandulapeter.campfire.feature.main.shared.baseSongList.OldBaseSongListFragment
import com.pandulapeter.campfire.feature.shared.widget.ToolbarButton
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.animatedDrawable
import com.pandulapeter.campfire.util.visibleOrGone
import com.pandulapeter.campfire.util.withArguments
import org.koin.android.ext.android.inject

class CollectionDetailFragment : OldBaseSongListFragment<CollectionDetailViewModel>() {

    companion object {
        private var Bundle.collection by BundleArgumentDelegate.Parcelable("collection")

        fun newInstance(collection: Collection) = CollectionDetailFragment().withArguments {
            it.collection = collection
        }
    }

    private val songRepository by inject<SongRepository>()
    private val songDetailRepository by inject<SongDetailRepository>()
    private val collectionRepository by inject<CollectionRepository>()
    private val preferenceDatabase by inject<PreferenceDatabase>()
    private val playlistRepository by inject<PlaylistRepository>()


    override val viewModel by lazy {
        CollectionDetailViewModel(
            (arguments?.collection as? Collection) ?: throw IllegalStateException("No Collection specified."),
            getCampfireActivity(),
            songRepository,
            songDetailRepository,
            collectionRepository,
            preferenceDatabase,
            playlistRepository,
            analyticsManager
        ) { shuffleButton.visibleOrGone = true }
    }
    private val shuffleButton: ToolbarButton by lazy {
        getCampfireActivity().toolbarContext.createToolbarButton(R.drawable.ic_shuffle_24dp) { shuffleSongs(AnalyticsManager.PARAM_VALUE_SCREEN_COLLECTION_DETAIL) }
            .apply { visibleOrGone = false }
    }
    private val drawableBookmarkedToNotBookmarked by lazy { getCampfireActivity().animatedDrawable(R.drawable.avd_bookmarked_to_not_bookmarked_24dp) }
    private val drawableNotBookmarkedToBookmarked by lazy { getCampfireActivity().animatedDrawable(R.drawable.avd_not_bookmarked_to_bookmarked_24dp) }
    private val bookmarkedButton: ToolbarButton by lazy {
        getCampfireActivity().toolbarContext.createToolbarButton(if (viewModel.collection.get()?.collection?.isBookmarked == true) R.drawable.ic_bookmarked_24dp else R.drawable.ic_not_bookmarked_24dp) {
            viewModel.collection.get()?.collection?.let {
                viewModel.collectionRepository.toggleBookmarkedState(it.id)
                analyticsManager.onCollectionBookmarkedStateChanged(it.id, it.isBookmarked == true, AnalyticsManager.PARAM_VALUE_SCREEN_COLLECTION_DETAIL)
                bookmarkedButton.setImageDrawable((if (it.isBookmarked == true) drawableNotBookmarkedToBookmarked else drawableBookmarkedToNotBookmarked).apply { this?.start() })
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
        getCampfireActivity().updateToolbarButtons(listOf(bookmarkedButton, shuffleButton))
        (arguments?.collection as? Collection).let {
            if (it == null) {
                defaultToolbar.updateToolbarTitle(R.string.main_collections)
            } else {
                analyticsManager.onCollectionDetailScreenOpened(it.id)
                val songCount = it.songs?.size ?: 0
                defaultToolbar.updateToolbarTitle(
                    it.title, if (songCount == 0) {
                        getString(R.string.manage_playlists_song_count_empty)
                    } else {
                        getCampfireActivity().resources.getQuantityString(R.plurals.playlist_song_count, songCount, songCount)
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