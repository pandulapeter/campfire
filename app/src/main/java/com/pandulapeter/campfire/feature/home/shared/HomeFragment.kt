package com.pandulapeter.campfire.feature.home.shared

import android.content.Context
import android.databinding.ViewDataBinding
import android.os.Bundle
import android.support.annotation.LayoutRes
import android.support.design.widget.Snackbar
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.feature.detail.DetailActivity
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.util.dimension
import com.pandulapeter.campfire.util.onEventTriggered
import com.pandulapeter.campfire.util.onPropertyChanged
import javax.inject.Inject

/**
 * Parent class for Fragments that can be seen on the main screen. Handles common operations related to a song list.
 *
 * Controlled by subclasses of [HomeFragmentViewModel].
 */
abstract class HomeFragment<B : ViewDataBinding, out VM : HomeFragmentViewModel>(@LayoutRes layoutResourceId: Int) : CampfireFragment<B, VM>(layoutResourceId) {

    @Inject lateinit var songInfoRepository: SongInfoRepository
    protected var callbacks: HomeCallbacks? = null

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is HomeCallbacks) {
            callbacks = context
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        context?.let { context ->
            // Initialize the list and pull-to-refresh functionality.
            //TODO: Hide the keyboard on scroll events.
            getRecyclerView().run {
                layoutManager = LinearLayoutManager(context)
                addItemDecoration(SpacesItemDecoration(context.dimension(R.dimen.content_padding)))
            }
            getSwipeRefreshLayout().setOnRefreshListener { viewModel.forceRefresh() }
            getSwipeRefreshLayout().isRefreshing = viewModel.isLoading.get()
            viewModel.isLoading.onPropertyChanged { getSwipeRefreshLayout().isRefreshing = it }
            // Setup list item click listeners.
            viewModel.adapter.itemClickListener = { position ->
                startActivity(DetailActivity.getStartIntent(
                    context = context,
                    currentId = viewModel.adapter.items[position].songInfo.id,
                    ids = viewModel.getAdapterItems().map { it.songInfo.id }))
            }
            // Setup error handling.
            viewModel.shouldShowErrorSnackbar.onEventTriggered {
                Snackbar
                    .make(binding.root, R.string.something_went_wrong, Snackbar.LENGTH_LONG)
                    .setAction(R.string.try_again, { viewModel.forceRefresh() })
                    .show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        songInfoRepository.subscribe(viewModel)
    }

    override fun onPause() {
        super.onPause()
        songInfoRepository.unsubscribe(viewModel)
    }

    abstract fun getRecyclerView(): RecyclerView

    abstract fun getSwipeRefreshLayout(): SwipeRefreshLayout

    open fun onBackPressed() = false

    interface HomeCallbacks {

        fun showMenu()
    }
}